package com.secretsanta.group.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.secretsanta.group.entity.Group;

class GroupAuthorizationServiceTest {

    private final GroupAuthorizationService authorizationService = new GroupAuthorizationService();

    @Test
    void allowsOwnerToPerformOperation() {
        Group group = Group.builder().ownerId("owner-001").build();

        assertThatCode(() -> authorizationService.requireOwnerForUpdate(
                group, "owner-001"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNonOwnerWithOperationSpecificMessage() {
        Group group = Group.builder().ownerId("owner-001").build();

        assertThatThrownBy(() -> authorizationService.requireOwnerForUpdate(
                group, "another-user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only the group owner can update the group");
    }

    @Test
    void rejectsMissingRequesterWithoutThrowingNullPointerException() {
        Group group = Group.builder().ownerId("owner-001").build();

        assertThatThrownBy(() -> authorizationService.requireOwnerForDelete(
                group, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only the group owner can delete the group");
    }

    @Test
    void rejectsBlankRequester() {
        Group group = Group.builder().ownerId("owner-001").build();

        assertThatThrownBy(() -> authorizationService.requireOwnerForDraw(
                group, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only the group owner can trigger a draw");
    }

    @Test
    void usesAddingMemberSpecificError() {
        Group group = Group.builder().ownerId("owner-001").build();

        assertThatThrownBy(() -> authorizationService.requireOwnerForAddingMember(
                group, "another-user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only the group owner can add members to the group");
    }

    @Test
    void rejectsMissingGroupWithDomainError() {
        assertThatThrownBy(() -> authorizationService.requireOwnerForUpdate(
                null, "owner-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Group is required for authorization");
    }
}
