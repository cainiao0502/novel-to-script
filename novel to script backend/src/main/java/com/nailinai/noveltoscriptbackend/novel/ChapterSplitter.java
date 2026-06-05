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
 *   3. "卷N" / "篇N"
 *   4. 行首独立成行的 "N" （数字章号，极弱匹配，仅作 fallback）
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

    private static final Pattern LOOSE_NUMERIC = Pattern.compile("(?m)^\\s*(\\d{1,3})\\s*$");

    public List<Chapter> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        Matcher m = CHAPTER_HEAD.matcher(normalized);
        java.util.List<int[]> positions = new java.util.ArrayList<>();
        while (m.find()) {
            positions.add(new int[]{m.start(), m.end()});
        }

        if (positions.isEmpty()) {
            // fallback：尝试独立数字行
            Matcher m2 = LOOSE_NUMERIC.matcher(normalized);
            while (m2.find()) {
                positions.add(new int[]{m2.start(), m2.end()});
            }
        }

        if (positions.isEmpty()) {
            // 整篇作为一章
            String title = extractFirstLine(normalized);
            return List.of(new Chapter(1, title, normalized.trim()));
        }

        java.util.List<Chapter> result = new java.util.ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            int[] cur = positions.get(i);
            int contentStart = cur[1];
            int contentEnd = (i + 1 < positions.size()) ? positions.get(i + 1)[0] : normalized.length();
            String headLine = normalized.substring(cur[0], cur[1]).trim();
            String body = normalized.substring(contentStart, contentEnd).trim();
            result.add(new Chapter(i + 1, headLine, body));
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
