package com.billbuddy.backend.features.groups.controller;

import com.billbuddy.backend.features.groups.dto.request.CreateGroupRequest;
import com.billbuddy.backend.features.groups.dto.request.UpdateGroupRequest;
import com.billbuddy.backend.features.groups.dto.response.GroupResponse;
import com.billbuddy.backend.features.groups.service.GroupService;
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
@RequestMapping("/api/v1/groups")
@Tag(name = "Groups", description = "Group management APIs")
@SecurityRequirement(name = "bearerAuth")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @Operation(summary = "Create a group", description = "Creates a new group; the creator becomes its first Admin")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Group created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<GroupResponse> createGroup(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateGroupRequest request
    ) {
        GroupResponse response = groupService.createGroup(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List my groups", description = "Lists all groups the authenticated user is an active member of")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Groups retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<GroupResponse>> listGroups(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(groupService.listGroups(userId));
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Get group details", description = "Returns group details; caller must be an active member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Group retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<GroupResponse> getGroup(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId
    ) {
        return ResponseEntity.ok(groupService.getGroup(groupId, userId));
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "Update a group", description = "Updates name/description/default currency; Admin only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Group updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "403", description = "Not a group admin"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<GroupResponse> updateGroup(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId,
            @Valid @RequestBody UpdateGroupRequest request
    ) {
        return ResponseEntity.ok(groupService.updateGroup(groupId, userId, request));
    }

    @DeleteMapping("/{groupId}")
    @Operation(summary = "Delete a group", description = "Soft-deletes a group; Admin only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Group deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Not a group admin"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<Void> deleteGroup(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId
    ) {
        groupService.deleteGroup(groupId, userId);
        return ResponseEntity.noContent().build();
    }
}
