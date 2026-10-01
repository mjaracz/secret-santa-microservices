package com.secretsanta.group.service

import com.secretsanta.common.group.commands.AddMemberCommand
import com.secretsanta.common.group.commands.CreateGroupCommand
import com.secretsanta.common.group.commands.DeleteGroupCommand
import com.secretsanta.common.group.commands.UpdateGroupCommand
import com.secretsanta.common.group.events.GroupCreatedEvent
import com.secretsanta.common.group.events.GroupDeletedEvent
import com.secretsanta.common.group.events.GroupUpdatedEvent
import com.secretsanta.common.group.events.MemberAddedEvent
import com.secretsanta.group.entity.Group
import com.secretsanta.group.entity.GroupMember
import com.secretsanta.group.repository.GroupMemberRepository
import com.secretsanta.group.repository.GroupRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GroupService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository
) {
    @Transactional
    fun createGroup(command: CreateGroupCommand): GroupCreatedEvent {
        val name = command.getName()
        val ownerId = command.getOwnerId()

        if (groupRepository.existsByNameAndOwnerId(name, ownerId)) {
            throw IllegalArgumentException(
                "Group with name '$name' already exists for this owner"
            )
        }

        val group = Group().apply {
            this.name = name
            description = command.getDescription()
            this.ownerId = ownerId
            maxMembers = command.getMaxMembers()
        }

        val savedGroup = groupRepository.save(group)
        val groupId = requireNotNull(savedGroup.id)
        log.info("Group created with ID: {}", groupId)

        val ownerMember = GroupMember().apply {
            this.group = savedGroup
            userId = ownerId
            userName = "Owner"
            role = "ADMIN"
        }
        groupMemberRepository.save(ownerMember)
        log.info("Owner added as ADMIN member for group: {}", groupId)

        return GroupCreatedEvent.builder()
            .groupId(groupId.toString())
            .name(savedGroup.name)
            .description(savedGroup.description)
            .ownerId(savedGroup.ownerId)
            .build()
            .apply { initDefaults("GROUP_CREATED") }
    }

    @Transactional
    fun updateGroup(command: UpdateGroupCommand): GroupUpdatedEvent {
        val groupId = UUID.fromString(command.getGroupId())
        val group = groupRepository.findById(groupId)
            .orElseThrow {
                IllegalArgumentException("Group not found: ${command.getGroupId()}")
            }

        command.getName()?.let { group.name = it }
        command.getDescription()?.let { group.description = it }
        if (command.getMaxMembers() > 0) {
            group.maxMembers = command.getMaxMembers()
        }

        groupRepository.save(group)
        log.info("Group updated: {}", groupId)

        return GroupUpdatedEvent.builder()
            .groupId(requireNotNull(group.id).toString())
            .name(group.name)
            .description(group.description)
            .build()
            .apply { initDefaults("GROUP_UPDATED") }
    }

    @Transactional
    fun deleteGroup(command: DeleteGroupCommand): GroupDeletedEvent {
        val groupId = UUID.fromString(command.getGroupId())
        val group = groupRepository.findById(groupId)
            .orElseThrow {
                IllegalArgumentException("Group not found: ${command.getGroupId()}")
            }

        if (group.ownerId != command.getOwnerId()) {
            throw IllegalArgumentException("Only the group owner can delete the group")
        }

        groupRepository.delete(group)
        log.info("Group deleted: {}", groupId)

        return GroupDeletedEvent.builder()
            .groupId(command.getGroupId())
            .build()
            .apply { initDefaults("GROUP_DELETED") }
    }

    @Transactional
    fun addMember(command: AddMemberCommand): MemberAddedEvent {
        val groupId = UUID.fromString(command.getGroupId())
        val group = groupRepository.findById(groupId)
            .orElseThrow {
                IllegalArgumentException("Group not found: ${command.getGroupId()}")
            }

        if (group.members.size >= group.maxMembers) {
            throw IllegalArgumentException(
                "Group has reached maximum member limit: ${group.maxMembers}"
            )
        }

        if (groupMemberRepository.existsByGroupAndUserId(group, command.getUserId())) {
            throw IllegalArgumentException("User is already a member of this group")
        }

        val role = command.getRole() ?: "MEMBER"
        val member = GroupMember().apply {
            this.group = group
            userId = command.getUserId()
            userEmail = command.getUserEmail()
            userName = command.getUserName()
            this.role = role
        }

        groupMemberRepository.save(member)
        log.info("Member {} added to group {}", command.getUserId(), groupId)

        return MemberAddedEvent.builder()
            .groupId(command.getGroupId())
            .userId(command.getUserId())
            .userName(command.getUserName())
            .role(role)
            .build()
            .apply { initDefaults("MEMBER_ADDED") }
    }

    private companion object {
        val log = LoggerFactory.getLogger(GroupService::class.java)
    }
}
