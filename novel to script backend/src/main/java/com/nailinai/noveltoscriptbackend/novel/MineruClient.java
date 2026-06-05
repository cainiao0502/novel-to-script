package com.nailinai.noveltoscriptbackend.novel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU 文档解析客户端。
 * <p>
 * 两条路径：
 * <ol>
 *   <li>Agent API（免 Token）：文件直接上传 → Markdown 输出，≤10MB / ≤20 页</li>
 *   <li>V4 API（需 Token）：传文件 URL → Zip(MD + JSON)，≤200MB / ≤200 页</li>
 * </ol>
 */
@Component
public class MineruClient {

    private static final Logger log = LoggerFactory.getLogger(MineruClient.class);

    private final WebClient client;
    private final String baseUrl;
    private final String apiToken;

    /** Agent API 的最大轮询次数（每次间隔 3s，共 ~3 分钟） */
    private static final int MAX_POLLS = 60;
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(3);

    public MineruClient(
            @Value("${app.mineru.base-url}") String baseUrl,
            @Value("${app.mineru.api-token:}") String apiToken) {
        this.baseUrl = baseUrl;
        this.client = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50MB zip
                .build();
        this.apiToken = apiToken;
    }

    // ==================== Agent API（文件上传，免 Token） ====================

    private static final int AGENT_MAX_PAGES = 20;

    /**
     * 上传 PDF 文件并解析为 Markdown。
     * <p>
     * 有 Token 时走 V4 API（支持 200 页），无 Token 时走 Agent API（20 页限制，超出自动分批）。
     */
    public String parseFile(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "upload.pdf";
        }
        log.info("MinerU: uploading file {} ({} bytes)", originalName, file.getSize());

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file: " + e.getMessage(), e);
        }

        boolean hasToken = apiToken != null && !apiToken.isBlank() && !"REPLACE_ME".equals(apiToken);

        if (hasToken) {
            // 有 Token：通过 V4 API 批量上传接口获取签名 URL → 上传 → 自动解析（支持 200 页）
            return uploadAndParseV4(fileBytes, originalName);
        }

        // 无 Token：走 Agent API（20 页限制）
        // 先尝试不带 page_range 提交，如果页数超限则分批
        try {
            return parseFileChunk(fileBytes, originalName, null);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("page count exceeds")) {
                log.info("MinerU Agent: PDF exceeds 20 pages, processing in chunks...");
                return parseFileInChunks(fileBytes, originalName);
            }
            throw e;
        }
    }

    /**
     * 不知道总页数时，按 20 页递增分批提交，直到返回空结果表示结束。
     */
    private String parseFileInChunks(byte[] fileBytes, String originalName) {
        StringBuilder result = new StringBuilder();
        int from = 1;
        int maxAttempts = 50; // 最多尝试 50 批（1000 页）
        for (int i = 0; i < maxAttempts; i++) {
            int to = from + AGENT_MAX_PAGES - 1;
            String pageRange = from + "-" + to;
            log.info("MinerU Agent: processing pages {}", pageRange);
            try {
                String md = parseFileChunk(fileBytes, originalName, pageRange);
                if (md == null || md.isBlank()) break;
                result.append(md).append("\n\n");
            } catch (RuntimeException e) {
                // 如果是页数超出总页数的错误，说明已经处理完所有页
                if (e.getMessage() != null && (e.getMessage().contains("out of range")
                        || e.getMessage().contains("invalid page")
                        || e.getMessage().contains("page"))) {
                    log.info("MinerU Agent: reached end of PDF at page {}", from - 1);
                    break;
                }
                throw e;
            }
            from = to + 1;
        }
        String finalResult = result.toString().trim();
        if (finalResult.isEmpty()) {
            throw new RuntimeException("MinerU Agent: no content extracted from PDF chunks");
        }
        return finalResult;
    }

    /**
     * 提交单次 Agent 解析请求（可指定 page_range）。
     */
    private String parseFileChunk(byte[] fileBytes, String originalName, String pageRange) {
        // ① POST JSON 提交文件名，获取 task_id 和 file_url
        org.springframework.web.client.RestTemplate rest = new org.springframework.web.client.RestTemplate();

        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("file_name", originalName);
        body.put("enable_table", true);
        body.put("is_ocr", false);
        body.put("enable_formula", true);
        if (pageRange != null) {
            body.put("page_range", pageRange);
        }

        String jsonBody;
        try {
            jsonBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
        log.info("MinerU Agent request: {}", jsonBody);

        org.springframework.http.HttpHeaders jsonHeaders = new org.springframework.http.HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<String> jsonRequest =
                new org.springframework.http.HttpEntity<>(jsonBody, jsonHeaders);

        AgentTaskResponse submitResp;
        try {
            submitResp = rest.postForObject(
                    baseUrl + "/api/v1/agent/parse/file",
                    jsonRequest,
                    AgentTaskResponse.class);
        } catch (org.springframework.web.client.RestClientException e) {
            throw new RuntimeException("MinerU Agent submit failed: " + e.getMessage(), e);
        }

        if (submitResp == null || submitResp.code != 0 || submitResp.data == null) {
            String err = submitResp != null ? submitResp.msg : "no response";
            throw new RuntimeException("MinerU Agent submit failed: " + err);
        }

        String taskId = submitResp.data.taskId;
        String fileUrl = submitResp.data.fileUrl;
        log.info("MinerU Agent: task submitted, taskId={}, uploading to OSS...", taskId);

        // ② PUT 文件二进制到 OSS 签名 URL（不能带任何额外头，否则签名不匹配）
        try {
            java.net.URL url = java.net.URI.create(fileUrl).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(fileBytes.length);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(fileBytes);
            }
            int code = conn.getResponseCode();
            if (code >= 400) {
                String errMsg;
                try (java.io.InputStream es = conn.getErrorStream()) {
                    errMsg = new String(es.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                throw new RuntimeException("HTTP " + code + ": " + errMsg);
            }
        } catch (IOException e) {
            throw new RuntimeException("MinerU Agent OSS upload failed: " + e.getMessage(), e);
        }
        log.info("MinerU Agent: file uploaded to OSS, polling for result...");

        // ③ 轮询直到完成
        String mdUrl = pollAgentTask(taskId);

        // ④ 下载 Markdown
        return downloadMarkdown(mdUrl);
    }

    /** 轮询 Agent 任务，返回 Markdown CDN URL。 */
    private String pollAgentTask(String taskId) {
        for (int i = 0; i < MAX_POLLS; i++) {
            sleep(POLL_INTERVAL);

            AgentTaskResponse resp = client.get()
                    .uri("/api/v1/agent/parse/{taskId}", taskId)
                    .retrieve()
                    .bodyToMono(AgentTaskResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (resp == null || resp.data == null) continue;

            String state = resp.data.state;
            log.debug("MinerU Agent poll {}: state={}", i + 1, state);

            if ("done".equals(state)) {
                if (resp.data.mdUrl == null || resp.data.mdUrl.isBlank()) {
                    throw new RuntimeException("MinerU Agent: task done but no md_url returned");
                }
                return resp.data.mdUrl;
            }
            if ("failed".equals(state)) {
                throw new RuntimeException("MinerU Agent parse failed: " +
                        (resp.data.errMsg != null ? resp.data.errMsg : "unknown"));
            }
            // pending / running / converting → continue polling
        }
        throw new RuntimeException("MinerU Agent: timeout after " + (MAX_POLLS * POLL_INTERVAL.getSeconds()) + "s");
    }

    // ==================== V4 API（需 Token） ====================

    /**
     * 通过 V4 批量上传接口上传文件并自动解析，返回 Markdown。
     * <p>
     * 流程：① POST 获取签名上传 URL + batch_id → ② PUT 文件（空 Content-Type）→ ③ 系统自动提交解析 → ④ 轮询 batch 获取结果
     * <p>
     * 限制：文件 ≤ 200MB，≤ 200 页。
     */
    private String uploadAndParseV4(byte[] fileBytes, String originalName) {
        log.info("MinerU V4: requesting upload URL for {}", originalName);

        // ① POST /api/v4/file-urls/batch 获取签名上传 URL 和 batch_id
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        java.util.List<java.util.Map<String, String>> files = new java.util.ArrayList<>();
        java.util.Map<String, String> fileInfo = new java.util.LinkedHashMap<>();
        fileInfo.put("name", originalName);
        files.add(fileInfo);
        body.put("files", files);

        String jsonBody;
        try {
            jsonBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request", e);
        }

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiToken);
        org.springframework.http.HttpEntity<String> request =
                new org.springframework.http.HttpEntity<>(jsonBody, headers);

        org.springframework.web.client.RestTemplate rest = new org.springframework.web.client.RestTemplate();
        BatchUploadResponse resp;
        try {
            resp = rest.postForObject(
                    baseUrl + "/api/v4/file-urls/batch",
                    request,
                    BatchUploadResponse.class);
        } catch (org.springframework.web.client.RestClientException e) {
            throw new RuntimeException("MinerU V4 batch upload failed: " + e.getMessage(), e);
        }

        if (resp == null || resp.code != 0 || resp.data == null
                || resp.data.batchId == null || resp.data.fileUrls == null
                || resp.data.fileUrls.isEmpty()) {
            String err = resp != null ? resp.msg : "no response";
            throw new RuntimeException("MinerU V4 batch upload failed: " + err);
        }

        String batchId = resp.data.batchId;
        String uploadUrl = resp.data.fileUrls.get(0);
        log.info("MinerU V4: got batch_id={}, uploading file...", batchId);

        // ② PUT 文件到签名 URL（必须设置空 Content-Type，否则 OSS 签名不匹配）
        try {
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) java.net.URI.create(uploadUrl).toURL().openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(fileBytes.length);
            conn.setRequestProperty("Content-Type", "");
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(fileBytes);
            }
            int code = conn.getResponseCode();
            if (code >= 400) {
                String errMsg;
                try (java.io.InputStream es = conn.getErrorStream()) {
                    errMsg = new String(es.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                throw new RuntimeException("V4 upload HTTP " + code + ": " + errMsg);
            }
        } catch (IOException e) {
            throw new RuntimeException("MinerU V4 upload failed: " + e.getMessage(), e);
        }

        log.info("MinerU V4: file uploaded, system will auto-submit parsing. Polling batch...");

        // ③ 轮询 batch 状态，等待解析完成
        return pollV4Batch(batchId);
    }

    /** 轮询 V4 批量任务，等待完成后下载并返回 Markdown。 */
    private String pollV4Batch(String batchId) {
        for (int i = 0; i < MAX_POLLS; i++) {
            sleep(POLL_INTERVAL);

            V4BatchStatusResponse resp = client.get()
                    .uri("/api/v4/extract-results/batch/{batchId}", batchId)
                    .header("Authorization", "Bearer " + apiToken)
                    .retrieve()
                    .bodyToMono(V4BatchStatusResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (resp == null || resp.data == null || resp.data.extractResult == null
                    || resp.data.extractResult.isEmpty()) continue;

            V4BatchStatusData.ExtractResult first = resp.data.extractResult.get(0);
            String state = first.state;
            log.debug("MinerU V4 batch poll {}: state={}", i + 1, state);

            if ("done".equals(state)) {
                if (first.fullZipUrl == null || first.fullZipUrl.isBlank()) {
                    throw new RuntimeException("MinerU V4: batch done but no full_zip_url");
                }
                return downloadAndExtractMarkdown(first.fullZipUrl);
            }
            if ("failed".equals(state)) {
                throw new RuntimeException("MinerU V4 batch parse failed: " +
                        (first.errMsg != null ? first.errMsg : "unknown"));
            }
            // waiting-file / pending / running / converting → continue
        }
        throw new RuntimeException("MinerU V4: batch timeout after "
                + (MAX_POLLS * POLL_INTERVAL.getSeconds()) + "s");
    }

    /**
     * 通过 V4 API 提交文件 URL 解析，返回 Markdown。
     * <p>
     * 限制：文件 ≤ 200MB，≤ 200 页。需要有效的 API Token。
     */
    public String parseUrl(String fileUrl) {
        if (apiToken == null || apiToken.isBlank() || "REPLACE_ME".equals(apiToken)) {
            throw new RuntimeException("MinerU V4 API token not configured. Set MINERU_API_TOKEN in .env");
        }
        log.info("MinerU V4: submitting URL {}", fileUrl);

        // 1. 提交任务
        V4TaskRequest reqBody = new V4TaskRequest();
        reqBody.url = fileUrl;
        reqBody.modelVersion = "vlm";

        V4TaskResponse submitResp = client.post()
                .uri("/api/v4/extract/task")
                .header("Authorization", "Bearer " + apiToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reqBody)
                .retrieve()
                .bodyToMono(V4TaskResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();

        if (submitResp == null || submitResp.code != 0 || submitResp.data == null) {
            String err = submitResp != null ? submitResp.msg : "no response";
            throw new RuntimeException("MinerU V4 submit failed: " + err);
        }
        String taskId = submitResp.data.taskId;
        log.info("MinerU V4: task submitted, taskId={}", taskId);

        // 2. 轮询直到完成
        String zipUrl = pollV4Task(taskId);

        // 3. 下载 Zip 并提取 full.md
        return downloadAndExtractMarkdown(zipUrl);
    }

    /** 轮询 V4 任务，返回 Zip URL。 */
    private String pollV4Task(String taskId) {
        for (int i = 0; i < MAX_POLLS; i++) {
            sleep(POLL_INTERVAL);

            V4TaskResponse resp = client.get()
                    .uri("/api/v4/extract/task/{taskId}", taskId)
                    .header("Authorization", "Bearer " + apiToken)
                    .retrieve()
                    .bodyToMono(V4TaskResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (resp == null || resp.data == null) continue;

            String state = resp.data.state;
            log.debug("MinerU V4 poll {}: state={}", i + 1, state);

            if ("done".equals(state)) {
                if (resp.data.fullZipUrl == null || resp.data.fullZipUrl.isBlank()) {
                    throw new RuntimeException("MinerU V4: task done but no full_zip_url returned");
                }
                return resp.data.fullZipUrl;
            }
            if ("failed".equals(state)) {
                throw new RuntimeException("MinerU V4 parse failed: " +
                        (resp.data.errMsg != null ? resp.data.errMsg : "unknown"));
            }
        }
        throw new RuntimeException("MinerU V4: timeout after " + (MAX_POLLS * POLL_INTERVAL.getSeconds()) + "s");
    }

    // ==================== 通用工具方法 ====================

    /** 从 CDN URL 下载 Markdown 文本。 */
    private String downloadMarkdown(String url) {
        log.info("MinerU: downloading Markdown from {}", url);
        try {
            byte[] bytes = client.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
            if (bytes == null || bytes.length == 0) {
                throw new RuntimeException("MinerU: downloaded Markdown is empty");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (WebClientResponseException e) {
            throw new RuntimeException("MinerU: failed to download Markdown: HTTP " +
                    e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        }
    }

    /** 下载 Zip 并从中提取 full.md。 */
    private String downloadAndExtractMarkdown(String zipUrl) {
        log.info("MinerU: downloading Zip from {}", zipUrl);
        byte[] zipBytes;
        try {
            zipBytes = client.get()
                    .uri(zipUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(120))
                    .block();
        } catch (WebClientResponseException e) {
            throw new RuntimeException("MinerU: failed to download Zip: HTTP " +
                    e.getStatusCode(), e);
        }
        if (zipBytes == null || zipBytes.length == 0) {
            throw new RuntimeException("MinerU: downloaded Zip is empty");
        }

        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // 查找 full.md（可能在根目录或子目录中）
                if (entry.getName().endsWith("full.md") && !entry.isDirectory()) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) != -1) {
                        bos.write(buf, 0, n);
                    }
                    String md = bos.toString(StandardCharsets.UTF_8);
                    log.info("MinerU: extracted full.md ({} chars) from {}", md.length(), entry.getName());
                    return md;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("MinerU: failed to extract full.md from Zip", e);
        }
        throw new RuntimeException("MinerU: full.md not found in Zip archive");
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during MinerU polling", e);
        }
    }

    // ==================== DTO ====================

    /** Agent API 通用响应 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AgentTaskResponse {
        public int code;
        public String msg;
        public AgentTaskData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AgentTaskData {
        @JsonProperty("task_id")
        public String taskId;
        public String state;
        @JsonProperty("file_url")
        public String fileUrl;
        @JsonProperty("markdown_url")
        public String mdUrl;
        @JsonProperty("err_msg")
        public String errMsg;
    }

    /** V4 批量上传响应 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BatchUploadResponse {
        public int code;
        public String msg;
        public BatchUploadData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BatchUploadData {
        @JsonProperty("batch_id")
        public String batchId;
        @JsonProperty("file_urls")
        public java.util.List<String> fileUrls;
    }

    /** V4 批量任务状态响应 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class V4BatchStatusResponse {
        public int code;
        public String msg;
        public V4BatchStatusData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class V4BatchStatusData {
        @JsonProperty("batch_id")
        public String batchId;
        @JsonProperty("extract_result")
        public java.util.List<ExtractResult> extractResult;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ExtractResult {
            @JsonProperty("file_name")
            public String fileName;
            public String state;
            @JsonProperty("full_zip_url")
            public String fullZipUrl;
            @JsonProperty("err_msg")
            public String errMsg;
        }
    }

    /** V4 API 请求体 */
    private static class V4TaskRequest {
        public String url;
        @JsonProperty("model_version")
        public String modelVersion;
    }

    /** V4 API 通用响应 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class V4TaskResponse {
        public int code;
        public String msg;
        @JsonProperty("trace_id")
        public String traceId;
        public V4TaskData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class V4TaskData {
        @JsonProperty("task_id")
        public String taskId;
        public String state;
        @JsonProperty("full_zip_url")
        public String fullZipUrl;
        @JsonProperty("err_msg")
        public String errMsg;
    }
}
