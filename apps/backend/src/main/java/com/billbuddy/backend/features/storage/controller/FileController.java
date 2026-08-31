package com.billbuddy.backend.features.storage.controller;

import com.billbuddy.backend.features.storage.dto.response.FileResponse;
import com.billbuddy.backend.features.storage.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "Files", description = "File upload and retrieval APIs")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file", description = "Uploads an image (JPEG/PNG/WEBP, up to 5MB); attach the returned id elsewhere as profilePicFileId or receiptFileId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Missing file, unsupported content type, or over the size limit")
    })
    public ResponseEntity<FileResponse> upload(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file
    ) {
        FileResponse response = fileService.upload(userId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "Download a file", description = "Returns the raw file content. Receipts require active membership in the owning expense's group; profile pictures are visible to any authenticated user; unattached files are visible only to the uploader")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File content returned"),
            @ApiResponse(responseCode = "403", description = "Not authorized to view this file"),
            @ApiResponse(responseCode = "404", description = "File not found")
    })
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long fileId
    ) {
        FileService.FileDownload download = fileService.getFileForViewing(fileId, userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .body(download.resource());
    }
}
