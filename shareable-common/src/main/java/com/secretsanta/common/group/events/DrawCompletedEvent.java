package com.secretsanta.common.group.events;

import com.secretsanta.common.BaseEvent;
import com.secretsanta.common.group.dto.DrawAssignmentDto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DrawCompletedEvent extends BaseEvent {

	private String groupId;
	private List<DrawAssignmentDto> assignments;
}
