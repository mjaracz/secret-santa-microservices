package com.secretsanta.group.service

import com.secretsanta.common.group.commands.GetMyGroupsCommand
import com.secretsanta.group.entity.Group
import com.secretsanta.group.entity.GroupMember
import com.secretsanta.group.repository.GroupRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GroupQueryServiceTest {
    @Mock
    lateinit var groupRepository: GroupRepository

    private lateinit var groupQueryService: GroupQueryService

    @BeforeEach
    fun setUp() {
        groupQueryService = GroupQueryService(groupRepository)
    }

    @Test
    fun `returns summaries for the trimmed requester id`() {
        val group = Group().apply {
            id = GROUP_ID
            name = "Secret Santa"
            description = "Friends exchange"
            ownerId = REQUESTER_ID
            maxMembers = 8
            drawn = true
            createdAt = CREATED_AT
            members = mutableListOf(
                GroupMember().apply { userId = REQUESTER_ID },
                GroupMember().apply { userId = "user-456" }
            )
        }
        `when`(
            groupRepository.findDistinctByMembers_UserIdOrderByCreatedAtDesc(
                REQUESTER_ID
            )
        ).thenReturn(listOf(group))

        val event = groupQueryService.getMyGroups(
            GetMyGroupsCommand.builder()
                .requestedBy("  $REQUESTER_ID  ")
                .build()
        )

        assertThat(event.getEventType()).isEqualTo("MY_GROUPS_FETCHED")
        assertThat(event.getGroups()).hasSize(1)
        assertThat(event.getGroups().first().groupId()).isEqualTo(GROUP_ID.toString())
        assertThat(event.getGroups().first().name()).isEqualTo("Secret Santa")
        assertThat(event.getGroups().first().memberCount()).isEqualTo(2)
        assertThat(event.getGroups().first().drawn()).isTrue()
        assertThat(event.getGroups().first().createdAt()).isEqualTo(CREATED_AT)
        verify(groupRepository)
            .findDistinctByMembers_UserIdOrderByCreatedAtDesc(REQUESTER_ID)
    }

    @Test
    fun `rejects a null command`() {
        assertThatThrownBy { groupQueryService.getMyGroups(null) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Requester ID is required")
    }

    @Test
    fun `rejects a blank requester id`() {
        val command = GetMyGroupsCommand.builder()
            .requestedBy("  ")
            .build()

        assertThatThrownBy { groupQueryService.getMyGroups(command) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Requester ID is required")
    }

    private companion object {
        const val REQUESTER_ID = "user-123"
        val GROUP_ID: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val CREATED_AT: Instant = Instant.parse("2026-01-02T03:04:05Z")
    }
}
