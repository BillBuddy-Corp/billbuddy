package com.billbuddy.backend.features.storage.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    String store(MultipartFile file);

    Resource load(String storageKey);

    void delete(String storageKey);
}
