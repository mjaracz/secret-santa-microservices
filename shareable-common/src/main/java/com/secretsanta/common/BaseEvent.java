package com.secretsanta.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.secretsanta.common.group.events.DrawCompletedEvent;
import com.secretsanta.common.group.events.GroupCreatedEvent;
import com.secretsanta.common.group.events.GroupDeletedEvent;
import com.secretsanta.common.group.events.GroupUpdatedEvent;
import com.secretsanta.common.group.events.MemberAddedEvent;
import com.secretsanta.common.user.events.UserCreatedEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({
		@JsonSubTypes.Type(value = UserCreatedEvent.class, name = "USER_CREATED"),
		@JsonSubTypes.Type(value = GroupCreatedEvent.class, name = "GROUP_CREATED"),
		@JsonSubTypes.Type(value = GroupUpdatedEvent.class, name = "GROUP_UPDATED"),
		@JsonSubTypes.Type(value = GroupDeletedEvent.class, name = "GROUP_DELETED"),
		@JsonSubTypes.Type(value = MemberAddedEvent.class, name = "MEMBER_ADDED"),
		@JsonSubTypes.Type(value = DrawCompletedEvent.class, name = "DRAW_COMPLETED"),
		@JsonSubTypes.Type(value = CommandFailedEvent.class, name = "COMMAND_FAILED")
})
public class BaseEvent {

	@JsonProperty("eventId")
	private String eventId;

	@JsonProperty("timestamp")
	private long timestamp;

	@JsonProperty("eventType")
	private String eventType;

	@JsonProperty("correlationId")
	private String correlationId;

	public void initDefaults(String type) {
		if (this.eventId == null) {
			this.eventId = UUID.randomUUID().toString();
		}
		if (this.timestamp == 0) {
			this.timestamp = Instant.now().toEpochMilli();
		}
		this.eventType = type;
	}
}
