package com.billbuddy.backend.features.friends.controller;

import com.billbuddy.backend.features.friends.dto.request.AddFriendRequest;
import com.billbuddy.backend.features.friends.dto.response.FriendResponse;
import com.billbuddy.backend.features.friends.service.FriendshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/friends")
@Tag(name = "Friends", description = "Friend connection APIs")
@SecurityRequirement(name = "bearerAuth")
public class FriendController {

    private final FriendshipService friendshipService;

    public FriendController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @PostMapping
    @Operation(summary = "Add a friend", description = "Adds an existing user as a friend by email; instant, no acceptance step")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Friend added successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid email, or attempting to add yourself"),
            @ApiResponse(responseCode = "404", description = "No account found with that email"),
            @ApiResponse(responseCode = "409", description = "Already friends with this user")
    })
    public ResponseEntity<FriendResponse> addFriend(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AddFriendRequest request
    ) {
        FriendResponse response = friendshipService.addFriend(userId, request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List friends", description = "Lists everyone the authenticated user has added as a friend")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Friends retrieved successfully")
    })
    public ResponseEntity<List<FriendResponse>> listFriends(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(friendshipService.listFriends(userId));
    }

    @DeleteMapping("/{friendUserId}")
    @Operation(summary = "Remove a friend", description = "Removes a friend connection; either side can remove it")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Friend removed successfully"),
            @ApiResponse(responseCode = "404", description = "Not friends with this user")
    })
    public ResponseEntity<Void> removeFriend(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long friendUserId
    ) {
        friendshipService.removeFriend(userId, friendUserId);
        return ResponseEntity.noContent().build();
    }
}
