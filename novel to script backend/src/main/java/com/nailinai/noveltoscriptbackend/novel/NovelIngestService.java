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
    private final ProjectStore store;

    public NovelIngestService(ChapterSplitter splitter,
                              DocxTextExtractor docxExtractor,
                              ProjectStore store) {
        this.splitter = splitter;
        this.docxExtractor = docxExtractor;
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

    public IngestResult ingestFile(String title, String sourceNovel, String genre, MultipartFile file) {
        String text;
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".docx")) {
            text = docxExtractor.extract(file);
        } else {
            try {
                text = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("Failed to read file: " + e.getMessage(), e);
            }
        }
        return ingestText(title, sourceNovel, genre, text);
    }

    public record IngestResult(ProjectEntity project, List<ChapterEntity> chapters) {
    }
}
