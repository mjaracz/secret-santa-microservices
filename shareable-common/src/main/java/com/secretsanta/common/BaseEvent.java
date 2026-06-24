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
import com.secretsanta.common.wishlist.events.GiftPurchaseUpdatedEvent;
import com.secretsanta.common.wishlist.events.ReceiverWishlistFetchedEvent;
import com.secretsanta.common.wishlist.events.WishlistAssignmentFetchedEvent;
import com.secretsanta.common.wishlist.events.WishlistFetchedEvent;
import com.secretsanta.common.wishlist.events.WishlistItemAddedEvent;
import com.secretsanta.common.wishlist.events.WishlistItemDeletedEvent;
import com.secretsanta.common.wishlist.events.WishlistItemUpdatedEvent;
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
		@JsonSubTypes.Type(value = CommandFailedEvent.class, name = "COMMAND_FAILED"),
		@JsonSubTypes.Type(value = WishlistItemAddedEvent.class, name = "WISHLIST_ITEM_ADDED"),
		@JsonSubTypes.Type(value = WishlistFetchedEvent.class, name = "WISHLIST_FETCHED"),
		@JsonSubTypes.Type(value = WishlistItemUpdatedEvent.class, name = "WISHLIST_ITEM_UPDATED"),
		@JsonSubTypes.Type(value = WishlistItemDeletedEvent.class, name = "WISHLIST_ITEM_DELETED"),
		@JsonSubTypes.Type(value = WishlistAssignmentFetchedEvent.class, name = "WISHLIST_ASSIGNMENT_FETCHED"),
		@JsonSubTypes.Type(value = GiftPurchaseUpdatedEvent.class, name = "GIFT_PURCHASE_UPDATED"),
		@JsonSubTypes.Type(value = ReceiverWishlistFetchedEvent.class, name = "RECEIVER_WISHLIST_FETCHED")
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
