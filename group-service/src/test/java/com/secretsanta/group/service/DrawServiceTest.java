package com.secretsanta.group.service;

import com.secretsanta.common.group.commands.DrawNamesCommand;
import com.secretsanta.common.group.dto.DrawAssignmentDto;
import com.secretsanta.common.group.events.DrawCompletedEvent;
import com.secretsanta.group.entity.DrawAssignment;
import com.secretsanta.group.entity.Group;
import com.secretsanta.group.entity.GroupMember;
import com.secretsanta.group.repository.DrawAssignmentRepository;
import com.secretsanta.group.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrawServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private DrawAssignmentRepository drawAssignmentRepository;

    @Captor
    private ArgumentCaptor<List<DrawAssignment>> assignmentsCaptor;

    private DrawService drawService;

    private static final String GROUP_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OWNER_ID = "owner-001";

    @BeforeEach
    void setUp() {
        drawService = new DrawService(
                groupRepository,
                drawAssignmentRepository,
                new GroupAuthorizationService());
    }

    @Test
    void draw_assigns_all_members_in_cycle() {
        Group group = buildGroupWithMembers(5);
        when(groupRepository.findById(UUID.fromString(GROUP_ID))).thenReturn(Optional.of(group));

        DrawNamesCommand command = DrawNamesCommand.builder()
                .groupId(GROUP_ID)
                .requestedBy(OWNER_ID)
                .build();
        command.initDefaults("DRAW_NAMES");

        DrawCompletedEvent event = drawService.drawNames(command, new Random(42));

        List<DrawAssignmentDto> assignments = event.getAssignments();
        assertThat(assignments).hasSize(5);

        Set<String> givers = assignments.stream().map(DrawAssignmentDto::getGiverId).collect(Collectors.toSet());
        Set<String> receivers = assignments.stream().map(DrawAssignmentDto::getReceiverId).collect(Collectors.toSet());
        assertThat(givers).hasSize(5);
        assertThat(receivers).hasSize(5);

        for (DrawAssignmentDto a : assignments) {
            assertThat(a.getGiverId()).isNotEqualTo(a.getReceiverId());
        }

        verify(drawAssignmentRepository).saveAll(assignmentsCaptor.capture());
        assertThat(assignmentsCaptor.getValue()).hasSize(5);
        assertThat(group.isDrawn()).isTrue();
    }

    @Test
    void draw_fails_with_fewer_than_3_members() {
        Group group = buildGroupWithMembers(2);
        when(groupRepository.findById(UUID.fromString(GROUP_ID))).thenReturn(Optional.of(group));

        DrawNamesCommand command = DrawNamesCommand.builder()
                .groupId(GROUP_ID)
                .requestedBy(OWNER_ID)
                .build();
        command.initDefaults("DRAW_NAMES");

        assertThatThrownBy(() -> drawService.drawNames(command, new Random(42)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 3 members");
    }

    @Test
    void draw_fails_if_already_drawn() {
        Group group = buildGroupWithMembers(3);
        group.setDrawn(true);
        when(groupRepository.findById(UUID.fromString(GROUP_ID))).thenReturn(Optional.of(group));

        DrawNamesCommand command = DrawNamesCommand.builder()
                .groupId(GROUP_ID)
                .requestedBy(OWNER_ID)
                .build();
        command.initDefaults("DRAW_NAMES");

        assertThatThrownBy(() -> drawService.drawNames(command, new Random(42)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been performed");
    }

    @Test
    void draw_fails_if_not_owner() {
        Group group = buildGroupWithMembers(3);
        when(groupRepository.findById(UUID.fromString(GROUP_ID))).thenReturn(Optional.of(group));

        DrawNamesCommand command = DrawNamesCommand.builder()
                .groupId(GROUP_ID)
                .requestedBy("not-the-owner")
                .build();
        command.initDefaults("DRAW_NAMES");

        assertThatThrownBy(() -> drawService.drawNames(command, new Random(42)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only the group owner");
    }

    @Test
    void draw_produces_valid_single_cycle() {
        Group group = buildGroupWithMembers(5);
        when(groupRepository.findById(UUID.fromString(GROUP_ID))).thenReturn(Optional.of(group));

        DrawNamesCommand command = DrawNamesCommand.builder()
                .groupId(GROUP_ID)
                .requestedBy(OWNER_ID)
                .build();
        command.initDefaults("DRAW_NAMES");

        DrawCompletedEvent event = drawService.drawNames(command, new Random(42));

        Map<String, String> giverToReceiver = new HashMap<>();
        for (DrawAssignmentDto a : event.getAssignments()) {
            giverToReceiver.put(a.getGiverId(), a.getReceiverId());
        }

        String start = event.getAssignments().getFirst().getGiverId();
        String current = start;
        Set<String> visited = new HashSet<>();

        do {
            visited.add(current);
            current = giverToReceiver.get(current);
        } while (!current.equals(start));

        assertThat(visited).hasSize(5);
    }

    private Group buildGroupWithMembers(int count) {
        Group group = Group.builder()
                .id(UUID.fromString(GROUP_ID))
                .name("Test Group")
                .ownerId(OWNER_ID)
                .maxMembers(10)
                .build();

        List<GroupMember> members = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            members.add(GroupMember.builder()
                    .id(UUID.randomUUID())
                    .group(group)
                    .userId("user-" + (i + 1))
                    .userName("User " + (i + 1))
                    .role(i == 0 ? "ADMIN" : "MEMBER")
                    .build());
        }
        group.setMembers(members);

        return group;
    }
}
