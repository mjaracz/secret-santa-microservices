package com.secretsanta.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    private static final UUID GROUP_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String OWNER_ID = "owner-001";

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Captor
    private ArgumentCaptor<Group> groupCaptor;

    @Captor
    private ArgumentCaptor<GroupMember> memberCaptor;

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(
                groupRepository,
                groupMemberRepository,
                new GroupAuthorizationService());
    }

    @Test
    void createsGroupWithOwnerAsAdminAndReturnsEvent() {
        CreateGroupCommand command = createGroupCommand();
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
            Group group = invocation.getArgument(0);
            group.setId(GROUP_ID);
            return group;
        });

        GroupCreatedEvent event = groupService.createGroup(command);

        verify(groupRepository).existsByNameAndOwnerId("Family", OWNER_ID);
        verify(groupRepository).save(groupCaptor.capture());
        verify(groupMemberRepository).save(memberCaptor.capture());

        Group savedGroup = groupCaptor.getValue();
        assertThat(savedGroup.getName()).isEqualTo("Family");
        assertThat(savedGroup.getDescription()).isEqualTo("Christmas draw");
        assertThat(savedGroup.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(savedGroup.getMaxMembers()).isEqualTo(8);

        GroupMember owner = memberCaptor.getValue();
        assertThat(owner.getGroup()).isSameAs(savedGroup);
        assertThat(owner.getUserId()).isEqualTo(OWNER_ID);
        assertThat(owner.getUserName()).isEqualTo("Owner");
        assertThat(owner.getRole()).isEqualTo("ADMIN");

        assertThat(event.getGroupId()).isEqualTo(GROUP_ID.toString());
        assertThat(event.getName()).isEqualTo("Family");
        assertThat(event.getDescription()).isEqualTo("Christmas draw");
        assertThat(event.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(event.getMaxMembers()).isEqualTo(8);
        assertInitializedEvent(event.getEventId(), event.getTimestamp(), event.getEventType(), "GROUP_CREATED");
    }

    @Test
    void rejectsDuplicateGroupNameForOwner() {
        when(groupRepository.existsByNameAndOwnerId("Family", OWNER_ID)).thenReturn(true);

        assertThatThrownBy(() -> groupService.createGroup(createGroupCommand()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Group with name 'Family' already exists for this owner");

        verify(groupRepository, never()).save(any(Group.class));
        verifyNoInteractions(groupMemberRepository);
    }

    @Test
    void rejectsGroupWithTooSmallMemberLimit() {
        CreateGroupCommand command = CreateGroupCommand.builder()
                .name("Family")
                .ownerId(OWNER_ID)
                .maxMembers(2)
                .build();

        assertThatThrownBy(() -> groupService.createGroup(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum member limit must be at least 3");

        verifyNoInteractions(groupRepository, groupMemberRepository);
    }

    @Test
    void updatesAllProvidedGroupFieldsAndReturnsEvent() {
        Group group = existingGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        UpdateGroupCommand command = UpdateGroupCommand.builder()
                .groupId(GROUP_ID.toString())
                .requestedBy(OWNER_ID)
                .name("Friends")
                .description("New description")
                .maxMembers(12)
                .build();

        GroupUpdatedEvent event = groupService.updateGroup(command);

        verify(groupRepository).save(group);
        assertThat(group.getName()).isEqualTo("Friends");
        assertThat(group.getDescription()).isEqualTo("New description");
        assertThat(group.getMaxMembers()).isEqualTo(12);
        assertThat(event.getGroupId()).isEqualTo(GROUP_ID.toString());
        assertThat(event.getName()).isEqualTo("Friends");
        assertThat(event.getDescription()).isEqualTo("New description");
        assertThat(event.getMaxMembers()).isEqualTo(12);
        assertInitializedEvent(event.getEventId(), event.getTimestamp(), event.getEventType(), "GROUP_UPDATED");
    }

    @Test
    void preservesFieldsMissingFromUpdateCommand() {
        Group group = existingGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        UpdateGroupCommand command = UpdateGroupCommand.builder()
                .groupId(GROUP_ID.toString())
                .requestedBy(OWNER_ID)
                .description("Only description changed")
                .build();

        groupService.updateGroup(command);

        assertThat(group.getName()).isEqualTo("Family");
        assertThat(group.getDescription()).isEqualTo("Only description changed");
        assertThat(group.getMaxMembers()).isEqualTo(8);
    }

    @Test
    void rejectsUpdateWhenGroupDoesNotExist() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());
        UpdateGroupCommand command = UpdateGroupCommand.builder()
                .groupId(GROUP_ID.toString())
                .requestedBy(OWNER_ID)
                .name("Friends")
                .build();

        assertThatThrownBy(() -> groupService.updateGroup(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Group not found: " + GROUP_ID);

        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void rejectsUpdateRequestedByNonOwner() {
        Group group = existingGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        UpdateGroupCommand command = UpdateGroupCommand.builder()
                .groupId(GROUP_ID.toString())
                .requestedBy("another-user")
                .name("Friends")
                .build();

        assertThatThrownBy(() -> groupService.updateGroup(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only the group owner can update the group");

        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void rejectsDuplicateNameDuringUpdate() {
        Group group = existingGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(groupRepository.existsByNameAndOwnerIdAndIdNot("Friends", OWNER_ID, GROUP_ID))
                .thenReturn(true);
        UpdateGroupCommand command = UpdateGroupCommand.builder()
                .groupId(GROUP_ID.toString())
                .requestedBy(OWNER_ID)
                .name("Friends")
                .build();

        assertThatThrownBy(() -> groupService.updateGroup(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Group with name 'Friends' already exists for this owner");

        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void rejectsMemberLimitLowerThanCurrentMemberCount() {
        Group group = existingGroup();
        group.setMembers(List.of(
                member(group, "user-001"),
                member(group, "user-002"),
                member(group, "user-003"),
                member(group, "user-004")));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        UpdateGroupCommand command = UpdateGroupCommand.builder()
                .groupId(GROUP_ID.toString())
                .requestedBy(OWNER_ID)
                .maxMembers(3)
                .build();

        assertThatThrownBy(() -> groupService.updateGroup(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum member limit cannot be lower than current member count: 4");

        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void deletesGroupWhenRequestedByOwnerAndReturnsEvent() {
        Group group = existingGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        DeleteGroupCommand command = DeleteGroupCommand.builder()
                .groupId(GROUP_ID.toString())
                .ownerId(OWNER_ID)
                .build();

        GroupDeletedEvent event = groupService.deleteGroup(command);

        verify(groupRepository).delete(group);
        assertThat(event.getGroupId()).isEqualTo(GROUP_ID.toString());
        assertInitializedEvent(event.getEventId(), event.getTimestamp(), event.getEventType(), "GROUP_DELETED");
    }

    @Test
    void rejectsDeleteRequestedByNonOwner() {
        Group group = existingGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        DeleteGroupCommand command = DeleteGroupCommand.builder()
                .groupId(GROUP_ID.toString())
                .ownerId("another-user")
                .build();

        assertThatThrownBy(() -> groupService.deleteGroup(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only the group owner can delete the group");

        verify(groupRepository, never()).delete(any(Group.class));
    }

    @Test
    void addsMemberWithDefaultRoleAndReturnsEvent() {
        Group group = existingGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        AddMemberCommand command = addMemberCommand(null);

        MemberAddedEvent event = groupService.addMember(command);

        verify(groupMemberRepository).existsByGroupAndUserId(group, "user-002");
        verify(groupMemberRepository).save(memberCaptor.capture());
        GroupMember member = memberCaptor.getValue();
        assertThat(member.getGroup()).isSameAs(group);
        assertThat(member.getUserId()).isEqualTo("user-002");
        assertThat(member.getUserEmail()).isEqualTo("user@example.com");
        assertThat(member.getUserName()).isEqualTo("Jane Doe");
        assertThat(member.getRole()).isEqualTo("MEMBER");
        assertThat(event.getGroupId()).isEqualTo(GROUP_ID.toString());
        assertThat(event.getUserId()).isEqualTo("user-002");
        assertThat(event.getUserEmail()).isEqualTo("user@example.com");
        assertThat(event.getUserName()).isEqualTo("Jane Doe");
        assertThat(event.getRole()).isEqualTo("MEMBER");
        assertInitializedEvent(event.getEventId(), event.getTimestamp(), event.getEventType(), "MEMBER_ADDED");
    }

    @Test
    void preservesExplicitMemberRole() {
        Group group = existingGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        MemberAddedEvent event = groupService.addMember(addMemberCommand("ADMIN"));

        verify(groupMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(event.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void normalizesMemberRole() {
        Group group = existingGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        MemberAddedEvent event = groupService.addMember(addMemberCommand(" member "));

        verify(groupMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo("MEMBER");
        assertThat(event.getRole()).isEqualTo("MEMBER");
    }

    @Test
    void rejectsUnsupportedMemberRole() {
        Group group = existingGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.addMember(addMemberCommand("OWNER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported group member role: OWNER");

        verify(groupMemberRepository, never()).save(any(GroupMember.class));
    }

    @Test
    void rejectsAddingMemberRequestedByNonOwner() {
        Group group = existingGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        AddMemberCommand command = addMemberCommand(null);
        command.setRequestedBy("another-user");

        assertThatThrownBy(() -> groupService.addMember(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only the group owner can add members to the group");

        verifyNoInteractions(groupMemberRepository);
    }

    @Test
    void rejectsMemberWhenGroupReachedCapacity() {
        Group group = existingGroup();
        group.setMaxMembers(2);
        group.setMembers(List.of(
                member(group, "user-001"),
                member(group, "user-003")));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.addMember(addMemberCommand(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Group has reached maximum member limit: 2");

        verifyNoInteractions(groupMemberRepository);
    }

    @Test
    void rejectsUserWhoIsAlreadyGroupMember() {
        Group group = existingGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupAndUserId(group, "user-002")).thenReturn(true);

        assertThatThrownBy(() -> groupService.addMember(addMemberCommand(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User is already a member of this group");

        verify(groupMemberRepository, never()).save(any(GroupMember.class));
    }

    @Test
    void rejectsAddingMemberWhenGroupDoesNotExist() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.addMember(addMemberCommand(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Group not found: " + GROUP_ID);

        verifyNoInteractions(groupMemberRepository);
    }

    private CreateGroupCommand createGroupCommand() {
        return CreateGroupCommand.builder()
                .name("Family")
                .description("Christmas draw")
                .ownerId(OWNER_ID)
                .maxMembers(8)
                .build();
    }

    private AddMemberCommand addMemberCommand(String role) {
        return AddMemberCommand.builder()
                .groupId(GROUP_ID.toString())
                .requestedBy(OWNER_ID)
                .userId("user-002")
                .userEmail("user@example.com")
                .userName("Jane Doe")
                .role(role)
                .build();
    }

    private Group existingGroup() {
        return Group.builder()
                .id(GROUP_ID)
                .name("Family")
                .description("Christmas draw")
                .ownerId(OWNER_ID)
                .maxMembers(8)
                .build();
    }

    private GroupMember member(Group group, String userId) {
        return GroupMember.builder()
                .group(group)
                .userId(userId)
                .userName("Member " + userId)
                .role("MEMBER")
                .build();
    }

    private void assertInitializedEvent(String eventId, long timestamp, String eventType, String expectedType) {
        assertThat(eventId).isNotBlank();
        assertThat(timestamp).isPositive();
        assertThat(eventType).isEqualTo(expectedType);
    }
}
