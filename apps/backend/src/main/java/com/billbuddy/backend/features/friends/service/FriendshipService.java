package com.billbuddy.backend.features.friends.service;

import com.billbuddy.backend.exception.AlreadyFriendsException;
import com.billbuddy.backend.exception.FriendNotFoundException;
import com.billbuddy.backend.exception.FriendshipNotFoundException;
import com.billbuddy.backend.exception.InvalidFriendException;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.friends.dto.response.FriendResponse;
import com.billbuddy.backend.features.friends.model.Friendship;
import com.billbuddy.backend.features.friends.repository.FriendshipRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public FriendshipService(FriendshipRepository friendshipRepository, UserRepository userRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public FriendResponse addFriend(Long requesterId, String rawEmail) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String email = normalizeEmail(rawEmail);
        User target = userRepository.findByEmail(email)
                .orElseThrow(() -> new FriendNotFoundException("No account found with that email"));

        if (target.getId().equals(requester.getId())) {
            throw new InvalidFriendException("You cannot add yourself as a friend");
        }

        if (findExistingFriendship(requester.getId(), target.getId()).isPresent()) {
            throw new AlreadyFriendsException("You are already friends with this user");
        }

        Friendship friendship = Friendship.create(requester, target);
        friendshipRepository.save(friendship);

        return toResponse(friendship, requester.getId());
    }

    @Transactional
    public List<FriendResponse> listFriends(Long userId) {
        return friendshipRepository.findByUserLow_IdOrUserHigh_Id(userId, userId).stream()
                .map(friendship -> toResponse(friendship, userId))
                .toList();
    }

    @Transactional
    public void removeFriend(Long requesterId, Long friendUserId) {
        Friendship friendship = findExistingFriendship(requesterId, friendUserId)
                .orElseThrow(() -> new FriendshipNotFoundException("You are not friends with this user"));
        friendshipRepository.delete(friendship);
    }

    // Used by other features (non-group expenses) to gate friend-only actions -- throws rather
    // than returning a boolean so callers get the same 404 FRIENDSHIP_NOT_FOUND shape for free.
    @Transactional
    public void requireFriends(Long userAId, Long userBId) {
        findExistingFriendship(userAId, userBId)
                .orElseThrow(() -> new FriendshipNotFoundException("You are not friends with this user"));
    }

    private Optional<Friendship> findExistingFriendship(Long userAId, Long userBId) {
        Long lowId = Math.min(userAId, userBId);
        Long highId = Math.max(userAId, userBId);
        return friendshipRepository.findByUserLow_IdAndUserHigh_Id(lowId, highId);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private FriendResponse toResponse(Friendship friendship, Long viewerUserId) {
        User other = friendship.theOtherUser(viewerUserId);
        return new FriendResponse(
                other.getId(),
                other.getFullName(),
                other.getEmail(),
                friendship.getCreatedAt()
        );
    }
}
