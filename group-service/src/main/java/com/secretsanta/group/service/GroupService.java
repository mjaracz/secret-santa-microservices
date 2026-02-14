package com.secretsanta.group.service;

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

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Transactional
    public GroupCreatedEvent createGroup(CreateGroupCommand command) {
        if (groupRepository.existsByNameAndOwnerId(command.getName(), command.getOwnerId())) {
            throw new IllegalArgumentException(
                    "Group with name '" + command.getName() + "' already exists for this owner");
        }

        Group group = Group.builder()
                .name(command.getName())
                .description(command.getDescription())
                .ownerId(command.getOwnerId())
                .maxMembers(command.getMaxMembers())
                .build();

        Group savedGroup = groupRepository.save(group);
        log.info("Group created with ID: {}", savedGroup.getId());

        GroupMember ownerMember = GroupMember.builder()
                .group(savedGroup)
                .userId(command.getOwnerId())
                .userName("Owner")
                .role("ADMIN")
                .build();
        groupMemberRepository.save(ownerMember);
        log.info("Owner added as ADMIN member for group: {}", savedGroup.getId());

        GroupCreatedEvent event = GroupCreatedEvent.builder()
                .groupId(savedGroup.getId().toString())
                .name(savedGroup.getName())
                .description(savedGroup.getDescription())
                .ownerId(savedGroup.getOwnerId())
                .build();
        event.initDefaults("GROUP_CREATED");

        return event;
    }

    @Transactional
    public GroupUpdatedEvent updateGroup(UpdateGroupCommand command) {
        UUID groupId = UUID.fromString(command.getGroupId());
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + command.getGroupId()));

        if (command.getName() != null) {
            group.setName(command.getName());
        }
        if (command.getDescription() != null) {
            group.setDescription(command.getDescription());
        }
        if (command.getMaxMembers() > 0) {
            group.setMaxMembers(command.getMaxMembers());
        }

        groupRepository.save(group);
        log.info("Group updated: {}", groupId);

        GroupUpdatedEvent event = GroupUpdatedEvent.builder()
                .groupId(group.getId().toString())
                .name(group.getName())
                .description(group.getDescription())
                .build();
        event.initDefaults("GROUP_UPDATED");

        return event;
    }

    @Transactional
    public GroupDeletedEvent deleteGroup(DeleteGroupCommand command) {
        UUID groupId = UUID.fromString(command.getGroupId());
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + command.getGroupId()));

        if (!group.getOwnerId().equals(command.getOwnerId())) {
            throw new IllegalArgumentException("Only the group owner can delete the group");
        }

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
        UUID groupId = UUID.fromString(command.getGroupId());
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + command.getGroupId()));

        if (group.getMembers().size() >= group.getMaxMembers()) {
            throw new IllegalArgumentException("Group has reached maximum member limit: " + group.getMaxMembers());
        }

        if (groupMemberRepository.existsByGroupAndUserId(group, command.getUserId())) {
            throw new IllegalArgumentException("User is already a member of this group");
        }

        String role = command.getRole() != null ? command.getRole() : "MEMBER";

        GroupMember member = GroupMember.builder()
                .group(group)
                .userId(command.getUserId())
                .userEmail(command.getUserEmail())
                .userName(command.getUserName())
                .role(role)
                .build();

        groupMemberRepository.save(member);
        log.info("Member {} added to group {}", command.getUserId(), groupId);

        MemberAddedEvent event = MemberAddedEvent.builder()
                .groupId(command.getGroupId())
                .userId(command.getUserId())
                .userName(command.getUserName())
                .role(role)
                .build();
        event.initDefaults("MEMBER_ADDED");

        return event;
    }
}
