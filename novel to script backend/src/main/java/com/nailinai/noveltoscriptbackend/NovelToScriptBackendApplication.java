package com.nailinai.noveltoscriptbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@SpringBootApplication
@MapperScan("com.nailinai.noveltoscriptbackend.persistence.mapper")
public class NovelToScriptBackendApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(NovelToScriptBackendApplication.class, args);
    }

    private static void loadDotEnv() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path[] candidates = {
                cwd.resolve(".env"),
                cwd.getParent() == null ? null : cwd.getParent().resolve(".env"),
        };
        for (Path envPath : candidates) {
            if (envPath != null && Files.exists(envPath)) {
                loadFrom(envPath);
                return;
            }
        }
    }

    private static void loadFrom(Path envPath) {
        try (Stream<String> lines = Files.lines(envPath)) {
            lines
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .forEach(line -> {
                        int eq = line.indexOf('=');
                        if (eq <= 0) {
                            return;
                        }
                        String key = line.substring(0, eq).trim();
                        String val = line.substring(eq + 1).trim();
                        if (System.getProperty(key) != null || System.getenv(key) != null) {
                            return;
                        }
                        System.setProperty(key, val);
                    });
        } catch (IOException e) {
            System.err.println("[dotenv] failed to read .env: " + e.getMessage());
        }
    }
}
