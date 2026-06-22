package com.secretsanta.group.service;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.user.UserRole;
import com.secretsanta.group.entity.Group;
import com.secretsanta.group.entity.GroupMember;
import com.secretsanta.group.exception.GroupCommandException;
import com.secretsanta.group.repository.GroupMemberRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class GroupAuthorizationService {

    private static final String FORBIDDEN_ERROR_CODE = "AUTH_FORBIDDEN";

    private final GroupMemberRepository groupMemberRepository;

    public GroupAuthorizationService(
            GroupMemberRepository groupMemberRepository
    ) {
        this.groupMemberRepository = groupMemberRepository;
    }

    public String requireActor(BaseCommand command) {
        if (command == null
                || command.getActorId() == null
                || command.getActorId().isBlank()) {
            throw forbidden();
        }
        return command.getActorId();
    }

    public void requireGroupAdmin(Group group, BaseCommand command) {
        String actorId = requireActor(command);
        if (isGlobalAdmin(command) || group.getOwnerId().equals(actorId)) {
            return;
        }

        boolean isGroupAdmin = groupMemberRepository
                .findByGroupAndUserId(group, actorId)
                .map(GroupMember::getRole)
                .map("ADMIN"::equals)
                .orElse(false);
        if (!isGroupAdmin) {
            throw forbidden();
        }
    }

    public void requireOwner(Group group, BaseCommand command) {
        String actorId = requireActor(command);
        if (isGlobalAdmin(command) || group.getOwnerId().equals(actorId)) {
            return;
        }
        throw forbidden();
    }

    private boolean isGlobalAdmin(BaseCommand command) {
        Set<UserRole> roles = command.getActorRoles();
        return roles != null && roles.contains(UserRole.ADMIN);
    }

    private GroupCommandException forbidden() {
        return new GroupCommandException(
                FORBIDDEN_ERROR_CODE,
                "You do not have permission to perform this operation"
        );
    }
}
