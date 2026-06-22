package com.secretsanta.group.service;

import com.secretsanta.common.group.commands.UpdateGroupCommand;
import com.secretsanta.common.user.UserRole;
import com.secretsanta.group.entity.Group;
import com.secretsanta.group.entity.GroupMember;
import com.secretsanta.group.exception.GroupCommandException;
import com.secretsanta.group.repository.GroupMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupAuthorizationServiceTest {

    @Mock
    private GroupMemberRepository groupMemberRepository;

    private GroupAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new GroupAuthorizationService(
                groupMemberRepository
        );
    }

    @Test
    void allowsGroupOwner() {
        Group group = Group.builder().ownerId("owner-001").build();

        assertThatCode(() -> authorizationService.requireOwner(
                group,
                command("owner-001", UserRole.USER)
        )).doesNotThrowAnyException();
    }

    @Test
    void allowsGlobalAdminForOwnerOperation() {
        Group group = Group.builder().ownerId("owner-001").build();

        assertThatCode(() -> authorizationService.requireOwner(
                group,
                command("admin-001", UserRole.ADMIN)
        )).doesNotThrowAnyException();
    }

    @Test
    void allowsGroupAdminForAdministrativeOperation() {
        Group group = Group.builder().ownerId("owner-001").build();
        GroupMember member = GroupMember.builder().role("ADMIN").build();
        when(groupMemberRepository.findByGroupAndUserId(group, "member-001"))
                .thenReturn(Optional.of(member));

        assertThatCode(() -> authorizationService.requireGroupAdmin(
                group,
                command("member-001", UserRole.USER)
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnprivilegedActorWithTypedError() {
        Group group = Group.builder().ownerId("owner-001").build();
        when(groupMemberRepository.findByGroupAndUserId(group, "member-001"))
                .thenReturn(Optional.empty());

        assertForbidden(() -> authorizationService.requireGroupAdmin(
                group,
                command("member-001", UserRole.USER)
        ));
    }

    @Test
    void rejectsCommandWithoutAuthenticatedActor() {
        assertForbidden(() -> authorizationService.requireActor(
                UpdateGroupCommand.builder().build()
        ));
    }

    private UpdateGroupCommand command(String actorId, UserRole role) {
        return UpdateGroupCommand.builder()
                .actorId(actorId)
                .actorRoles(Set.of(role))
                .build();
    }

    private void assertForbidden(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(
                        GroupCommandException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo("AUTH_FORBIDDEN");
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "You do not have permission to perform this operation"
                                    );
                        }
                );
    }
}
