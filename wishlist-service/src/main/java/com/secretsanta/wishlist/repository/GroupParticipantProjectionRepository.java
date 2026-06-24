package com.secretsanta.wishlist.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secretsanta.wishlist.entity.GroupParticipantProjection;

public interface GroupParticipantProjectionRepository extends JpaRepository<GroupParticipantProjection, UUID> {

    boolean existsByGroupIdAndUserId(UUID groupId, String userId);

    Optional<GroupParticipantProjection> findByGroupIdAndUserId(UUID groupId, String userId);

    void deleteByGroupId(UUID groupId);
}
