package com.secretsanta.common.wishlist.events;

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
public class WishlistItemDeletedEvent extends BaseEvent {

    private String groupId;
    private String ownerUserId;
    private String itemId;
}
