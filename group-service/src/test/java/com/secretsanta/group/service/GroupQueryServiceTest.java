package com.secretsanta.group.service;

import com.secretsanta.common.group.commands.GetMyGroupsCommand;
import com.secretsanta.common.group.events.MyGroupsFetchedEvent;
import com.secretsanta.group.entity.Group;
import com.secretsanta.group.entity.GroupMember;
import com.secretsanta.group.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupQueryServiceTest {

    private static final String REQUESTER_ID = "user-123";
    private static final UUID GROUP_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CREATED_AT = Instant.parse("2026-01-02T03:04:05Z");

    @Mock
    private GroupRepository groupRepository;

    private GroupQueryService groupQueryService;

    @BeforeEach
    void setUp() {
        groupQueryService = new GroupQueryService(groupRepository);
    }

    @Test
    void returnsGroupSummariesForTrimmedRequesterId() {
        Group group = Group.builder()
                .id(GROUP_ID)
                .name("Secret Santa")
                .description("Friends exchange")
                .ownerId(REQUESTER_ID)
                .maxMembers(8)
                .drawn(true)
                .createdAt(CREATED_AT)
                .members(List.of(
                        GroupMember.builder().userId(REQUESTER_ID).build(),
                        GroupMember.builder().userId("user-456").build()
                ))
                .build();
        when(groupRepository.findDistinctByMembers_UserIdOrderByCreatedAtDesc(
                REQUESTER_ID
        )).thenReturn(List.of(group));

        MyGroupsFetchedEvent event = groupQueryService.getMyGroups(
                GetMyGroupsCommand.builder()
                        .requestedBy("  " + REQUESTER_ID + "  ")
                        .build()
        );

        assertThat(event.getEventType()).isEqualTo("MY_GROUPS_FETCHED");
        assertThat(event.getGroups()).hasSize(1);
        assertThat(event.getGroups().getFirst().groupId()).isEqualTo(GROUP_ID.toString());
        assertThat(event.getGroups().getFirst().name()).isEqualTo("Secret Santa");
        assertThat(event.getGroups().getFirst().memberCount()).isEqualTo(2);
        assertThat(event.getGroups().getFirst().drawn()).isTrue();
        assertThat(event.getGroups().getFirst().createdAt()).isEqualTo(CREATED_AT);
        verify(groupRepository)
                .findDistinctByMembers_UserIdOrderByCreatedAtDesc(REQUESTER_ID);
    }

    @Test
    void rejectsNullCommand() {
        assertThatThrownBy(() -> groupQueryService.getMyGroups(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Requester ID is required");
    }

    @Test
    void rejectsBlankRequesterId() {
        assertThatThrownBy(() -> groupQueryService.getMyGroups(
                GetMyGroupsCommand.builder().requestedBy("  ").build()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Requester ID is required");
    }
}
