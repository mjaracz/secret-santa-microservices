package com.secretsanta.common.group.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawAssignmentDto {

	private String giverId;
	private String giverName;
	private String receiverId;
	private String receiverName;
}
