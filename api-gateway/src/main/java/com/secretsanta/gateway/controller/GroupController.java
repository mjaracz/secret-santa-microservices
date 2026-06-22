package com.secretsanta.gateway.controller;

import com.secretsanta.gateway.dto.AddMemberRequest;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateGroupRequest;
import com.secretsanta.gateway.dto.UpdateGroupRequest;
import com.secretsanta.gateway.security.AuthenticatedActor;
import com.secretsanta.gateway.service.GroupGatewayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return groupGatewayService
                .createGroup(request, AuthenticatedActor.from(jwt))
                .map(response -> ResponseMapper.toResponseEntity(
                        response,
                        HttpStatus.CREATED
                ));
    }

    @PutMapping("/{groupId}")
    public Mono<ResponseEntity<CommandResponse>> updateGroup(
            @NotBlank @Pattern(regexp = UUID_PATTERN, message = "Group ID must be a valid UUID")
            @PathVariable String groupId,
            @Valid @RequestBody UpdateGroupRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return groupGatewayService
                .updateGroup(groupId, request, AuthenticatedActor.from(jwt))
                .map(ResponseMapper::toResponseEntity);
    }

    @DeleteMapping("/{groupId}")
    public Mono<ResponseEntity<CommandResponse>> deleteGroup(
            @NotBlank @Pattern(regexp = UUID_PATTERN, message = "Group ID must be a valid UUID")
            @PathVariable String groupId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return groupGatewayService
                .deleteGroup(groupId, AuthenticatedActor.from(jwt))
                .map(ResponseMapper::toResponseEntity);
    }

    @PostMapping("/{groupId}/members")
    public Mono<ResponseEntity<CommandResponse>> addMember(
            @NotBlank @Pattern(regexp = UUID_PATTERN, message = "Group ID must be a valid UUID")
            @PathVariable String groupId,
            @Valid @RequestBody AddMemberRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return groupGatewayService
                .addMember(groupId, request, AuthenticatedActor.from(jwt))
                .map(ResponseMapper::toResponseEntity);
    }

    @PostMapping("/{groupId}/draw")
    public Mono<ResponseEntity<CommandResponse>> drawNames(
            @NotBlank @Pattern(regexp = UUID_PATTERN, message = "Group ID must be a valid UUID")
            @PathVariable String groupId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return groupGatewayService
                .drawNames(groupId, AuthenticatedActor.from(jwt))
                .map(ResponseMapper::toResponseEntity);
    }
}
