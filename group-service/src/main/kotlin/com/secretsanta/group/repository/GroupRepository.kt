package com.secretsanta.group.repository

import com.secretsanta.group.entity.Group
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GroupRepository : JpaRepository<Group, UUID> {
    fun findByOwnerId(ownerId: String): List<Group>

    fun existsByNameAndOwnerId(name: String, ownerId: String): Boolean

    @EntityGraph(attributePaths = ["members"])
    fun findDistinctByMembers_UserIdOrderByCreatedAtDesc(userId: String): List<Group>
}
