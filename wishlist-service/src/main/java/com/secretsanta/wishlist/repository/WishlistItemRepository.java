package com.secretsanta.wishlist.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secretsanta.wishlist.entity.WishlistItem;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

    List<WishlistItem> findByGroupIdAndOwnerUserIdOrderByCreatedAtAsc(UUID groupId, String ownerUserId);

    Optional<WishlistItem> findByIdAndGroupIdAndOwnerUserId(UUID id, UUID groupId, String ownerUserId);

    void deleteByGroupId(UUID groupId);
}
