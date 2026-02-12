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

## 💾 Database Schema (PostgreSQL)

**Database Name:** `user_db`  
**Port:** `5432` (in docker-compose)
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_created_at ON users(created_at DESC);
```

**Entity Example:**
```java
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "password_hash")
    private String passwordHash;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

---

## 🚀 Running Locally

### Prerequisites
- Java 25
- Docker (Kafka + PostgreSQL)

### Steps

1. **Start infrastructure:**
```bash
   docker-compose up -d postgres-user kafka zookeeper
```

2. **Run service:**
```bash
   cd user-service
   mvn spring-boot:run
```

3. **Verify database:**
```bash
   docker exec -it postgres-user psql -U user_admin -d user_db
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

`application.yml`:
```yaml
server:
  port: 8081  # Internal only (not exposed)

spring:
  application:
    name: user-service

  datasource:
    url: jdbc:postgresql://localhost:5432/user_db
    username: user_admin
    password: user_pass
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: user-service-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
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

- [ ] Password hashing (BCrypt/Argon2)
- [ ] Email verification workflow
- [ ] OAuth2 integration (Google, GitHub)
- [ ] User avatar storage (S3)
- [ ] Two-factor authentication

---

**Author:** Michał (mjaracz) 
**Role:** Pure Event-Driven Worker  
**Domain:** User Identity & Authentication