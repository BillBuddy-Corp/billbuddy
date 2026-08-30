package com.billbuddy.backend.features.groups.controller;

import com.billbuddy.backend.features.groups.dto.request.ChangeMemberRoleRequest;
import com.billbuddy.backend.features.groups.dto.response.GroupMemberResponse;
import com.billbuddy.backend.features.groups.service.GroupMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/members")
@Tag(name = "Group Members", description = "Group member and role management APIs")
@SecurityRequirement(name = "bearerAuth")
public class GroupMemberController {

    private final GroupMemberService groupMemberService;

    public GroupMemberController(GroupMemberService groupMemberService) {
        this.groupMemberService = groupMemberService;
    }

    @GetMapping
    @Operation(summary = "List members", description = "Lists all active members of the group; caller must be a member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Members retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<List<GroupMemberResponse>> listMembers(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId
    ) {
        return ResponseEntity.ok(groupMemberService.listMembers(groupId, userId));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Remove or leave", description = "Removes a member (Admin only) or lets a member remove themselves")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Member removed successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized to remove this member"),
            @ApiResponse(responseCode = "404", description = "Group or member not found"),
            @ApiResponse(responseCode = "409", description = "Cannot leave as the sole remaining admin")
    })
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long groupId,
            @PathVariable Long userId
    ) {
        groupMemberService.removeMember(groupId, requesterId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/role")
    @Operation(summary = "Change member role", description = "Promotes/demotes a member; Admin only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Role changed successfully"),
            @ApiResponse(responseCode = "403", description = "Not a group admin"),
            @ApiResponse(responseCode = "404", description = "Group or member not found"),
            @ApiResponse(responseCode = "409", description = "Cannot demote the sole remaining admin")
    })
    public ResponseEntity<GroupMemberResponse> changeRole(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeMemberRoleRequest request
    ) {
        return ResponseEntity.ok(
                groupMemberService.changeRole(groupId, requesterId, userId, request.getRole())
        );
    }
}
