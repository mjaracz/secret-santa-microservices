package com.secretsanta.group.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.secretsanta.group.entity.DrawAssignment;

@Repository
public interface DrawAssignmentRepository extends JpaRepository<DrawAssignment, UUID> {
}
