package com.secretsanta.group.service

import com.secretsanta.common.group.commands.DrawNamesCommand
import com.secretsanta.group.entity.DrawAssignment
import com.secretsanta.group.entity.Group
import com.secretsanta.group.entity.GroupMember
import com.secretsanta.group.repository.DrawAssignmentRepository
import com.secretsanta.group.repository.GroupRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional
import java.util.Random
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DrawServiceTest {
    @Mock
    lateinit var groupRepository: GroupRepository

    @Mock
    lateinit var drawAssignmentRepository: DrawAssignmentRepository

    private lateinit var drawService: DrawService

    @BeforeEach
    fun setUp() {
        drawService = DrawService(groupRepository, drawAssignmentRepository)
    }

    @Test
    fun `assigns each member once in a cycle`() {
        val group = buildGroupWithMembers(5)
        `when`(groupRepository.findById(UUID.fromString(GROUP_ID)))
            .thenReturn(Optional.of(group))

        val event = drawService.drawNames(validCommand(), Random(42))
        val assignments = event.getAssignments()

        assertThat(assignments).hasSize(5)
        val givers = assignments.map { it.getGiverId() }.toSet()
        val receivers = assignments.map { it.getReceiverId() }.toSet()
        assertThat(givers).hasSize(5)
        assertThat(receivers).hasSize(5)
        assignments.forEach { assignment ->
            assertThat(assignment.getGiverId()).isNotEqualTo(assignment.getReceiverId())
        }

        @Suppress("UNCHECKED_CAST")
        val assignmentsCaptor = ArgumentCaptor.forClass(Iterable::class.java)
            as ArgumentCaptor<Iterable<DrawAssignment>>
        verify(drawAssignmentRepository).saveAll(assignmentsCaptor.capture())
        assertThat(assignmentsCaptor.value.toList()).hasSize(5)
        assertThat(group.drawn).isTrue()
    }

    @Test
    fun `rejects a group with fewer than three members`() {
        val group = buildGroupWithMembers(2)
        `when`(groupRepository.findById(UUID.fromString(GROUP_ID)))
            .thenReturn(Optional.of(group))

        assertThatThrownBy { drawService.drawNames(validCommand(), Random(42)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("at least 3 members")
    }

    @Test
    fun `rejects a group that has already been drawn`() {
        val group = buildGroupWithMembers(3).apply { drawn = true }
        `when`(groupRepository.findById(UUID.fromString(GROUP_ID)))
            .thenReturn(Optional.of(group))

        assertThatThrownBy { drawService.drawNames(validCommand(), Random(42)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already been performed")
    }

    @Test
    fun `rejects a draw requested by someone other than the owner`() {
        val group = buildGroupWithMembers(3)
        `when`(groupRepository.findById(UUID.fromString(GROUP_ID)))
            .thenReturn(Optional.of(group))

        val command = DrawNamesCommand.builder()
            .groupId(GROUP_ID)
            .requestedBy("not-the-owner")
            .build()

        assertThatThrownBy { drawService.drawNames(command, Random(42)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Only the group owner")
    }

    @Test
    fun `assignments form one cycle`() {
        val group = buildGroupWithMembers(5)
        `when`(groupRepository.findById(UUID.fromString(GROUP_ID)))
            .thenReturn(Optional.of(group))

        val assignments = drawService.drawNames(validCommand(), Random(42))
            .getAssignments()
        val giverToReceiver = assignments.associate {
            it.getGiverId() to it.getReceiverId()
        }
        val start = assignments.first().getGiverId()
        var current = start
        val visited = mutableSetOf<String>()

        do {
            visited += current
            current = giverToReceiver.getValue(current)
        } while (current != start)

        assertThat(visited).hasSize(5)
    }

    private fun validCommand() = DrawNamesCommand.builder()
        .groupId(GROUP_ID)
        .requestedBy(OWNER_ID)
        .build()
        .apply { initDefaults("DRAW_NAMES") }

    private fun buildGroupWithMembers(count: Int): Group = Group().apply {
        val group = this
        id = UUID.fromString(GROUP_ID)
        name = "Test Group"
        ownerId = OWNER_ID
        maxMembers = 10
        members = (0 until count).map { index ->
            GroupMember().apply {
                this.group = group
                userId = "user-${index + 1}"
                userName = "User ${index + 1}"
                role = if (index == 0) "ADMIN" else "MEMBER"
            }
        }.toMutableList()
    }

    private companion object {
        const val GROUP_ID = "11111111-1111-1111-1111-111111111111"
        const val OWNER_ID = "owner-001"
    }
}
