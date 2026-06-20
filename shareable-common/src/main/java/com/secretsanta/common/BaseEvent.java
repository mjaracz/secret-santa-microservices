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
import com.secretsanta.common.user.events.EmailVerificationRequestedEvent;
import com.secretsanta.common.user.events.EmailVerificationResentEvent;
import com.secretsanta.common.user.events.EmailVerifiedEvent;
import com.secretsanta.common.user.events.SessionRefreshedEvent;
import com.secretsanta.common.user.events.SessionRevokedEvent;
import com.secretsanta.common.user.events.UserAuthenticatedEvent;
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
@JsonTypeInfo(
		use = JsonTypeInfo.Id.NAME,
		include = JsonTypeInfo.As.PROPERTY,
		property = "eventType",
		visible = true
)
@JsonSubTypes({
		@JsonSubTypes.Type(value = UserCreatedEvent.class, name = "USER_CREATED"),
		@JsonSubTypes.Type(value = UserAuthenticatedEvent.class, name = "USER_AUTHENTICATED"),
		@JsonSubTypes.Type(value = SessionRefreshedEvent.class, name = "SESSION_REFRESHED"),
		@JsonSubTypes.Type(value = SessionRevokedEvent.class, name = "SESSION_REVOKED"),
		@JsonSubTypes.Type(value = EmailVerifiedEvent.class, name = "EMAIL_VERIFIED"),
		@JsonSubTypes.Type(value = EmailVerificationResentEvent.class, name = "EMAIL_VERIFICATION_RESENT"),
		@JsonSubTypes.Type(value = EmailVerificationRequestedEvent.class, name = "EMAIL_VERIFICATION_REQUESTED"),
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
