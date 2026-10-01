package com.secretsanta.group.service

import com.secretsanta.common.group.commands.DrawNamesCommand
import com.secretsanta.common.group.dto.DrawAssignmentDto
import com.secretsanta.common.group.events.DrawCompletedEvent
import com.secretsanta.group.entity.DrawAssignment
import com.secretsanta.group.entity.Group
import com.secretsanta.group.entity.GroupMember
import com.secretsanta.group.repository.DrawAssignmentRepository
import com.secretsanta.group.repository.GroupRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Collections
import java.util.Random
import java.util.UUID

@Service
class DrawService(
    private val groupRepository: GroupRepository,
    private val drawAssignmentRepository: DrawAssignmentRepository
) {
    @Transactional
    fun drawNames(
        command: DrawNamesCommand,
        random: Random = Random()
    ): DrawCompletedEvent {
        val groupId = UUID.fromString(command.getGroupId())
        val group = groupRepository.findById(groupId)
            .orElseThrow {
                IllegalArgumentException("Group not found: ${command.getGroupId()}")
            }

        val shuffled = getGroupMembers(command, group)
        Collections.shuffle(shuffled, random)

        val assignments = mutableListOf<DrawAssignment>()
        val assignmentDtos = mutableListOf<DrawAssignmentDto>()

        for (index in shuffled.indices) {
            val giver = shuffled[index]
            val receiver = shuffled[(index + 1) % shuffled.size]

            assignments += DrawAssignment().apply {
                this.group = group
                giverId = giver.userId
                giverName = giver.userName
                receiverId = receiver.userId
                receiverName = receiver.userName
            }

            assignmentDtos += DrawAssignmentDto.builder()
                .giverId(giver.userId)
                .giverName(giver.userName)
                .receiverId(receiver.userId)
                .receiverName(receiver.userName)
                .build()
        }

        drawAssignmentRepository.saveAll(assignments)
        group.drawn = true
        groupRepository.save(group)
        log.info(
            "Draw completed for group {} with {} assignments",
            groupId,
            assignments.size
        )

        return DrawCompletedEvent.builder()
            .groupId(command.getGroupId())
            .assignments(assignmentDtos)
            .build()
            .apply { initDefaults("DRAW_COMPLETED") }
    }

    private fun getGroupMembers(
        command: DrawNamesCommand,
        group: Group
    ): MutableList<GroupMember> {
        if (group.ownerId != command.getRequestedBy()) {
            throw IllegalArgumentException("Only the group owner can trigger a draw")
        }

        if (group.drawn) {
            throw IllegalArgumentException("Draw has already been performed for this group")
        }

        if (group.members.size < MINIMUM_MEMBERS) {
            throw IllegalArgumentException(
                "Group must have at least $MINIMUM_MEMBERS members to perform a draw"
            )
        }

        return group.members.toMutableList()
    }

    private companion object {
        const val MINIMUM_MEMBERS = 3
        val log = LoggerFactory.getLogger(DrawService::class.java)
    }
}
