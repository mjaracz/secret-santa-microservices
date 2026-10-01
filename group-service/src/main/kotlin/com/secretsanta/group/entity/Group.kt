package com.secretsanta.group.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.Hibernate
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "groups")
class Group {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @field:Column(nullable = false, length = 255)
    var name: String = ""

    @field:Column(length = 1000)
    var description: String? = null

    @field:Column(name = "owner_id", nullable = false)
    var ownerId: String = ""

    @field:Column(name = "max_members", nullable = false)
    var maxMembers: Int = 0

    @field:Column(nullable = false)
    var drawn: Boolean = false

    @field:OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true)
    var members: MutableList<GroupMember> = mutableListOf()

    @field:OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true)
    var drawAssignments: MutableList<DrawAssignment> = mutableListOf()

    @field:CreationTimestamp
    @field:Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null

    @field:UpdateTimestamp
    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        return id != null && id == (other as Group).id
    }

    override fun hashCode(): Int = Hibernate.getClass(this).hashCode()
}
