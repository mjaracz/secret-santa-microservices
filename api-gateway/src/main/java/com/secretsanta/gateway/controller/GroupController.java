package com.secretsanta.gateway.controller;

import com.secretsanta.gateway.dto.AddMemberRequest;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateGroupRequest;
import com.secretsanta.gateway.dto.DrawNamesRequest;
import com.secretsanta.gateway.dto.UpdateGroupRequest;
import com.secretsanta.gateway.service.GroupGatewayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final GroupGatewayService groupGatewayService;

    @PostMapping
    public Mono<ResponseEntity<CommandResponse>> createGroup(
            @Valid @RequestBody CreateGroupRequest request) {
        return groupGatewayService.createGroup(request)
                .map(ResponseMapper::toResponseEntity);
    }

    @PutMapping("/{groupId}")
    public Mono<ResponseEntity<CommandResponse>> updateGroup(
            @NotBlank @Pattern(regexp = UUID_PATTERN, message = "Group ID must be a valid UUID")
            @PathVariable String groupId,
            @Valid @RequestBody UpdateGroupRequest request) {
        return groupGatewayService.updateGroup(groupId, request)
                .map(ResponseMapper::toResponseEntity);
    }

    @DeleteMapping("/{groupId}")
    public Mono<ResponseEntity<CommandResponse>> deleteGroup(
            @NotBlank @Pattern(regexp = UUID_PATTERN, message = "Group ID must be a valid UUID")
            @PathVariable String groupId,
            @NotBlank @RequestParam String ownerId) {
        return groupGatewayService.deleteGroup(groupId, ownerId)
                .map(ResponseMapper::toResponseEntity);
    }

    @PostMapping("/{groupId}/members")
    public Mono<ResponseEntity<CommandResponse>> addMember(
            @NotBlank @Pattern(regexp = UUID_PATTERN, message = "Group ID must be a valid UUID")
            @PathVariable String groupId,
            @Valid @RequestBody AddMemberRequest request) {
        return groupGatewayService.addMember(groupId, request)
                .map(ResponseMapper::toResponseEntity);
    }

    @PostMapping("/{groupId}/draw")
    public Mono<ResponseEntity<CommandResponse>> drawNames(
            @NotBlank @Pattern(regexp = UUID_PATTERN, message = "Group ID must be a valid UUID")
            @PathVariable String groupId,
            @Valid @RequestBody DrawNamesRequest request) {
        return groupGatewayService.drawNames(groupId, request)
                .map(ResponseMapper::toResponseEntity);
    }
}
