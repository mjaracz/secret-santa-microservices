package com.secretsanta.group.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.secretsanta.group.entity.Group;
import com.secretsanta.group.entity.GroupMember;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    boolean existsByGroupAndUserId(Group group, String userId);
}
