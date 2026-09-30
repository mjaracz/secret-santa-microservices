package com.secretsanta.group.service;

import com.secretsanta.common.group.commands.GetMyGroupsCommand;
import com.secretsanta.common.group.dto.GroupSummaryDto;
import com.secretsanta.common.group.events.MyGroupsFetchedEvent;
import com.secretsanta.group.entity.Group;
import com.secretsanta.group.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupQueryService {

	private final GroupRepository groupRepository;

	@Transactional(readOnly = true)
	public MyGroupsFetchedEvent getMyGroups(GetMyGroupsCommand command) {
		boolean isInvalidActor = command == null || command.getRequestedBy() == null || command.getRequestedBy().isBlank();

		if (isInvalidActor) {
			throw new IllegalArgumentException("Requester ID is required");
		}

		List<GroupSummaryDto> groups = groupRepository
			.findDistinctByMembers_UserIdOrderByCreatedAtDesc(
				command.getRequestedBy().trim()
			)
			.stream()
			.map(GroupQueryService::toSummary)
			.toList();

		MyGroupsFetchedEvent event = MyGroupsFetchedEvent.builder()
			.groups(groups)
			.build();

		event.initDefaults("MY_GROUPS_FETCHED");

		return event;
	}

	private static GroupSummaryDto toSummary(Group group) {
		return new GroupSummaryDto(
			group.getId().toString(),
			group.getName(),
			group.getDescription(),
			group.getOwnerId(),
			group.getMaxMembers(),
			group.getMembers().size(),
			group.isDrawn(),
			group.getCreatedAt()
		);
	}
}
