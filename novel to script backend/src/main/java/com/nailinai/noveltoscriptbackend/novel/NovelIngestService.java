package com.nailinai.noveltoscriptbackend.novel;

import com.nailinai.noveltoscriptbackend.domain.entity.ChapterEntity;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectEntity;
import com.nailinai.noveltoscriptbackend.persistence.ProjectStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class NovelIngestService {

    private final ChapterSplitter splitter;
    private final DocxTextExtractor docxExtractor;
    private final MineruClient mineruClient;
    private final ProjectStore store;

    public NovelIngestService(ChapterSplitter splitter,
                              DocxTextExtractor docxExtractor,
                              MineruClient mineruClient,
                              ProjectStore store) {
        this.splitter = splitter;
        this.docxExtractor = docxExtractor;
        this.mineruClient = mineruClient;
        this.store = store;
    }

    public IngestResult ingestText(String title, String sourceNovel, String genre, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("Novel text is empty");
        }
        List<ChapterSplitter.Chapter> chapters = splitter.split(rawText);
        if (chapters.size() < 3) {
            throw new IllegalArgumentException(
                    "At least 3 chapters required, detected " + chapters.size());
        }
        ProjectEntity project = store.createProject(title, sourceNovel, genre, chapters.size());
        for (ChapterSplitter.Chapter c : chapters) {
            store.createChapter(project.getId(), c.index(), c.title(), c.content());
        }
        return new IngestResult(project, store.listChapters(project.getId()));
    }

    /** MinerU 支持解析的文件扩展名 */
    private static final java.util.Set<String> MINERU_EXTENSIONS = java.util.Set.of(
            ".pdf", ".ppt", ".pptx", ".html", ".htm", ".mhtml",
            ".png", ".jpg", ".jpeg", ".bmp", ".tiff", ".tif"
    );

    public IngestResult ingestFile(String title, String sourceNovel, String genre, MultipartFile file) {
        String text;
        String filename = file.getOriginalFilename();
        String ext = filename != null ? getExtension(filename) : "";

        if (".docx".equals(ext)) {
            text = docxExtractor.extract(file);
        } else if (MINERU_EXTENSIONS.contains(ext)) {
            // PDF / PPT / 图片 / HTML 等 → MinerU 解析为 Markdown
            text = mineruClient.parseFile(file);
        } else {
            // txt / md / 其他纯文本
            try {
                text = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("Failed to read file: " + e.getMessage(), e);
            }
        }
        return ingestText(title, sourceNovel, genre, text);
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }

    public record IngestResult(ProjectEntity project, List<ChapterEntity> chapters) {
    }
}
