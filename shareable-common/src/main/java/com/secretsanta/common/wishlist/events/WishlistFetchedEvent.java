package com.secretsanta.common.wishlist.events;

import com.secretsanta.common.BaseEvent;
import com.secretsanta.common.wishlist.dto.WishlistItemDto;

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
public class WishlistFetchedEvent extends BaseEvent {

    private String groupId;
    private String ownerUserId;
    private List<WishlistItemDto> items;
}
