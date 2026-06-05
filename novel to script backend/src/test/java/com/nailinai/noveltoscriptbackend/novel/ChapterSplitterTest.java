package com.nailinai.noveltoscriptbackend.novel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChapterSplitterTest {

    private final ChapterSplitter splitter = new ChapterSplitter();

    @Test
    void splitsChineseChapterHeads() {
        String text = """
                第一章 开端
                这是第一章正文。

                第二章 发展
                这是第二章正文。

                第三章 高潮
                这是第三章正文。
                """;
        List<ChapterSplitter.Chapter> chapters = splitter.split(text);
        assertEquals(3, chapters.size());
        assertEquals(1, chapters.get(0).index());
        assertTrue(chapters.get(0).title().contains("第一章"));
        assertTrue(chapters.get(0).content().contains("第一章正文"));
        assertTrue(chapters.get(2).content().contains("第三章正文"));
    }

    @Test
    void splitsMixedChineseAndArabic() {
        String text = """
                第1章 引子
                内容1
                第2章 展开
                内容2
                第3章 冲突
                内容3
                """;
        List<ChapterSplitter.Chapter> chapters = splitter.split(text);
        assertEquals(3, chapters.size());
        assertEquals(2, chapters.get(1).index());
    }

    @Test
    void splitsEnglishChapterHeads() {
        String text = """
                Chapter 1 The Beginning
                Once upon a time...

                Chapter 2 The Middle
                And then...

                Chapter 3 The End
                Finally.
                """;
        List<ChapterSplitter.Chapter> chapters = splitter.split(text);
        assertEquals(3, chapters.size());
    }

    @Test
    void fallbackReturnsSingleChapterForEmptyHeads() {
        String text = "这是没有章标的小说全文。";
        List<ChapterSplitter.Chapter> chapters = splitter.split(text);
        assertEquals(1, chapters.size());
        assertEquals(1, chapters.get(0).index());
    }

    @Test
    void emptyTextReturnsEmpty() {
        assertTrue(splitter.split("").isEmpty());
        assertTrue(splitter.split(null).isEmpty());
    }

    @Test
    void handlesCarriageReturnLineEndings() {
        String text = "第一章\r\n内容\r\n\r\n第二章\r\n内容";
        List<ChapterSplitter.Chapter> chapters = splitter.split(text);
        assertEquals(2, chapters.size());
    }
}
