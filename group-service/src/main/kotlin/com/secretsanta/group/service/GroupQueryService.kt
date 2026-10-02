package com.secretsanta.group.service

import com.secretsanta.common.group.commands.GetMyGroupsCommand
import com.secretsanta.common.group.dto.GroupSummaryDto
import com.secretsanta.common.group.events.MyGroupsFetchedEvent
import com.secretsanta.group.entity.Group
import com.secretsanta.group.repository.GroupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GroupQueryService(
    private val groupRepository: GroupRepository
) {
    @Transactional(readOnly = true)
    fun getMyGroups(command: GetMyGroupsCommand?): MyGroupsFetchedEvent {
        val requesterId = command?.getRequestedBy()
            ?.takeIf(String::isNotBlank)
            ?.trim()
            ?: throw IllegalArgumentException("Requester ID is required")

        val groups = groupRepository
            .findDistinctByMembers_UserIdOrderByCreatedAtDesc(requesterId)
            .map(::toSummary)

        return MyGroupsFetchedEvent.builder()
            .groups(groups)
            .build()
            .apply { initDefaults("MY_GROUPS_FETCHED") }
    }

    private fun toSummary(group: Group) = GroupSummaryDto(
        requireNotNull(group.id).toString(),
        group.name,
        group.description,
        group.ownerId,
        group.maxMembers,
        group.members.size,
        group.drawn,
        requireNotNull(group.createdAt)
    )
}
