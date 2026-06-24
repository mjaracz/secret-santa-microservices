package com.secretsanta.wishlist.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secretsanta.common.group.dto.DrawAssignmentDto;
import com.secretsanta.common.group.events.DrawCompletedEvent;
import com.secretsanta.common.group.events.GroupCreatedEvent;
import com.secretsanta.common.group.events.GroupDeletedEvent;
import com.secretsanta.common.group.events.MemberAddedEvent;
import com.secretsanta.wishlist.entity.DrawAssignmentProjection;
import com.secretsanta.wishlist.entity.GroupParticipantProjection;
import com.secretsanta.wishlist.entity.GroupProjection;
import com.secretsanta.wishlist.repository.DrawAssignmentProjectionRepository;
import com.secretsanta.wishlist.repository.GroupParticipantProjectionRepository;
import com.secretsanta.wishlist.repository.GroupProjectionRepository;
import com.secretsanta.wishlist.repository.WishlistItemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroupEventProjectionService {

    private static final String OWNER_DISPLAY_NAME = "Owner";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String MEMBER_ROLE = "MEMBER";

    private final GroupProjectionRepository groupRepository;
    private final GroupParticipantProjectionRepository participantRepository;
    private final DrawAssignmentProjectionRepository assignmentRepository;
    private final WishlistItemRepository wishlistItemRepository;

    @Transactional
    public void apply(GroupCreatedEvent event) {
        UUID groupId = UUID.fromString(event.getGroupId());
        GroupProjection group = groupRepository.findById(groupId)
                .orElseGet(() -> GroupProjection.builder()
                        .groupId(groupId)
                        .build());

        group.setGroupName(event.getName());
        group.setOwnerUserId(event.getOwnerId());
        groupRepository.save(group);

        upsertParticipant(groupId, event.getOwnerId(), OWNER_DISPLAY_NAME, null, ADMIN_ROLE);
    }

    @Transactional
    public void apply(MemberAddedEvent event) {
        UUID groupId = UUID.fromString(event.getGroupId());
        ensureGroupExists(groupId);
        upsertParticipant(
                groupId,
                event.getUserId(),
                event.getUserName(),
                event.getUserEmail(),
                event.getRole()
        );
    }

    @Transactional
    public void apply(DrawCompletedEvent event) {
        UUID groupId = UUID.fromString(event.getGroupId());
        GroupProjection group = ensureGroupExists(groupId);
        group.setDrawn(true);
        groupRepository.save(group);

        for (DrawAssignmentDto assignment : assignmentsOf(event)) {
            upsertParticipant(groupId, assignment.getGiverId(), assignment.getGiverName(), null, MEMBER_ROLE);
            upsertParticipant(groupId, assignment.getReceiverId(), assignment.getReceiverName(), null, MEMBER_ROLE);

            DrawAssignmentProjection projection = assignmentRepository
                    .findByGroupIdAndGiverId(groupId, assignment.getGiverId())
                    .orElseGet(() -> DrawAssignmentProjection.builder()
                            .groupId(groupId)
                            .giverId(assignment.getGiverId())
                            .build());

            projection.setGiverName(assignment.getGiverName());
            projection.setReceiverId(assignment.getReceiverId());
            projection.setReceiverName(assignment.getReceiverName());
            assignmentRepository.save(projection);
        }
    }

    @Transactional
    public void apply(GroupDeletedEvent event) {
        UUID groupId = UUID.fromString(event.getGroupId());
        wishlistItemRepository.deleteByGroupId(groupId);
        assignmentRepository.deleteByGroupId(groupId);
        participantRepository.deleteByGroupId(groupId);
        if (groupRepository.existsById(groupId)) {
            groupRepository.deleteById(groupId);
        }
    }

    private GroupProjection ensureGroupExists(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseGet(() -> groupRepository.save(GroupProjection.builder()
                        .groupId(groupId)
                        .build()));
    }

    private void upsertParticipant(
            UUID groupId,
            String userId,
            String userName,
            String userEmail,
            String role
    ) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        GroupParticipantProjection participant = participantRepository
                .findByGroupIdAndUserId(groupId, userId)
                .orElseGet(() -> GroupParticipantProjection.builder()
                        .groupId(groupId)
                        .userId(userId)
                        .build());

        if (userName != null && !userName.isBlank()) {
            participant.setUserName(userName);
        }
        if (userEmail != null && !userEmail.isBlank()) {
            participant.setUserEmail(userEmail);
        }
        if (role != null && !role.isBlank()) {
            participant.setRole(role);
        }

        participantRepository.save(participant);
    }

    private static List<DrawAssignmentDto> assignmentsOf(DrawCompletedEvent event) {
        if (event.getAssignments() == null) {
            return List.of();
        }
        return event.getAssignments();
    }
}
