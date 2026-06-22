# User Service (Pure Worker)

> **Part of Secret Santa Microservices Platform**
>
> *Note: In production, this would be a separate repository.*

---

## 🏗️ Architectural Role

**Type:** `Pure Event-Driven Worker`

**Responsibility:**  
Domain service responsible for user identity, authentication, and profile management. Processes commands via Kafka, applies business logic, persists to PostgreSQL, and publishes domain events.

**Key Characteristics:**
- ✅ **Pure worker** - Kafka consumer/producer only
- ✅ **Domain logic** - User registration, authentication, profile management
- ✅ **Database per service** - Isolated PostgreSQL instance
- ✅ **Event-driven** - Consumes commands, publishes domain events
- ❌ **No REST endpoints** - Pure asynchronous processing
- ❌ **No HTTP exposure** - Not accessible from external clients

**Architecture Pattern:**  
Event-Driven Microservices + Database per Service + Domain-Driven Design

**Communication Flow:**
```
API Gateway publishes CreateUserCommand
    ↓
Kafka (user.commands topic)
    ↓
User Service @KafkaListener
    ↓
Business Logic (validation, hashing)
    ↓
PostgreSQL (persist user)
    ↓
Kafka (user.events topic)
    ↓
UserCreatedEvent → Other workers
```

---

## 📦 Dependencies

### Spring Boot Initializer Selection

When generating from [start.spring.io](https://start.spring.io):

**Project Metadata:**
- Spring Boot: `4.0.2`
- Java: `25`
- Group: `com.secretsanta`
- Artifact: `user-service`
- Package: `com.secretsanta.user`

**Dependencies to Add:**

| Category | Dependency Name | Identifier | Purpose |
|----------|----------------|------------|---------|
| **Messaging** | Spring for Apache Kafka | `kafka` | Event consumer/producer |
| **SQL** | Spring Data JPA | `data-jpa` | ORM for PostgreSQL |
| **SQL** | PostgreSQL Driver | `postgresql` | Database connectivity |
| **Developer Tools** | Lombok | `lombok` | Reduce boilerplate (@Data, @Builder) |

### Maven Dependencies (pom.xml)
```xml
<dependencies>
    <!-- Spring Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- Spring Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- PostgreSQL Driver -->
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
- ❌ **DO NOT add `spring-boot-starter-web`** - This is a pure worker (no REST)
- ✅ **Minimal dependencies** - Only domain logic essentials

---

## 🎯 Domain Responsibility

### Core Business Logic
- **User Registration** - Create new accounts with email uniqueness validation
- **Profile Management** - Update name, email, password
- **Authentication** - Credential validation, token issuance (optional)
- **Account Deletion** - GDPR-compliant removal with cascade cleanup
- **Email Verification** - Send verification codes (via Notification Service)

### Business Rules
- Email must be unique (database constraint)
- Passwords hashed with BCrypt (never plaintext)
- Deleted users trigger cascade events (remove from groups, wishlists)
- User IDs are UUIDs for global uniqueness

---

## 📨 Event Production

Publishes **domain events** to `user.events`:

| Event Type | Trigger | Consumed By |
|------------|---------|-------------|
| `UserCreatedEvent` | Registration completed | Notification Service (welcome email), Group Service (cache) |
| `UserUpdatedEvent` | Profile modified | Group Service (update cache) |
| `UserDeletedEvent` | Account deleted | Group Service, Wishlist Service (cascade cleanup) |

**Event Schema:**
```json
{
  "eventType": "UserCreatedEvent",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "name": "John Doe",
  "timestamp": 1707562800000
}
```

---

## 📡 Event Consumption

Listens to Kafka topics:

| Topic | Event | Action |
|-------|-------|--------|
| `user.commands` | CreateUserCommand | Validate → Hash password → Save to DB → Publish UserCreatedEvent |
| `user.commands` | UpdateUserCommand | Validate → Update DB → Publish UserUpdatedEvent |
| `user.commands` | DeleteUserCommand | Delete user → Publish UserDeletedEvent |

**Kafka Listener Example:**
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class UserCommandListener {
    
    private final UserService userService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(topics = "user.commands", groupId = "user-service-group")
    public void handleCreateUser(CreateUserCommand command) {
        log.info("Processing CreateUserCommand: {}", command.getEmail());
        
        User user = userService.createUser(command);
        
        kafkaTemplate.send("user.events", user.getId(), 
            new UserCreatedEvent(user.getId(), user.getEmail(), user.getName()));
    }
}
```

---

## 💾 Persistence model (PostgreSQL)

**Database:** `user_db`
**Port:** `5432`

Flyway owns the schema lifecycle. Hibernate only validates mappings through
`spring.jpa.hibernate.ddl-auto=validate`; it does not create or update tables.

| Migration | Responsibility |
|-----------|----------------|
| `V1__create_users_table.sql` | Historical base `users` table |
| `V2__add_registration_status.sql` | Normalized unique email, account status, verification timestamp and optimistic-lock version |
| `V3__add_authentication_and_verification.sql` | User role, verification tokens and refresh-session families |

The persistence boundary consists of three JPA repositories:

- `UserRepository` — account identity and normalized e-mail uniqueness;
- `EmailVerificationTokenRepository` — expiring, one-time verification tokens;
- `RefreshSessionRepository` — refresh-token rotation, family revocation and reuse detection.

Authentication services use Spring transactions. Refresh and revoke operations
lock the selected session row before changing its token family. Database unique
constraints remain the final protection against concurrent registrations and
duplicate token hashes.

### Why the relational latency cost is accepted

The measured end-to-end overhead over the MongoDB implementation was about 9 ms
for successful sign-in, 14 ms for refresh and 17 ms for registration. These paths
still spend more time in BCrypt or the Kafka request-reply flow than in the
database itself.

MongoDB can atomically update a single embedded user document, but refresh-session
history and verification tokens are independently growing records. PostgreSQL
keeps them bounded away from the user row and provides explicit transactions,
row locking, foreign keys, unique constraints, targeted indexes and predictable
cleanup/audit queries. For this identity domain those guarantees justify the
measured latency cost.

See the complete [persistence latency comparison](../docs/user-service-persistence-latency-comparison.md).

---

## 🚀 Running Locally

### Prerequisites
- Java 25
- Docker (Kafka + PostgreSQL)

### Steps

1. **Start infrastructure:**
```bash
   docker compose up -d postgres-user kafka zookeeper
```

2. **Run service:**
```bash
   cd user-service
   mvn spring-boot:run
```

3. **Verify database:**
```bash
   docker compose exec postgres-user psql -U user_admin -d user_db
   \dt  # List tables
   SELECT * FROM users;
```

4. **Publish test command:**
```bash
   # Via Kafka console producer
   docker exec -it kafka kafka-console-producer \
     --broker-list localhost:9092 \
     --topic user.commands
   
   # Then paste JSON:
   {"commandType":"CreateUserCommand","email":"test@example.com","name":"John"}
```

---

## ⚙️ Configuration

`application.properties` reads secrets and connection details from environment
variables. For local development, copy `application-local.example.properties` to
the ignored `application-local.properties` file.

```properties
DB_URL=jdbc:postgresql://localhost:5432/user_db
DB_USERNAME=user_admin
DB_PASSWORD=user_pass
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

---

## 📊 Technology Stack

- **Spring Boot**: 4.0.2
- **Java**: 25
- **Spring Data JPA**: ORM layer
- **PostgreSQL**: 15
- **Apache Kafka**: Event streaming
- **Lombok**: Code generation
- **Maven**: 3.9+

---

## 🔮 Future Enhancements

- [x] Password hashing with BCrypt
- [x] Email verification workflow
- [ ] OAuth2 integration (Google, GitHub)
- [ ] User avatar storage (S3)
- [ ] Two-factor authentication

---

**Author:** Michał (mjaracz) 
**Role:** Pure Event-Driven Worker  
**Domain:** User Identity & Authentication
