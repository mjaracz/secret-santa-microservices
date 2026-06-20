package com.secretsanta.group.service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.secretsanta.common.group.commands.AddMemberCommand;
import com.secretsanta.common.group.commands.CreateGroupCommand;
import com.secretsanta.common.group.commands.DeleteGroupCommand;
import com.secretsanta.common.group.commands.UpdateGroupCommand;
import com.secretsanta.common.group.events.GroupCreatedEvent;
import com.secretsanta.common.group.events.GroupDeletedEvent;
import com.secretsanta.common.group.events.GroupUpdatedEvent;
import com.secretsanta.common.group.events.MemberAddedEvent;
import com.secretsanta.group.entity.Group;
import com.secretsanta.group.entity.GroupMember;
import com.secretsanta.group.repository.GroupMemberRepository;
import com.secretsanta.group.repository.GroupRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private static final int MIN_MEMBERS = 3;
    private static final String DEFAULT_MEMBER_ROLE = "MEMBER";
    private static final Set<String> ALLOWED_MEMBER_ROLES = Set.of("MEMBER", "ADMIN");

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupAuthorizationService authorizationService;

    @Transactional
    public GroupCreatedEvent createGroup(CreateGroupCommand command) {
        requireCommand(command);
        requireText(command.getName(), "Group name is required");
        requireText(command.getOwnerId(), "Owner ID is required");
        validateMaxMembers(command.getMaxMembers());

        String ownerId = command.getOwnerId();
        if (groupRepository.existsByNameAndOwnerId(command.getName(), ownerId)) {
            throw new IllegalArgumentException(
                    "Group with name '" + command.getName() + "' already exists for this owner");
        }

        Group group = Group.builder()
                .name(command.getName())
                .description(command.getDescription())
                .ownerId(ownerId)
                .maxMembers(command.getMaxMembers())
                .build();

        Group savedGroup = groupRepository.save(group);
        log.info("Group created with ID: {}", savedGroup.getId());

        GroupMember ownerMember = GroupMember.builder()
                .group(savedGroup)
                .userId(ownerId)
                .userName("Owner")
                .role("ADMIN")
                .build();
        groupMemberRepository.save(ownerMember);
        savedGroup.getMembers().add(ownerMember);
        log.info("Owner added as ADMIN member for group: {}", savedGroup.getId());

        GroupCreatedEvent event = GroupCreatedEvent.builder()
                .groupId(savedGroup.getId().toString())
                .name(savedGroup.getName())
                .description(savedGroup.getDescription())
                .ownerId(savedGroup.getOwnerId())
                .maxMembers(savedGroup.getMaxMembers())
                .build();
        event.initDefaults("GROUP_CREATED");

        return event;
    }

    @Transactional
    public GroupUpdatedEvent updateGroup(UpdateGroupCommand command) {
        requireCommand(command);
        UUID groupId = UUID.fromString(command.getGroupId());
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + command.getGroupId()));

        authorizationService.requireOwnerForUpdate(group, command.getRequestedBy());

        if (command.getName() == null
                && command.getDescription() == null
                && command.getMaxMembers() == null) {
            throw new IllegalArgumentException("At least one group field must be provided for update");
        }

        if (command.getName() != null) {
            requireText(command.getName(), "Group name must not be blank");
            if (!command.getName().equals(group.getName())
                    && groupRepository.existsByNameAndOwnerIdAndIdNot(
                            command.getName(), group.getOwnerId(), group.getId())) {
                throw new IllegalArgumentException(
                        "Group with name '" + command.getName() + "' already exists for this owner");
            }
            group.setName(command.getName());
        }
        if (command.getDescription() != null) {
            group.setDescription(command.getDescription());
        }
        if (command.getMaxMembers() != null) {
            validateMaxMembers(command.getMaxMembers());
            if (command.getMaxMembers() < group.getMembers().size()) {
                throw new IllegalArgumentException(
                        "Maximum member limit cannot be lower than current member count: "
                                + group.getMembers().size());
            }
            group.setMaxMembers(command.getMaxMembers());
        }

        groupRepository.save(group);
        log.info("Group updated: {}", groupId);

        GroupUpdatedEvent event = GroupUpdatedEvent.builder()
                .groupId(group.getId().toString())
                .name(group.getName())
                .description(group.getDescription())
                .maxMembers(group.getMaxMembers())
                .build();
        event.initDefaults("GROUP_UPDATED");

        return event;
    }

    @Transactional
    public GroupDeletedEvent deleteGroup(DeleteGroupCommand command) {
        requireCommand(command);
        UUID groupId = UUID.fromString(command.getGroupId());
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + command.getGroupId()));

        authorizationService.requireOwnerForDelete(group, command.getOwnerId());

        groupRepository.delete(group);
        log.info("Group deleted: {}", groupId);

        GroupDeletedEvent event = GroupDeletedEvent.builder()
                .groupId(command.getGroupId())
                .build();
        event.initDefaults("GROUP_DELETED");

        return event;
    }

    @Transactional
    public MemberAddedEvent addMember(AddMemberCommand command) {
        requireCommand(command);
        UUID groupId = UUID.fromString(command.getGroupId());
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + command.getGroupId()));

        authorizationService.requireOwnerForAddingMember(group, command.getRequestedBy());

        requireText(command.getUserId(), "User ID is required");
        requireText(command.getUserName(), "User name is required");

        if (group.getMembers().size() >= group.getMaxMembers()) {
            throw new IllegalArgumentException("Group has reached maximum member limit: " + group.getMaxMembers());
        }

        if (groupMemberRepository.existsByGroupAndUserId(group, command.getUserId())) {
            throw new IllegalArgumentException("User is already a member of this group");
        }

        String role = normalizeRole(command.getRole());

        GroupMember member = GroupMember.builder()
                .group(group)
                .userId(command.getUserId())
                .userEmail(command.getUserEmail())
                .userName(command.getUserName())
                .role(role)
                .build();

        groupMemberRepository.save(member);
        group.getMembers().add(member);
        log.info("Member {} added to group {}", command.getUserId(), groupId);

        MemberAddedEvent event = MemberAddedEvent.builder()
                .groupId(command.getGroupId())
                .userId(command.getUserId())
                .userEmail(command.getUserEmail())
                .userName(command.getUserName())
                .role(role)
                .build();
        event.initDefaults("MEMBER_ADDED");

        return event;
    }

    private static void validateMaxMembers(int maxMembers) {
        if (maxMembers < MIN_MEMBERS) {
            throw new IllegalArgumentException(
                    "Maximum member limit must be at least " + MIN_MEMBERS);
        }
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return DEFAULT_MEMBER_ROLE;
        }

        String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_MEMBER_ROLES.contains(normalizedRole)) {
            throw new IllegalArgumentException("Unsupported group member role: " + role);
        }
        return normalizedRole;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireCommand(Object command) {
        if (command == null) {
            throw new IllegalArgumentException("Command is required");
        }
    }
}
