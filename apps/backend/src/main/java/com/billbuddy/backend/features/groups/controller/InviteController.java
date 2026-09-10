package com.billbuddy.backend.features.groups.controller;

import com.billbuddy.backend.features.groups.dto.request.JoinInviteRequest;
import com.billbuddy.backend.features.groups.dto.response.JoinInviteResponse;
import com.billbuddy.backend.features.groups.dto.response.MyInviteResponse;
import com.billbuddy.backend.features.groups.service.GroupInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invites")
@Tag(name = "Invites", description = "Join a group via an email or shareable-link invite token")
@SecurityRequirement(name = "bearerAuth")
public class InviteController {

    private final GroupInviteService groupInviteService;

    public InviteController(GroupInviteService groupInviteService) {
        this.groupInviteService = groupInviteService;
    }

    @PostMapping("/join")
    @Operation(summary = "Join a group via invite", description = "Joins a group using either an email-invite or shareable-link token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Joined group successfully"),
            @ApiResponse(responseCode = "400", description = "Invite is revoked, expired, or already used"),
            @ApiResponse(responseCode = "404", description = "Invite or group not found"),
            @ApiResponse(responseCode = "409", description = "Already a member of this group")
    })
    public ResponseEntity<JoinInviteResponse> join(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody JoinInviteRequest request
    ) {
        return ResponseEntity.ok(groupInviteService.joinViaInvite(userId, request.getToken()));
    }

    @GetMapping("/mine")
    @Operation(
            summary = "List invites addressed to me",
            description = "Lists pending email invites sent to the caller's own account email, across all groups"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invites retrieved successfully")
    })
    public ResponseEntity<List<MyInviteResponse>> listMine(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(groupInviteService.listMyInvites(userId));
    }

    @PostMapping("/{inviteId}/accept")
    @Operation(
            summary = "Accept an invite addressed to me",
            description = "Joins the group for a pending email invite sent to the caller's own account email"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Joined group successfully"),
            @ApiResponse(responseCode = "400", description = "Invite is revoked, expired, or already used"),
            @ApiResponse(responseCode = "404", description = "Invite not found"),
            @ApiResponse(responseCode = "409", description = "Already a member of this group")
    })
    public ResponseEntity<JoinInviteResponse> accept(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long inviteId
    ) {
        return ResponseEntity.ok(groupInviteService.acceptMyInvite(userId, inviteId));
    }

    @PostMapping("/{inviteId}/decline")
    @Operation(
            summary = "Decline an invite addressed to me",
            description = "Revokes a pending email invite sent to the caller's own account email"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Invite declined successfully"),
            @ApiResponse(responseCode = "404", description = "Invite not found")
    })
    public ResponseEntity<Void> decline(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long inviteId
    ) {
        groupInviteService.declineMyInvite(userId, inviteId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
