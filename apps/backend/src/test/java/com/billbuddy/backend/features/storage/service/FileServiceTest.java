package com.billbuddy.backend.features.storage.service;

import com.billbuddy.backend.exception.InvalidFileException;
import com.billbuddy.backend.exception.NotFileOwnerException;
import com.billbuddy.backend.exception.NotGroupMemberException;
import com.billbuddy.backend.exception.StoredFileNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.expenses.repository.ExpenseRepository;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.storage.dto.response.FileResponse;
import com.billbuddy.backend.features.storage.model.StoredFile;
import com.billbuddy.backend.features.storage.repository.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @InjectMocks
    private FileService fileService;

    private User buildUser(Long id) {
        User user = User.signupWithEmail("user" + id + "@example.com", "hashed-password", "User " + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Group buildGroup(Long id, User creator) {
        Group group = Group.create("Goa Trip", "desc", "INR", creator);
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private StoredFile buildStoredFile(Long id, User uploadedBy) {
        StoredFile file = StoredFile.create(uploadedBy, "image/png", 1024L, "key-" + id + ".png");
        ReflectionTestUtils.setField(file, "id", id);
        return file;
    }

    // ===================== UPLOAD =====================

    @Test
    void upload_savesFile_whenValid() {
        User uploader = buildUser(1L);
        MockMultipartFile multipartFile = new MockMultipartFile("file", "photo.png", "image/png", "hello".getBytes());

        when(userRepository.findById(1L)).thenReturn(Optional.of(uploader));
        when(fileStorageService.store(any())).thenReturn("generated-key.png");
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            ReflectionTestUtils.setField(f, "id", 100L);
            return f;
        });

        FileResponse response = fileService.upload(1L, multipartFile);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getUrl()).isEqualTo("/api/v1/files/100");
        assertThat(response.getContentType()).isEqualTo("image/png");
    }

    @Test
    void upload_throwsInvalidFile_whenFileIsEmpty() {
        MultipartFile empty = new MockMultipartFile("file", "photo.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> fileService.upload(1L, empty))
                .isInstanceOf(InvalidFileException.class);

        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void upload_throwsInvalidFile_whenContentTypeNotAllowed() {
        MultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> fileService.upload(1L, pdf))
                .isInstanceOf(InvalidFileException.class);

        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void upload_throwsInvalidFile_whenOverSizeLimit() {
        byte[] tooLarge = new byte[6 * 1024 * 1024];
        MultipartFile oversized = new MockMultipartFile("file", "photo.png", "image/png", tooLarge);

        assertThatThrownBy(() -> fileService.upload(1L, oversized))
                .isInstanceOf(InvalidFileException.class);

        verify(storedFileRepository, never()).save(any());
    }

    // ===================== GET FOR VIEWING =====================

    @Test
    void getFileForViewing_throwsStoredFileNotFound_whenMissing() {
        when(storedFileRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getFileForViewing(100L, 1L))
                .isInstanceOf(StoredFileNotFoundException.class);
    }

    @Test
    void getFileForViewing_allowsUploader_whenOrphaned() {
        User uploader = buildUser(1L);
        StoredFile file = buildStoredFile(100L, uploader);

        when(storedFileRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(file));
        when(expenseRepository.findByReceiptFile_Id(100L)).thenReturn(List.of());
        when(userRepository.existsByProfilePicFile_Id(100L)).thenReturn(false);
        when(fileStorageService.load(file.getStorageKey())).thenReturn(mock(org.springframework.core.io.Resource.class));

        FileService.FileDownload result = fileService.getFileForViewing(100L, 1L);

        assertThat(result.contentType()).isEqualTo("image/png");
    }

    @Test
    void getFileForViewing_throwsNotFileOwner_whenOrphanedAndNotUploader() {
        User uploader = buildUser(1L);
        StoredFile file = buildStoredFile(100L, uploader);

        when(storedFileRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(file));
        when(expenseRepository.findByReceiptFile_Id(100L)).thenReturn(List.of());
        when(userRepository.existsByProfilePicFile_Id(100L)).thenReturn(false);

        assertThatThrownBy(() -> fileService.getFileForViewing(100L, 2L))
                .isInstanceOf(NotFileOwnerException.class);
    }

    @Test
    void getFileForViewing_allowsGroupMember_whenAttachedAsReceipt() {
        User admin = buildUser(1L);
        User member = buildUser(2L);
        Group group = buildGroup(10L, admin);
        StoredFile file = buildStoredFile(100L, admin);
        Expense expense = Expense.create(group, admin, "Dinner", new BigDecimal("90"), "INR",
                new BigDecimal("90"), BigDecimal.ONE, null, file, SplitType.EQUAL);

        when(storedFileRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(file));
        when(expenseRepository.findByReceiptFile_Id(100L)).thenReturn(List.of(expense));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(10L, 2L))
                .thenReturn(Optional.of(GroupMember.createMember(group, member)));
        when(fileStorageService.load(file.getStorageKey())).thenReturn(mock(org.springframework.core.io.Resource.class));

        FileService.FileDownload result = fileService.getFileForViewing(100L, 2L);

        assertThat(result.contentType()).isEqualTo("image/png");
    }

    @Test
    void getFileForViewing_throwsNotGroupMember_whenAttachedAsReceiptAndNotMember() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin);
        StoredFile file = buildStoredFile(100L, admin);
        Expense expense = Expense.create(group, admin, "Dinner", new BigDecimal("90"), "INR",
                new BigDecimal("90"), BigDecimal.ONE, null, file, SplitType.EQUAL);

        when(storedFileRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(file));
        when(expenseRepository.findByReceiptFile_Id(100L)).thenReturn(List.of(expense));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(10L, 3L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getFileForViewing(100L, 3L))
                .isInstanceOf(NotGroupMemberException.class);
    }

    @Test
    void getFileForViewing_allowsAnyAuthenticatedUser_whenAttachedAsProfilePicture() {
        User uploader = buildUser(1L);
        StoredFile file = buildStoredFile(100L, uploader);

        when(storedFileRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(file));
        when(expenseRepository.findByReceiptFile_Id(100L)).thenReturn(List.of());
        when(userRepository.existsByProfilePicFile_Id(100L)).thenReturn(true);
        when(fileStorageService.load(file.getStorageKey())).thenReturn(mock(org.springframework.core.io.Resource.class));

        FileService.FileDownload result = fileService.getFileForViewing(100L, 999L);

        assertThat(result.contentType()).isEqualTo("image/png");
    }

    // ===================== REQUIRE OWNED FILE =====================

    @Test
    void requireOwnedFile_returnsFile_whenRequesterIsUploader() {
        User uploader = buildUser(1L);
        StoredFile file = buildStoredFile(100L, uploader);
        when(storedFileRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(file));

        StoredFile result = fileService.requireOwnedFile(100L, 1L);

        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    void requireOwnedFile_throwsNotFileOwner_whenRequesterDidNotUploadIt() {
        User uploader = buildUser(1L);
        StoredFile file = buildStoredFile(100L, uploader);
        when(storedFileRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.requireOwnedFile(100L, 2L))
                .isInstanceOf(NotFileOwnerException.class);
    }

    @Test
    void requireOwnedFile_throwsStoredFileNotFound_whenMissing() {
        when(storedFileRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.requireOwnedFile(100L, 1L))
                .isInstanceOf(StoredFileNotFoundException.class);
    }
}
