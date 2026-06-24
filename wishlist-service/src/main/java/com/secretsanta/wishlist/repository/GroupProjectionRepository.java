package com.secretsanta.wishlist.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secretsanta.wishlist.entity.GroupProjection;

public interface GroupProjectionRepository extends JpaRepository<GroupProjection, UUID> {
}
