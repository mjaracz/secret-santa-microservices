# Common Library Module

> **Shared code and domain models for Secret Santa Microservices Platform**

---

## 📋 Table of Contents

1. [Purpose](#purpose)
2. [Module Structure](#module-structure)
3. [Event Definitions](#event-definitions)
4. [Command Definitions](#command-definitions)
5. [Data Transfer Objects (DTOs)](#data-transfer-objects-dtos)
6. [Custom Exceptions](#custom-exceptions)
7. [Utilities](#utilities)
8. [Usage in Services](#usage-in-services)
9. [Building & Installation](#building--installation)
10. [Versioning Strategy](#versioning-strategy)

---

## 🎯 Purpose

**Common Library** is a shared Maven module that contains:
- **Event definitions** - Kafka event POJOs shared across all microservices
- **Command definitions** - Command objects published by API Gateway
- **DTOs** - Data Transfer Objects for inter-service communication
- **Exceptions** - Custom business exceptions
- **Utilities** - Shared helper classes (date formatting, validation, etc.)

### Why Common Library?

**Problem without common-lib:**
```
User Service: UserCreatedEvent (version 1)
Group Service: UserCreatedEvent (version 2) // Incompatible!
Notification Service: UserCreatedEvent (version 3) // Different schema!
```
❌ Event schema inconsistency  
❌ Duplication of code  
❌ Difficult to maintain  

**Solution with common-lib:**
```
common-lib: UserCreatedEvent (single source of truth)
    ↓
User Service (depends on common-lib)
Group Service (depends on common-lib)
Notification Service (depends on common-lib)
```
✅ Single source of truth  
✅ Consistent event schemas  
✅ Easy to evolve (change once, rebuild all)  

---

## 📦 Module Structure

```
common-lib/
├── pom.xml
└── src/main/java/com/secretsanta/common/
    ├── events/                    # Event POJOs
    │   ├── user/
    │   │   ├── UserCreatedEvent.java
    │   │   ├── UserUpdatedEvent.java
    │   │   └── UserDeletedEvent.java
    │   ├── group/
    │   │   ├── GroupCreatedEvent.java
    │   │   ├── UserAddedToGroupEvent.java
    │   │   ├── UserRemovedFromGroupEvent.java
    │   │   ├── DrawCompletedEvent.java
    │   │   └── GroupDeletedEvent.java
    │   ├── wishlist/
    │   │   ├── WishlistItemAddedEvent.java
    │   │   ├── WishlistItemUpdatedEvent.java
    │   │   └── WishlistItemDeletedEvent.java
    │   └── notification/
    │       ├── EmailSentEvent.java
    │       └── EmailFailedEvent.java
    │
    ├── commands/                  # Command POJOs
    │   ├── user/
    │   │   ├── CreateUserCommand.java
    │   │   ├── UpdateUserCommand.java
    │   │   └── DeleteUserCommand.java
    │   ├── group/
    │   │   ├── CreateGroupCommand.java
    │   │   ├── AddMemberCommand.java
    │   │   ├── RemoveMemberCommand.java
    │   │   └── ExecuteDrawCommand.java
    │   └── wishlist/
    │       ├── AddWishlistItemCommand.java
    │       ├── UpdateWishlistItemCommand.java
    │       └── DeleteWishlistItemCommand.java
    │
    ├── dto/                       # Data Transfer Objects
    │   ├── UserDTO.java
    │   ├── GroupDTO.java
    │   ├── GroupMemberDTO.java
    │   ├── WishlistDTO.java
    │   ├── WishlistItemDTO.java
    │   └── AssignmentDTO.java
    │
    ├── exception/                 # Custom Exceptions
    │   ├── UserNotFoundException.java
    │   ├── GroupNotFoundException.java
    │   ├── InvalidDrawException.java
    │   ├── WishlistNotFoundException.java
    │   └── DuplicateEmailException.java
    │
    └── util/                      # Utility Classes
        ├── DateUtils.java
        ├── ValidationUtils.java
        └── EventSerializer.java
```

---

## 📨 Event Definitions

### User Events

**Package:** `com.secretsanta.common.events.user`

#### UserCreatedEvent

```java
package com.secretsanta.common.events.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event published when a new user registers in the system.
 * 
 * Producer: User Service
 * Consumers: Group Service, Notification Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedEvent {
    
    @JsonProperty("eventType")
    private String eventType = "UserCreatedEvent";
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("timestamp")
    private long timestamp;
}
```

#### UserUpdatedEvent

```java
/**
 * Event published when user profile is updated.
 * 
 * Producer: User Service
 * Consumers: Group Service (update cache), Notification Service (optional)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdatedEvent {
    
    private String eventType = "UserUpdatedEvent";
    private String userId;
    private String email;
    private String name;
    private long timestamp;
}
```

#### UserDeletedEvent

```java
/**
 * Event published when user account is deleted.
 * 
 * Producer: User Service
 * Consumers: Group Service (remove from all groups), Wishlist Service (delete all wishlists)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDeletedEvent {
    
    private String eventType = "UserDeletedEvent";
    private String userId;
    private long timestamp;
}
```

### Group Events

**Package:** `com.secretsanta.common.events.group`

#### GroupCreatedEvent

```java
/**
 * Event published when a new Secret Santa group is created.
 * 
 * Producer: Group Service
 * Consumers: Wishlist Service, Notification Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupCreatedEvent {
    
    private String eventType = "GroupCreatedEvent";
    private String groupId;
    private String groupName;
    private String creatorUserId;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private LocalDate exchangeDate;
    private long timestamp;
}
```

#### UserAddedToGroupEvent

```java
/**
 * Event published when a user is added to a group.
 * 
 * Producer: Group Service
 * Consumers: Notification Service (send invitation email)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAddedToGroupEvent {
    
    private String eventType = "UserAddedToGroupEvent";
    private String groupId;
    private String groupName;
    private String userId;
    private String userEmail;
    private String userName;
    private String roleInGroup; // ORGANIZER, MEMBER
    private long timestamp;
}
```

#### DrawCompletedEvent

```java
/**
 * Event published when Secret Santa draw is executed for a group.
 * 
 * Producer: Group Service
 * Consumers: Wishlist Service (unlock visibility), Notification Service (send assignments)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawCompletedEvent {
    
    private String eventType = "DrawCompletedEvent";
    private String groupId;
    private String groupName;
    private Map<String, String> assignments; // giverUserId -> receiverUserId
    private long timestamp;
}
```

### Wishlist Events

**Package:** `com.secretsanta.common.events.wishlist`

#### WishlistItemAddedEvent

```java
/**
 * Event published when user adds item to their wishlist.
 * 
 * Producer: Wishlist Service
 * Consumers: Notification Service (notify Santa if draw completed)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemAddedEvent {
    
    private String eventType = "WishlistItemAddedEvent";
    private String itemId;
    private String userId;
    private String groupId;
    private String itemName;
    private String description;
    private String url;
    private BigDecimal price;
    private int priority; // 1=high, 2=medium, 3=low
    private long timestamp;
}
```

---

## 📩 Command Definitions

### User Commands

**Package:** `com.secretsanta.common.commands.user`

#### CreateUserCommand

```java
package com.secretsanta.common.commands.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Command to create a new user account.
 * 
 * Publisher: API Gateway
 * Consumer: User Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserCommand {
    
    private String commandType = "CreateUserCommand";
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
    
    private long timestamp;
}
```

### Group Commands

**Package:** `com.secretsanta.common.commands.group`

#### CreateGroupCommand

```java
/**
 * Command to create a new Secret Santa group.
 * 
 * Publisher: API Gateway
 * Consumer: Group Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupCommand {
    
    private String commandType = "CreateGroupCommand";
    
    @NotBlank(message = "Group name is required")
    private String groupName;
    
    @NotBlank(message = "Creator user ID is required")
    private String creatorUserId;
    
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private LocalDate exchangeDate;
    
    private long timestamp;
}
```

#### ExecuteDrawCommand

```java
/**
 * Command to execute Secret Santa draw for a group.
 * 
 * Publisher: API Gateway
 * Consumer: Group Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteDrawCommand {
    
    private String commandType = "ExecuteDrawCommand";
    
    @NotBlank(message = "Group ID is required")
    private String groupId;
    
    @NotBlank(message = "Executor user ID is required")
    private String executorUserId; // Must be group organizer
    
    private long timestamp;
}
```

---

## 📋 Data Transfer Objects (DTOs)

**Package:** `com.secretsanta.common.dto`

### UserDTO

```java
/**
 * User data transfer object for inter-service communication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private String userId;
    private String email;
    private String name;
    private LocalDateTime createdAt;
}
```

### GroupDTO

```java
/**
 * Group data transfer object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupDTO {
    private String groupId;
    private String groupName;
    private String creatorUserId;
    private String status; // ACTIVE, DRAWN, COMPLETED, ARCHIVED
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private LocalDate exchangeDate;
    private List<GroupMemberDTO> members;
    private LocalDateTime createdAt;
}
```

### WishlistItemDTO

```java
/**
 * Wishlist item data transfer object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemDTO {
    private String itemId;
    private String itemName;
    private String description;
    private String url;
    private BigDecimal price;
    private int priority;
    private String status; // AVAILABLE, PURCHASED
}
```

---

## 🚨 Custom Exceptions

**Package:** `com.secretsanta.common.exception`

### Base Exception

```java
/**
 * Base exception for all Secret Santa business exceptions.
 */
public abstract class SecretSantaException extends RuntimeException {
    
    private final String errorCode;
    
    protected SecretSantaException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}
```

### Domain-Specific Exceptions

```java
// User Domain
public class UserNotFoundException extends SecretSantaException {
    public UserNotFoundException(String userId) {
        super("User not found: " + userId, "USER_NOT_FOUND");
    }
}

public class DuplicateEmailException extends SecretSantaException {
    public DuplicateEmailException(String email) {
        super("Email already exists: " + email, "DUPLICATE_EMAIL");
    }
}

// Group Domain
public class GroupNotFoundException extends SecretSantaException {
    public GroupNotFoundException(String groupId) {
        super("Group not found: " + groupId, "GROUP_NOT_FOUND");
    }
}

public class InvalidDrawException extends SecretSantaException {
    public InvalidDrawException(String message) {
        super(message, "INVALID_DRAW");
    }
}

public class UserNotInGroupException extends SecretSantaException {
    public UserNotInGroupException(String userId, String groupId) {
        super("User " + userId + " not in group " + groupId, "USER_NOT_IN_GROUP");
    }
}

// Wishlist Domain
public class WishlistNotFoundException extends SecretSantaException {
    public WishlistNotFoundException(String wishlistId) {
        super("Wishlist not found: " + wishlistId, "WISHLIST_NOT_FOUND");
    }
}
```

---

## 🛠️ Utilities

**Package:** `com.secretsanta.common.util`

### DateUtils

```java
/**
 * Utility class for date/time operations.
 */
public class DateUtils {
    
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
    
    public static LocalDateTime fromTimestamp(long timestamp) {
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp), 
            ZoneId.systemDefault()
        );
    }
    
    public static String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
```

### ValidationUtils

```java
/**
 * Utility class for validation operations.
 */
public class ValidationUtils {
    
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    public static boolean isValidUUID(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
```

---

## 🔧 Usage in Services

### Adding Dependency

**In service pom.xml:**

```xml
<dependencies>
    <!-- Common Library -->
    <dependency>
        <groupId>com.secretsanta</groupId>
        <artifactId>common-lib</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### Publishing Events (Producer)

```java
// User Service - Publishing UserCreatedEvent
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UserRepository userRepository;
    
    public User createUser(CreateUserCommand command) {
        // Validate
        if (userRepository.existsByEmail(command.getEmail())) {
            throw new DuplicateEmailException(command.getEmail());
        }
        
        // Create user
        User user = User.builder()
            .email(command.getEmail())
            .name(command.getName())
            .build();
        
        user = userRepository.save(user);
        
        // Publish event (using common-lib event)
        UserCreatedEvent event = UserCreatedEvent.builder()
            .userId(user.getId())
            .email(user.getEmail())
            .name(user.getName())
            .timestamp(DateUtils.getCurrentTimestamp())
            .build();
        
        kafkaTemplate.send("user.events", event);
        
        return user;
    }
}
```

### Consuming Events (Consumer)

```java
// Notification Service - Consuming UserCreatedEvent
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {
    
    private final EmailService emailService;
    
    @KafkaListener(topics = "user.events", groupId = "notification-service")
    public void handleUserEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        
        // Using common-lib event
        if (event instanceof UserCreatedEvent) {
            UserCreatedEvent userCreated = (UserCreatedEvent) event;
            
            log.info("Received UserCreatedEvent for user: {}", userCreated.getUserId());
            emailService.sendWelcomeEmail(
                userCreated.getEmail(), 
                userCreated.getName()
            );
        }
    }
}
```

### Using DTOs

```java
// API Gateway - Returning DTO to client
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDTO> getGroup(@PathVariable String groupId) {
        // ... fetch group from cache or query service
        
        // Return common-lib DTO
        GroupDTO groupDTO = GroupDTO.builder()
            .groupId(group.getId())
            .groupName(group.getName())
            .status(group.getStatus())
            .members(convertToMemberDTOs(group.getMembers()))
            .build();
        
        return ResponseEntity.ok(groupDTO);
    }
}
```

### Using Exceptions

```java
// Group Service - Throwing custom exception
@Service
public class DrawService {
    
    public void executeDraw(String groupId) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new GroupNotFoundException(groupId));
        
        if (group.getMembers().size() < 3) {
            throw new InvalidDrawException(
                "Minimum 3 members required for draw, found: " + 
                group.getMembers().size()
            );
        }
        
        // ... execute draw
    }
}
```

---

## 🏗️ Building & Installation

### Local Build

```bash
# Navigate to common-lib directory
cd common-lib

# Clean and install to local Maven repository
mvn clean install

# Output: common-lib-1.0.0-SNAPSHOT.jar installed to ~/.m2/repository/
```

### Build All Modules (From Root)

```bash
# Navigate to project root
cd secret-santa-microservices

# Build parent + all modules
mvn clean install

# Skip tests for faster build
mvn clean install -DskipTests
```

### Verify Installation

```bash
# Check local Maven repository
ls ~/.m2/repository/com/secretsanta/common-lib/1.0.0-SNAPSHOT/

# Expected output:
# common-lib-1.0.0-SNAPSHOT.jar
# common-lib-1.0.0-SNAPSHOT.pom
```

---

## 📌 Versioning Strategy

### Semantic Versioning

**Format:** `MAJOR.MINOR.PATCH`

- **MAJOR** - Breaking changes (incompatible event schemas)
- **MINOR** - New features (new events, backward compatible)
- **PATCH** - Bug fixes (no schema changes)

### Examples

**Version 1.0.0** (Initial Release)
- UserCreatedEvent, GroupCreatedEvent, WishlistItemAddedEvent
- Base exceptions
- Core utilities

**Version 1.1.0** (Add New Event - Minor)
- ✅ Add `UserPasswordResetEvent`
- ✅ Backward compatible (old services ignore new event)
- ✅ No breaking changes

**Version 2.0.0** (Breaking Change - Major)
- ❌ Change `UserCreatedEvent.userId` from String to Long
- ❌ Breaking change (all services must update)
- ❌ Requires rebuild of all services

### Best Practices

1. **Avoid Breaking Changes** - Add new fields instead of modifying existing
2. **Use Optional Fields** - New fields should have defaults
3. **Deprecation Strategy** - Mark old fields as @Deprecated before removal
4. **Communicate Changes** - Document all changes in CHANGELOG.md

**Example: Adding Field (Non-Breaking)**

```java
// Version 1.0.0
public class UserCreatedEvent {
    private String userId;
    private String email;
    private String name;
}

// Version 1.1.0 (Add optional field)
public class UserCreatedEvent {
    private String userId;
    private String email;
    private String name;
    private String phoneNumber; // New optional field (null if not provided)
}
```

**Example: Breaking Change (Requires Major Version)**

```java
// Version 1.x.x
public class DrawCompletedEvent {
    private Map<String, String> assignments; // giverUserId -> receiverUserId
}

// Version 2.0.0 (Breaking: change data structure)
public class DrawCompletedEvent {
    private List<AssignmentDTO> assignments; // Better structure
}

// ❌ This requires all consumers to update!
```

---

## 📝 Development Guidelines

### Adding New Event

1. **Create Event Class**
   ```java
   @Data
   @Builder
   @NoArgsConstructor
   @AllArgsConstructor
   public class NewEvent {
       private String eventType = "NewEvent";
       private String someId;
       private long timestamp;
   }
   ```

2. **Add Jackson Annotations**
   ```java
   @JsonProperty("eventType")
   private String eventType;
   ```

3. **Document Producer/Consumers**
   ```java
   /**
    * Event published when...
    * 
    * Producer: Service X
    * Consumers: Service Y, Service Z
    */
   ```

4. **Add to Kafka Type Mapping**
   ```yaml
   # In consumer service application.yml
   spring:
     kafka:
       consumer:
         properties:
           spring.json.type.mapping: >
             NewEvent:com.secretsanta.common.events.NewEvent
   ```

5. **Update Common-Lib Version**
   ```xml
   <!-- pom.xml -->
   <version>1.1.0-SNAPSHOT</version>
   ```

6. **Rebuild and Install**
   ```bash
   mvn clean install
   ```

### Testing Events

```java
@Test
public void testUserCreatedEventSerialization() {
    UserCreatedEvent event = UserCreatedEvent.builder()
        .userId("user-123")
        .email("test@example.com")
        .name("John Doe")
        .timestamp(System.currentTimeMillis())
        .build();
    
    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(event);
    
    UserCreatedEvent deserialized = mapper.readValue(json, UserCreatedEvent.class);
    
    assertEquals(event.getUserId(), deserialized.getUserId());
    assertEquals(event.getEmail(), deserialized.getEmail());
}
```

---

## 🎯 Key Takeaways

✅ **Single Source of Truth** - One definition of each event, used by all services  
✅ **Consistency** - Same event schemas across entire platform  
✅ **Versioning** - Semantic versioning for controlled evolution  
✅ **Reusability** - Shared DTOs, exceptions, utilities  
✅ **Type Safety** - Compile-time checking of event structures  
✅ **Documentation** - Javadoc comments on all public classes  

---

## 📚 Further Reading

- [Event-Driven Architecture Patterns](https://martinfowler.com/articles/201701-event-driven.html)
- [Domain Events (DDD)](https://martinfowler.com/eaaDev/DomainEvent.html)
- [Semantic Versioning](https://semver.org/)
- [Kafka Type Mapping](https://docs.spring.io/spring-kafka/docs/current/reference/html/#type-mapping)

---

**Author:** Michał (mjaracz)  
**Module Type:** Shared Library  
**Language:** Java 25  
**Build Tool:** Maven 3.9+
