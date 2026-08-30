package com.billbuddy.backend.features.groups.controller;

import com.billbuddy.backend.features.groups.dto.request.JoinInviteRequest;
import com.billbuddy.backend.features.groups.dto.response.JoinInviteResponse;
import com.billbuddy.backend.features.groups.service.GroupInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
