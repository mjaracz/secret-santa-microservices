# Group Service (Pure Worker)

> **Part of Secret Santa Microservices Platform**
>
> *Note: In production, this would be a separate repository.*

---

## 🏗️ Architectural Role

**Type:** `Pure Event-Driven Worker`

**Responsibility:**  
Domain service managing Secret Santa groups, memberships, and draw execution. Handles complete group lifecycle from creation through member management to Secret Santa assignment algorithm.

**Key Characteristics:**
- ✅ **Pure worker** - Kafka-only communication
- ✅ **Domain logic** - Group management + draw algorithm
- ✅ **Database per service** - Isolated PostgreSQL
- ✅ **Complex business rules** - Member validation, draw logic
- ❌ **No REST endpoints** - Event-driven only
- ❌ **No HTTP exposure** - Internal service

**Architecture Pattern:**  
Event-Driven Microservices + Domain-Driven Design + Saga Pattern (for draw)

**Communication Flow:**
```
CreateGroupCommand (from Gateway)
    ↓
Group Service @KafkaListener
    ↓
Validate (min members, creator exists)
    ↓
Save to PostgreSQL
    ↓
Publish GroupCreatedEvent
    ↓
Other services react (Wishlist, Notification)
```

---

## 📦 Dependencies

### Spring Boot Initializer Selection

**Project Metadata:**
- Spring Boot: `4.0.2`
- Java: `25`
- Group: `com.secretsanta`
- Artifact: `group-service`
- Package: `com.secretsanta.group`

**Dependencies:**

| Category | Dependency | Identifier | Purpose |
|----------|-----------|------------|---------|
| **Messaging** | Spring for Apache Kafka | `kafka` | Event consumer/producer |
| **SQL** | Spring Data JPA | `data-jpa` | ORM for PostgreSQL |
| **SQL** | PostgreSQL Driver | `postgresql` | Database driver |
| **Developer Tools** | Lombok | `lombok` | Reduce boilerplate |

### Maven Dependencies
```xml
<dependencies>
    <!-- Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
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

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

**Critical Notes:**
- ❌ **No `spring-boot-starter-web`** - Pure worker
- ✅ **Same stack as User Service** - Consistency

---

## 🎯 Domain Responsibility

### Group Management
- **Group Creation** - Create Secret Santa groups
- **Configuration** - Budget limits, exchange dates, rules
- **Membership** - Add/remove participants
- **Status Lifecycle** - ACTIVE → DRAWN → COMPLETED → ARCHIVED

### Draw Execution Logic
- **Algorithm** - Assign Secret Santa pairings
- **Validation** - No self-assignments, min 3 members
- **Randomization** - Cryptographically secure random
- **Exclusions** - Prevent specific pairings (e.g., spouses)
- **Re-draw** - Allow organizers to regenerate assignments

### Business Rules
- Minimum 3 members for valid draw
- Creator automatically becomes ORGANIZER
- User can join multiple groups simultaneously
- Draw results encrypted until revealed

---

## 📨 Event Production

Publishes to `group.events`:

| Event | Trigger | Consumed By |
|-------|---------|-------------|
| `GroupCreatedEvent` | Group created | Wishlist Service, Notification Service |
| `UserAddedToGroupEvent` | Member joined | Notification Service (invitation email) |
| `UserRemovedFromGroupEvent` | Member left | Wishlist Service (delete wishlist) |
| `DrawCompletedEvent` | Draw executed | Notification Service (send assignments), Wishlist Service (unlock) |

---

## 📡 Event Consumption

| Topic | Event | Action |
|-------|-------|--------|
| `group.commands` | CreateGroupCommand | Create group → Publish GroupCreatedEvent |
| `group.commands` | AddMemberCommand | Add user → Publish UserAddedToGroupEvent |
| `group.commands` | ExecuteDrawCommand | Run algorithm → Publish DrawCompletedEvent |
| `user.events` | UserDeletedEvent | Remove user from all groups |

---

## 💾 Database Schema (PostgreSQL)

**Database:** `group_db` (Port: `5433`)
```sql
-- Groups
CREATE TABLE groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    creator_user_id UUID NOT NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    budget_min DECIMAL(10,2),
    budget_max DECIMAL(10,2),
    exchange_date DATE,
    draw_executed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Memberships
CREATE TABLE group_members (
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role_in_group VARCHAR(50) DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (group_id, user_id)
);

-- Draw Assignments
CREATE TABLE draw_assignments (
    group_id UUID REFERENCES groups(id),
    giver_user_id UUID NOT NULL,
    receiver_user_id UUID NOT NULL,
    assigned_at TIMESTAMP DEFAULT NOW(),
    revealed BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (group_id, giver_user_id)
);
```

---

## 🎲 Draw Algorithm
```java
public void executeDraw(String groupId) {
    List<String> members = getMemberIds(groupId);
    
    if (members.size() < 3) {
        throw new InvalidDrawException("Min 3 members required");
    }
    
    // Shuffle with cryptographic random
    Collections.shuffle(members, SecureRandom.getInstanceStrong());
    
    // Circular assignment
    for (int i = 0; i < members.size(); i++) {
        String giver = members.get(i);
        String receiver = members.get((i + 1) % members.size());
        saveAssignment(groupId, giver, receiver);
    }
    
    kafkaTemplate.send("group.events", new DrawCompletedEvent(groupId));
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
server:
  port: 8082

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

- Spring Boot 4.0.2 + Java 25
- PostgreSQL 15 + Spring Data JPA
- Apache Kafka
- Lombok

---

**Author:** Michał (mjaracz)   
**Role:** Pure Worker (Group Management + Draw Algorithm)