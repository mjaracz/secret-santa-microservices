package com.secretsanta.common.group.events;

import com.secretsanta.common.BaseEvent;

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
public class GroupCreatedEvent extends BaseEvent {

	private String groupId;
	private String name;
	private String description;
	private String ownerId;
}
