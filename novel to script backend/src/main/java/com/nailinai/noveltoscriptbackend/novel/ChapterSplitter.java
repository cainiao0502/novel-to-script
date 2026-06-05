package com.nailinai.noveltoscriptbackend.novel;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 章节切分：按常见中文/英文章标识别。
 * 优先级（自上而下，首个匹配胜出）：
 *   1. "第N章" / "第N回" / "第N节"
 *   2. "Chapter N" / "CHAPTER N"
 *   3. Markdown 标题（# / ##）
 *   4. 分隔符（--- / *** / ===）
 *   5. 行首独立成行的 "N" （数字章号，极弱匹配）
 *   6. 按段落数量均分（最终 fallback）
 */
@Component
public class ChapterSplitter {

    private static final Pattern CHAPTER_HEAD = Pattern.compile(
            "(?m)^\\s*(?:"
                    + "第[\\d零一二三四五六七八九十百千]+[章回节集卷篇]|"
                    + "[Cc]hapter\\s+\\d+|"
                    + "[Cc]hapter\\s+[IVXLCDM]+|"
                    + "[Pp]art\\s+\\d+"
                    + ")[\\s::：]*(.*)$"
    );

    private static final Pattern MARKDOWN_HEAD = Pattern.compile("(?m)^\\s{0,3}(#{1,3})\\s+(.+)$");

    private static final Pattern SEPARATOR = Pattern.compile("(?m)^\\s*(?:-{3,}|\\*{3,}|={3,})\\s*$");

    private static final Pattern LOOSE_NUMERIC = Pattern.compile("(?m)^\\s*(\\d{1,3})\\s*$");

    /** 均分 fallback 时，每章最少段落数 */
    private static final int MIN_PARAGRAPHS_PER_CHAPTER = 3;

    public List<Chapter> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");

        // 1. 中/英文章标题
        java.util.List<int[]> positions = findMatches(CHAPTER_HEAD, normalized);

        // 2. Markdown 标题
        if (positions.isEmpty()) {
            positions = findMatches(MARKDOWN_HEAD, normalized);
        }

        // 3. 分隔符
        if (positions.isEmpty()) {
            positions = findMatches(SEPARATOR, normalized);
        }

        // 4. 独立数字行
        if (positions.isEmpty()) {
            positions = findMatches(LOOSE_NUMERIC, normalized);
        }

        // 5. 按段落数量均分
        if (positions.isEmpty()) {
            return splitByParagraphs(normalized);
        }

        java.util.List<Chapter> result = new java.util.ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            int[] cur = positions.get(i);
            int contentStart = cur[1];
            int contentEnd = (i + 1 < positions.size()) ? positions.get(i + 1)[0] : normalized.length();
            String headLine = normalized.substring(cur[0], cur[1]).trim();
            String body = normalized.substring(contentStart, contentEnd).trim();
            if (!body.isBlank()) {
                result.add(new Chapter(result.size() + 1, headLine, body));
            }
        }
        return result.isEmpty() ? splitByParagraphs(normalized) : result;
    }

    private java.util.List<int[]> findMatches(Pattern pattern, String text) {
        java.util.List<int[]> positions = new java.util.ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            positions.add(new int[]{m.start(), m.end()});
        }
        return positions;
    }

    /**
     * 按段落数量均分文本，确保至少产生 MIN_CHAPTERS 章。
     */
    private java.util.List<Chapter> splitByParagraphs(String text) {
        String[] paragraphs = text.split("\\n\\s*\\n");
        if (paragraphs.length < 3) {
            // 段落太少，整篇作为一章
            String title = extractFirstLine(text);
            return List.of(new Chapter(1, title, text.trim()));
        }

        // 目标：分成 3~5 章
        int targetChapters = Math.min(5, Math.max(3, paragraphs.length / MIN_PARAGRAPHS_PER_CHAPTER));
        int parasPerChapter = Math.max(1, paragraphs.length / targetChapters);

        java.util.List<Chapter> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        int count = 0;

        for (int i = 0; i < paragraphs.length; i++) {
            if (current.length() > 0) current.append("\n\n");
            current.append(paragraphs[i].trim());
            count++;

            boolean isLast = (i == paragraphs.length - 1);
            if (count >= parasPerChapter && !isLast && result.size() < targetChapters - 1) {
                result.add(new Chapter(result.size() + 1,
                        "第 " + (result.size() + 1) + " 部分", current.toString().trim()));
                current.setLength(0);
                count = 0;
            }
        }
        if (current.length() > 0) {
            result.add(new Chapter(result.size() + 1,
                    "第 " + (result.size() + 1) + " 部分", current.toString().trim()));
        }
        return result;
    }

    private String extractFirstLine(String text) {
        int nl = text.indexOf('\n');
        String first = nl < 0 ? text : text.substring(0, nl);
        return first.length() > 64 ? first.substring(0, 64) : first;
    }

    public record Chapter(int index, String title, String content) {
    }
}
