package com.secretsanta.common.group.dto;

import java.time.Instant;

public record GroupSummaryDto(
	String groupId,
	String name,
	String description,
	String ownerId,
	int maxMembers,
	int memberCount,
	boolean drawn,
	Instant createdAt
) {
}
