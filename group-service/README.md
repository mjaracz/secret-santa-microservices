# Group Service (Pure Worker)

> **Part of Secret Santa Microservices Platform**
>
> *Note: In production, this would be a separate repository.*

---

## 🏗️ Architectural Role

**Type:** `Pure Event-Driven Worker`

**Responsibility:**  
Domain service managing Secret Santa groups, memberships, group queries, and draw execution. It persists group data to PostgreSQL and communicates through Kafka; HTTP endpoints belong to API Gateway.

**Key Characteristics:**
- ✅ **Pure worker** - Kafka-only communication
- ✅ **Domain logic** - Group management + draw algorithm
- ✅ **Database per service** - Isolated PostgreSQL
- ✅ **Complex business rules** - Member validation, draw logic
- ❌ **No REST endpoints** - Event-driven only
- ❌ **No HTTP exposure** - Internal service

**Architecture Pattern:**  
Event-Driven Microservices + Domain-Driven Design

**Communication Flow:**
```
CreateGroupCommand (from Gateway)
    ↓
Group Service @KafkaListener
    ↓
Validate group-name uniqueness
    ↓
Save to PostgreSQL
    ↓
Add owner as ADMIN member
    ↓
Publish correlated GroupCreatedEvent
    ↓
API Gateway returns the command result
```

---

## 📦 Dependencies

### Spring Boot Initializer Selection

**Project Metadata:**
- Spring Boot: `4.0.2`
- Kotlin: `2.3.21` (JVM target: Java `25`)
- Group: `com.secretsanta`
- Artifact: `group-service`
- Package: `com.secretsanta.group`

**Dependencies:**

| Category | Dependency | Identifier | Purpose |
|----------|-----------|------------|---------|
| **Messaging** | Spring for Apache Kafka | `spring-boot-starter-kafka` | Event consumer/producer |
| **SQL** | Spring Data JPA | `data-jpa` | ORM for PostgreSQL |
| **SQL** | PostgreSQL Driver | `postgresql` | Database driver |
| **Language** | Kotlin | `kotlin-stdlib`, `kotlin-reflect` | Worker implementation and Spring integration |
| **Compiler plugins** | Kotlin Spring/JPA | `kotlin-maven-allopen`, `kotlin-maven-noarg` | Open Spring/JPA classes and provide JPA constructors |

### Maven Dependencies
```xml
<dependencies>
    <!-- Kafka -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-kafka</artifactId>
    </dependency>

    <!-- JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <dependency>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-stdlib</artifactId>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-reflect</artifactId>
    </dependency>
</dependencies>
```

The Kotlin Maven plugin and its Spring/JPA compiler plugins are configured in
`group-service/pom.xml`.

**Critical Notes:**
- ❌ **No `spring-boot-starter-web`** - Pure worker
- ✅ **Spring JPA and Kafka** - Same infrastructure patterns as User Service
- ✅ **Kotlin 2.3.21** - The `group-service` implementation and its unit tests are Kotlin; shared commands and events remain Java classes in `shareable-common`.

### Kotlin implementation notes

The conversion preserves the worker boundary: `GroupCommandListener` consumes Kafka commands, delegates to application services, and publishes correlated events. REST remains in API Gateway. Spring injects the listener and services through their primary constructors, so the Kotlin code does not need Lombok's `@RequiredArgsConstructor`.

The Kotlin Maven plugin applies the `spring` and `jpa` compiler plugins. The Spring plugin opens Spring-managed classes for proxies; the JPA plugin supplies the constructor/open-class behavior Hibernate needs. Entity annotations use Kotlin use-site targets such as `@field:Id` and `@field:ManyToOne`, which place the annotation on the Java field that JPA inspects. Entities are regular classes rather than `data class` because generated `equals`, `hashCode`, and `copy` methods do not fit Hibernate proxy and persistence identity behavior.

Kotlin services call the Java shared-library API directly. Java Lombok builders remain available to Kotlin, while Kotlin uses nullability, constructor injection, `apply`, collection operations, and function references such as `::onCreateGroup`. `DrawService.drawNames` has a default `Random()` argument for production and accepts a seeded `Random` in deterministic unit tests.

To build and run the module locally:

```bash
cd shareable-common && mvn clean install
cd ../shareable-infrastructure && mvn clean install
cd ../group-service && mvn clean test
```

`group-service` is still started as a Kafka worker; invoke group REST operations through API Gateway (`api-gateway`), not through this module.

---

## 🎯 Domain Responsibility

### Group Management
- **Group Creation** - Create Secret Santa groups
- **Configuration** - Group name, description, and maximum member count
- **Membership** - Add participants; the creator is added as an `ADMIN` member
- **Group queries** - Return a summary of groups for a requested member
- **Draw state** - Record whether the one allowed draw has completed

### Draw Execution Logic
- **Algorithm** - Assign Secret Santa pairings
- **Validation** - No self-assignments, min 3 members
- **Randomization** - `Collections.shuffle` with `java.util.Random`; tests can supply a seeded instance
- **No re-draw** - A group can be drawn only once

### Business Rules
- Minimum 3 members for valid draw
- Creator becomes an `ADMIN` member
- Only the owner can trigger a draw or delete the group
- User can join multiple groups simultaneously
- Draw results are returned in `DrawCompletedEvent`

---

## 📨 Event Production

Publishes to `group.events`:

| Event | Trigger | Consumed By |
|-------|---------|-------------|
| `GroupCreatedEvent` | Group created | API Gateway (request-reply response) |
| `GroupUpdatedEvent` | Group updated | API Gateway (request-reply response) |
| `GroupDeletedEvent` | Group deleted | API Gateway (request-reply response) |
| `MemberAddedEvent` | Member added | API Gateway (request-reply response) |
| `DrawCompletedEvent` | Draw executed | API Gateway (request-reply response) |
| `MyGroupsFetchedEvent` | `GetMyGroupsCommand` processed | API Gateway (request-reply response) |

---

## 📡 Event Consumption

| Topic | Event | Action |
|-------|-------|--------|
| `group.commands` | `CreateGroupCommand` | Create group → Publish `GroupCreatedEvent` |
| `group.commands` | `UpdateGroupCommand` | Update group → Publish `GroupUpdatedEvent` |
| `group.commands` | `DeleteGroupCommand` | Delete group → Publish `GroupDeletedEvent` |
| `group.commands` | `AddMemberCommand` | Add user → Publish `MemberAddedEvent` |
| `group.commands` | `DrawNamesCommand` | Run draw algorithm → Publish `DrawCompletedEvent` |
| `group.commands` | `GetMyGroupsCommand` | Query membership summaries → Publish correlated `MyGroupsFetchedEvent` |

### Query: groups for the authenticated user

The REST endpoint is `GET /api/groups/me` in API Gateway. It requires a bearer
JWT; the Gateway reads the user ID from the verified token subject and sends a
`GetMyGroupsCommand` with that ID in `requestedBy` to `group.commands`. The
command has `commandId`, `timestamp` and `commandType` fields from `BaseCommand`;
the actor ID is carried by `requestedBy`, not by the base class.

`GroupCommandListener` delegates this command to `GroupQueryService`. The
service validates the requester, runs the repository query in a read-only
transaction, and maps each entity to `GroupSummaryDto`. The repository method
`findDistinctByMembers_UserIdOrderByCreatedAtDesc` selects distinct groups for
the member, newest first. Its `@EntityGraph(attributePaths = "members")` loads
the member collection used to calculate `memberCount` while the transaction
is open. The service returns `MyGroupsFetchedEvent`; the listener sets its
`correlationId` to the command ID and publishes it to `group.events`. The
Gateway matches that event to the waiting HTTP request.

Each summary contains `groupId`, `name`, `description`, `ownerId`, `maxMembers`,
`memberCount`, `drawn` and `createdAt`. The Gateway wraps the event in its
standard `CommandResponse` (`success`, `commandId`, `data`).

---

## 💾 Database Schema (PostgreSQL)

**Database:** `group_db` (Port: `5433`)
```sql
-- Groups
CREATE TABLE groups (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    owner_id VARCHAR(255) NOT NULL,
    max_members INTEGER NOT NULL,
    drawn BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Memberships
CREATE TABLE group_members (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id),
    user_id VARCHAR(255) NOT NULL,
    user_email VARCHAR(255),
    user_name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Draw Assignments
CREATE TABLE draw_assignments (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id),
    giver_id VARCHAR(255) NOT NULL,
    giver_name VARCHAR(255) NOT NULL,
    receiver_id VARCHAR(255) NOT NULL,
    receiver_name VARCHAR(255) NOT NULL,
    drawn_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

---

## 🎲 Draw Algorithm
```kotlin
val shuffled = group.members.toMutableList()
Collections.shuffle(shuffled, random)

val assignments = shuffled.indices.map { index ->
    val giver = shuffled[index]
    val receiver = shuffled[(index + 1) % shuffled.size]

    DrawAssignment().apply {
        this.group = group
        giverId = giver.userId
        giverName = giver.userName
        receiverId = receiver.userId
        receiverName = receiver.userName
    }
}
```

---

## 🚀 Running Locally
```bash
# 1. Start infra
docker-compose up -d postgres-group kafka

# 2. Run service
cd group-service
mvn spring-boot:run

# 3. Check DB
docker exec -it postgres-group psql -U group_admin -d group_db
SELECT * FROM groups;
```

---

## ⚙️ Configuration
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/group_db
    username: group_admin
    password: group_pass

  kafka:
    bootstrap-servers: localhost:9092
```

---

## 📊 Technology Stack

- Spring Boot 4.0.2 + Kotlin 2.3.21 (Java 25 JVM target)
- PostgreSQL 15 + Spring Data JPA
- Apache Kafka
- Java `shareable-common` commands, events, and DTOs called from Kotlin

---

**Author:** Michał (mjaracz)   
**Role:** Pure Worker (Group Management + Draw Algorithm)
