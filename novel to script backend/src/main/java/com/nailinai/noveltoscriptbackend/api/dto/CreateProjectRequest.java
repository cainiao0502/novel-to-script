package com.nailinai.noveltoscriptbackend.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateProjectRequest {
    @NotBlank
    @Size(max = 128)
    private String title;

    @Size(max = 256)
    private String sourceNovel;

    @Size(max = 64)
    private String genre;

    @NotBlank
    private String text;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSourceNovel() { return sourceNovel; }
    public void setSourceNovel(String sourceNovel) { this.sourceNovel = sourceNovel; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
