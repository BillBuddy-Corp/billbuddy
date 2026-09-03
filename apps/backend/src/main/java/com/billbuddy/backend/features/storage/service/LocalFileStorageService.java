package com.billbuddy.backend.features.storage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path uploadDir;

    public LocalFileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir);
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not create upload directory: " + uploadDir, ex);
        }
    }

    @Override
    public String store(MultipartFile file) {
        String storageKey = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        try {
            Files.copy(file.getInputStream(), uploadDir.resolve(storageKey));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store uploaded file", ex);
        }
        return storageKey;
    }

    @Override
    public Resource load(String storageKey) {
        return new FileSystemResource(uploadDir.resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(uploadDir.resolve(storageKey));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not delete stored file", ex);
        }
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        return dotIndex >= 0 ? originalFilename.substring(dotIndex) : "";
    }
}
