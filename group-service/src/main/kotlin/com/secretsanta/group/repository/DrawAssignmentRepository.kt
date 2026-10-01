package com.secretsanta.group.repository

import com.secretsanta.group.entity.DrawAssignment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DrawAssignmentRepository : JpaRepository<DrawAssignment, UUID>
