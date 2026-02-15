package com.secretsanta.gateway.controller;

import com.secretsanta.gateway.dto.AddMemberRequest;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateGroupRequest;
import com.secretsanta.gateway.dto.DrawNamesRequest;
import com.secretsanta.gateway.dto.UpdateGroupRequest;
import com.secretsanta.gateway.service.GroupGatewayService;
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

    private final GroupGatewayService groupGatewayService;

    @PostMapping
    public Mono<ResponseEntity<CommandResponse>> createGroup(
            @RequestBody CreateGroupRequest request) {
        return groupGatewayService.createGroup(request)
                .map(ResponseMapper::toResponseEntity);
    }

    @PutMapping("/{groupId}")
    public Mono<ResponseEntity<CommandResponse>> updateGroup(
            @PathVariable String groupId,
            @RequestBody UpdateGroupRequest request) {
        return groupGatewayService.updateGroup(groupId, request)
                .map(ResponseMapper::toResponseEntity);
    }

    @DeleteMapping("/{groupId}")
    public Mono<ResponseEntity<CommandResponse>> deleteGroup(
            @PathVariable String groupId,
            @RequestParam String ownerId) {
        return groupGatewayService.deleteGroup(groupId, ownerId)
                .map(ResponseMapper::toResponseEntity);
    }

    @PostMapping("/{groupId}/members")
    public Mono<ResponseEntity<CommandResponse>> addMember(
            @PathVariable String groupId,
            @RequestBody AddMemberRequest request) {
        return groupGatewayService.addMember(groupId, request)
                .map(ResponseMapper::toResponseEntity);
    }

    @PostMapping("/{groupId}/draw")
    public Mono<ResponseEntity<CommandResponse>> drawNames(
            @PathVariable String groupId,
            @RequestBody DrawNamesRequest request) {
        return groupGatewayService.drawNames(groupId, request)
                .map(ResponseMapper::toResponseEntity);
    }
}
