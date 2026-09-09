package com.billbuddy.backend.features.groups.controller;

import com.billbuddy.backend.features.groups.dto.request.EmailInviteRequest;
import com.billbuddy.backend.features.groups.dto.response.GroupInviteResponse;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/invites")
@Tag(name = "Group Invites", description = "Email and shareable-link invite APIs")
@SecurityRequirement(name = "bearerAuth")
public class GroupInviteController {

    private final GroupInviteService groupInviteService;

    public GroupInviteController(GroupInviteService groupInviteService) {
        this.groupInviteService = groupInviteService;
    }

    @PostMapping("/email")
    @Operation(summary = "Send an email invite", description = "Emails an invite link to the given address; any active group member can send one")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Invite sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid email"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Group not found"),
            @ApiResponse(responseCode = "409", description = "User is already a member")
    })
    public ResponseEntity<Map<String, String>> sendEmailInvite(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId,
            @Valid @RequestBody EmailInviteRequest request
    ) {
        groupInviteService.createEmailInvite(groupId, userId, request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Invite sent"));
    }

    @GetMapping
    @Operation(summary = "List pending invites", description = "Lists both email and shareable-link invites; any active group member can view")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invites retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group")
    })
    public ResponseEntity<List<GroupInviteResponse>> listInvites(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId
    ) {
        return ResponseEntity.ok(groupInviteService.listInvites(groupId, userId));
    }

    @DeleteMapping("/{inviteId}")
    @Operation(summary = "Revoke an email invite", description = "Revokes a specific pending invite; Admin only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Invite revoked successfully"),
            @ApiResponse(responseCode = "403", description = "Not a group admin"),
            @ApiResponse(responseCode = "404", description = "Invite not found")
    })
    public ResponseEntity<Void> revokeInvite(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId,
            @PathVariable Long inviteId
    ) {
        groupInviteService.revokeInvite(groupId, userId, inviteId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/link/generate")
    @Operation(summary = "Generate/regenerate shareable link", description = "Creates a new join link, invalidating any prior one; Admin only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Link generated successfully"),
            @ApiResponse(responseCode = "403", description = "Not a group admin"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<GroupInviteResponse> generateLink(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupInviteService.generateLink(groupId, userId));
    }

    @DeleteMapping("/link")
    @Operation(summary = "Disable shareable link", description = "Disables the group's active join link, if any; Admin only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Link disabled successfully"),
            @ApiResponse(responseCode = "403", description = "Not a group admin")
    })
    public ResponseEntity<Void> disableLink(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId
    ) {
        groupInviteService.disableLink(groupId, userId);
        return ResponseEntity.noContent().build();
    }
}
