package com.secretsanta.group.repository

import com.secretsanta.group.entity.Group
import com.secretsanta.group.entity.GroupMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GroupMemberRepository : JpaRepository<GroupMember, UUID> {
    fun existsByGroupAndUserId(group: Group, userId: String): Boolean
}
