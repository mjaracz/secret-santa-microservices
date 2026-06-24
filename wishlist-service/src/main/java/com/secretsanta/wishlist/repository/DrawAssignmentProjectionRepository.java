package com.secretsanta.wishlist.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secretsanta.wishlist.entity.DrawAssignmentProjection;

public interface DrawAssignmentProjectionRepository extends JpaRepository<DrawAssignmentProjection, UUID> {

    Optional<DrawAssignmentProjection> findByGroupIdAndGiverId(UUID groupId, String giverId);

    void deleteByGroupId(UUID groupId);
}
