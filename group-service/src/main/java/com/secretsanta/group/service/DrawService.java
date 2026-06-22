package com.secretsanta.group.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.secretsanta.common.group.commands.DrawNamesCommand;
import com.secretsanta.common.group.dto.DrawAssignmentDto;
import com.secretsanta.common.group.events.DrawCompletedEvent;
import com.secretsanta.group.entity.DrawAssignment;
import com.secretsanta.group.entity.Group;
import com.secretsanta.group.entity.GroupMember;
import com.secretsanta.group.repository.DrawAssignmentRepository;
import com.secretsanta.group.repository.GroupRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DrawService {

    private final GroupRepository groupRepository;
    private final DrawAssignmentRepository drawAssignmentRepository;
    private final GroupAuthorizationService authorizationService;

    @Transactional
    public DrawCompletedEvent drawNames(DrawNamesCommand command) {
        return drawNames(command, new SecureRandom());
    }

    @Transactional
    public DrawCompletedEvent drawNames(DrawNamesCommand command, Random random) {
        if (command == null) {
            throw new IllegalArgumentException("Command is required");
        }
        if (random == null) {
            throw new IllegalArgumentException("Random generator is required");
        }

        UUID groupId = UUID.fromString(command.getGroupId());
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + command.getGroupId()));

        authorizationService.requireOwner(group, command);
        List<GroupMember> shuffled = getGroupMembers(group);
        Collections.shuffle(shuffled, random);

        List<DrawAssignment> assignments = new ArrayList<>();
        List<DrawAssignmentDto> assignmentDtos = new ArrayList<>();

        for (int i = 0; i < shuffled.size(); i++) {
            GroupMember giver = shuffled.get(i);
            GroupMember receiver = shuffled.get((i + 1) % shuffled.size());

            DrawAssignment assignment = DrawAssignment.builder()
                    .group(group)
                    .giverId(giver.getUserId())
                    .giverName(giver.getUserName())
                    .receiverId(receiver.getUserId())
                    .receiverName(receiver.getUserName())
                    .build();
            assignments.add(assignment);

            assignmentDtos.add(DrawAssignmentDto.builder()
                    .giverId(giver.getUserId())
                    .giverName(giver.getUserName())
                    .receiverId(receiver.getUserId())
                    .receiverName(receiver.getUserName())
                    .build());
        }

        drawAssignmentRepository.saveAll(assignments);
        group.setDrawn(true);
        groupRepository.save(group);
        log.info("Draw completed for group {} with {} assignments", groupId, assignments.size());

        DrawCompletedEvent event = DrawCompletedEvent.builder()
                .groupId(command.getGroupId())
                .assignments(assignmentDtos)
                .build();
        event.initDefaults("DRAW_COMPLETED");

        return event;
    }

    private static List<GroupMember> getGroupMembers(Group group) {
        if (group.isDrawn()) {
            throw new IllegalArgumentException("Draw has already been performed for this group");
        }

        List<GroupMember> members = group.getMembers();
        if (members.size() < 3) {
            throw new IllegalArgumentException("Group must have at least 3 members to perform a draw");
        }

	    return new ArrayList<>(members);
    }
}
