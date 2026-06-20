package com.secretsanta.group.service;

import org.springframework.stereotype.Service;

import com.secretsanta.group.entity.Group;

@Service
public class GroupAuthorizationService {

    private static final String UPDATE_DENIED = "Only the group owner can update the group";
    private static final String DELETE_DENIED = "Only the group owner can delete the group";
    private static final String ADD_MEMBER_DENIED = "Only the group owner can add members to the group";
    private static final String DRAW_DENIED = "Only the group owner can trigger a draw";

    public void requireOwnerForUpdate(Group group, String requestedBy) {
        requireOwner(group, requestedBy, UPDATE_DENIED);
    }

    public void requireOwnerForDelete(Group group, String requestedBy) {
        requireOwner(group, requestedBy, DELETE_DENIED);
    }

    public void requireOwnerForAddingMember(Group group, String requestedBy) {
        requireOwner(group, requestedBy, ADD_MEMBER_DENIED);
    }

    public void requireOwnerForDraw(Group group, String requestedBy) {
        requireOwner(group, requestedBy, DRAW_DENIED);
    }

    private static void requireOwner(Group group, String requestedBy, String deniedMessage) {
        if (group == null) {
            throw new IllegalArgumentException("Group is required for authorization");
        }

        if (requestedBy == null
                || requestedBy.isBlank()
                || !requestedBy.equals(group.getOwnerId())) {
            throw new IllegalArgumentException(deniedMessage);
        }
    }
}
