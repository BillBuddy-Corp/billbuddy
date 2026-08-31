package com.billbuddy.backend.features.storage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class FileResponse {

    private Long id;
    private String url;
    private String contentType;
    private Long fileSizeBytes;
    private LocalDateTime createdAt;
}
