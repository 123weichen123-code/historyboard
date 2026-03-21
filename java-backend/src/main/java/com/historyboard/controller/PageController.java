package com.historyboard.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PageController {

    @Value("${historyboard.frontend-dir:../frontend}")
    private String frontendDir;

    @GetMapping("/")
    public ResponseEntity<Resource> index() throws IOException {
        Path file = Path.of(frontendDir).resolve("index.html").toAbsolutePath().normalize();
        byte[] body = Files.readAllBytes(file);
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(new ByteArrayResource(body));
    }
}
