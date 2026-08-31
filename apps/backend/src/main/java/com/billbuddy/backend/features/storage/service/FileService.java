package com.billbuddy.backend.features.storage.service;

import com.billbuddy.backend.exception.InvalidFileException;
import com.billbuddy.backend.exception.NotFileOwnerException;
import com.billbuddy.backend.exception.NotGroupMemberException;
import com.billbuddy.backend.exception.StoredFileNotFoundException;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.repository.ExpenseRepository;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.storage.dto.response.FileResponse;
import com.billbuddy.backend.features.storage.model.StoredFile;
import com.billbuddy.backend.features.storage.repository.StoredFileRepository;
import jakarta.transaction.Transactional;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@Service
public class FileService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final GroupMemberRepository groupMemberRepository;

    public FileService(
            StoredFileRepository storedFileRepository,
            FileStorageService fileStorageService,
            UserRepository userRepository,
            ExpenseRepository expenseRepository,
            GroupMemberRepository groupMemberRepository
    ) {
        this.storedFileRepository = storedFileRepository;
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    @Transactional
    public FileResponse upload(Long uploaderId, MultipartFile file) {
        validateFile(file);
        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String storageKey = fileStorageService.store(file);
        StoredFile storedFile = StoredFile.create(uploader, file.getContentType(), file.getSize(), storageKey);
        storedFile = storedFileRepository.save(storedFile);

        return toResponse(storedFile);
    }

    @Transactional
    public FileDownload getFileForViewing(Long fileId, Long requesterId) {
        StoredFile storedFile = storedFileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new StoredFileNotFoundException("File not found"));

        List<Expense> expensesUsingFile = expenseRepository.findByReceiptFile_Id(fileId);
        if (!expensesUsingFile.isEmpty()) {
            boolean isMemberOfAny = expensesUsingFile.stream()
                    .anyMatch(e -> groupMemberRepository
                            .findByGroup_IdAndUser_IdAndLeftAtIsNull(e.getGroup().getId(), requesterId)
                            .isPresent());
            if (!isMemberOfAny) {
                throw new NotGroupMemberException("You are not a member of the group this receipt belongs to");
            }
            return toDownload(storedFile);
        }

        boolean isProfilePicture = userRepository.existsByProfilePicFile_Id(fileId);
        if (isProfilePicture) {
            return toDownload(storedFile);
        }

        // orphaned -- not yet attached to anything, only the uploader can see it
        if (!storedFile.getUploadedBy().getId().equals(requesterId)) {
            throw new NotFileOwnerException("You can only view files you uploaded until they're attached to something");
        }
        return toDownload(storedFile);
    }

    @Transactional
    public StoredFile requireOwnedFile(Long fileId, Long requesterId) {
        StoredFile storedFile = storedFileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new StoredFileNotFoundException("File not found"));
        if (!storedFile.getUploadedBy().getId().equals(requesterId)) {
            throw new NotFileOwnerException("You can only attach files you uploaded yourself");
        }
        return storedFile;
    }

    // ===================== HELPERS =====================

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File is required");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new InvalidFileException("Only JPEG, PNG, and WEBP images are allowed");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileException("File exceeds the 5MB size limit");
        }
    }

    private FileResponse toResponse(StoredFile storedFile) {
        return new FileResponse(
                storedFile.getId(),
                "/api/v1/files/" + storedFile.getId(),
                storedFile.getContentType(),
                storedFile.getFileSizeBytes(),
                storedFile.getCreatedAt()
        );
    }

    private FileDownload toDownload(StoredFile storedFile) {
        Resource resource = fileStorageService.load(storedFile.getStorageKey());
        return new FileDownload(resource, storedFile.getContentType());
    }

    public record FileDownload(Resource resource, String contentType) {
    }
}
