package com.billbuddy.backend.features.friends.service;

import com.billbuddy.backend.exception.AlreadyFriendsException;
import com.billbuddy.backend.exception.FriendNotFoundException;
import com.billbuddy.backend.exception.FriendshipNotFoundException;
import com.billbuddy.backend.exception.InvalidFriendException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.friends.dto.response.FriendResponse;
import com.billbuddy.backend.features.friends.model.Friendship;
import com.billbuddy.backend.features.friends.repository.FriendshipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FriendshipService friendshipService;

    private User buildUser(Long id, String email) {
        User user = User.signupWithEmail(email, "hashed-password", "User " + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // ===================== ADD FRIEND =====================

    @Test
    void addFriend_createsFriendship_whenBothUsersExist() {
        User requester = buildUser(1L, "requester@example.com");
        User target = buildUser(2L, "target@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userRepository.findByEmail("target@example.com")).thenReturn(Optional.of(target));
        when(friendshipRepository.findByUserLow_IdAndUserHigh_Id(1L, 2L)).thenReturn(Optional.empty());

        FriendResponse response = friendshipService.addFriend(1L, "Target@Example.com");

        assertThat(response.getUserId()).isEqualTo(2L);
        assertThat(response.getEmail()).isEqualTo("target@example.com");
        verify(friendshipRepository).save(any(Friendship.class));
    }

    @Test
    void addFriend_throwsFriendNotFound_whenNoAccountWithEmail() {
        User requester = buildUser(1L, "requester@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.addFriend(1L, "nobody@example.com"))
                .isInstanceOf(FriendNotFoundException.class);

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void addFriend_throwsInvalidFriend_whenAddingSelf() {
        User requester = buildUser(1L, "requester@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userRepository.findByEmail("requester@example.com")).thenReturn(Optional.of(requester));

        assertThatThrownBy(() -> friendshipService.addFriend(1L, "requester@example.com"))
                .isInstanceOf(InvalidFriendException.class);

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void addFriend_throwsAlreadyFriends_whenFriendshipExists() {
        User requester = buildUser(1L, "requester@example.com");
        User target = buildUser(2L, "target@example.com");
        Friendship existing = Friendship.create(requester, target);

        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userRepository.findByEmail("target@example.com")).thenReturn(Optional.of(target));
        when(friendshipRepository.findByUserLow_IdAndUserHigh_Id(1L, 2L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> friendshipService.addFriend(1L, "target@example.com"))
                .isInstanceOf(AlreadyFriendsException.class);

        verify(friendshipRepository, never()).save(any());
    }

    // ===================== LIST FRIENDS =====================

    @Test
    void listFriends_returnsTheOtherUserForEachFriendship() {
        User requester = buildUser(1L, "requester@example.com");
        User target = buildUser(2L, "target@example.com");
        Friendship friendship = Friendship.create(requester, target);

        when(friendshipRepository.findByUserLow_IdOrUserHigh_Id(1L, 1L)).thenReturn(List.of(friendship));

        List<FriendResponse> responses = friendshipService.listFriends(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getUserId()).isEqualTo(2L);
        assertThat(responses.get(0).getEmail()).isEqualTo("target@example.com");
    }

    @Test
    void listFriends_worksRegardlessOfWhichSideRequesterIsOn() {
        User requester = buildUser(2L, "requester@example.com");
        User target = buildUser(1L, "target@example.com");
        // target has the lower id here, so requester ends up as userHigh
        Friendship friendship = Friendship.create(requester, target);

        when(friendshipRepository.findByUserLow_IdOrUserHigh_Id(2L, 2L)).thenReturn(List.of(friendship));

        List<FriendResponse> responses = friendshipService.listFriends(2L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getUserId()).isEqualTo(1L);
    }

    // ===================== REMOVE FRIEND =====================

    @Test
    void removeFriend_deletesFriendship_whenExists() {
        User requester = buildUser(1L, "requester@example.com");
        User target = buildUser(2L, "target@example.com");
        Friendship friendship = Friendship.create(requester, target);

        when(friendshipRepository.findByUserLow_IdAndUserHigh_Id(1L, 2L)).thenReturn(Optional.of(friendship));

        friendshipService.removeFriend(1L, 2L);

        verify(friendshipRepository).delete(friendship);
    }

    @Test
    void removeFriend_throwsFriendshipNotFound_whenNotFriends() {
        when(friendshipRepository.findByUserLow_IdAndUserHigh_Id(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.removeFriend(1L, 2L))
                .isInstanceOf(FriendshipNotFoundException.class);

        verify(friendshipRepository, never()).delete(any(Friendship.class));
    }
}
