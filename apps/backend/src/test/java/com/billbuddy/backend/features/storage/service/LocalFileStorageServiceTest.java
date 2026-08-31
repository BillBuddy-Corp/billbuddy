package com.billbuddy.backend.features.storage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void store_writesFileToDisk_andReturnsAKeyPreservingTheExtension() throws IOException {
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "hello".getBytes());

        String storageKey = service.store(file);

        assertThat(storageKey).endsWith(".png");
        assertThat(Files.exists(tempDir.resolve(storageKey))).isTrue();
        assertThat(Files.readString(tempDir.resolve(storageKey))).isEqualTo("hello");
    }

    @Test
    void store_generatesDifferentKeys_forTwoUploadsOfTheSameFilename() throws IOException {
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString());
        MockMultipartFile file1 = new MockMultipartFile("file", "photo.png", "image/png", "one".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "photo.png", "image/png", "two".getBytes());

        String key1 = service.store(file1);
        String key2 = service.store(file2);

        assertThat(key1).isNotEqualTo(key2);
        assertThat(Files.readString(tempDir.resolve(key1))).isEqualTo("one");
        assertThat(Files.readString(tempDir.resolve(key2))).isEqualTo("two");
    }

    @Test
    void store_handlesAFilenameWithNoExtension() {
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "noext", "image/png", "hello".getBytes());

        String storageKey = service.store(file);

        assertThat(storageKey).doesNotContain(".");
    }

    @Test
    void load_returnsAReadableResource_forAStoredFile() throws IOException {
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "hello".getBytes());
        String storageKey = service.store(file);

        Resource resource = service.load(storageKey);

        assertThat(resource.exists()).isTrue();
        try (InputStream in = resource.getInputStream()) {
            assertThat(new String(in.readAllBytes())).isEqualTo("hello");
        }
    }

    @Test
    void delete_removesTheFileFromDisk() {
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "hello".getBytes());
        String storageKey = service.store(file);

        service.delete(storageKey);

        assertThat(Files.exists(tempDir.resolve(storageKey))).isFalse();
    }

    @Test
    void delete_doesNotThrow_whenFileDoesNotExist() {
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString());

        service.delete("never-existed.png");
    }

    @Test
    void constructor_createsTheUploadDirectory_whenItDoesNotExistYet() {
        Path nested = tempDir.resolve("does/not/exist/yet");

        new LocalFileStorageService(nested.toString());

        assertThat(Files.isDirectory(nested)).isTrue();
    }
}
