package com.secretsanta.group.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.secretsanta.group.entity.Group;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {

    List<Group> findByOwnerId(String ownerId);

    boolean existsByNameAndOwnerId(String name, String ownerId);

    boolean existsByNameAndOwnerIdAndIdNot(String name, String ownerId, UUID id);
}
