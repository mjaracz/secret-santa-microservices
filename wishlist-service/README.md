# Wishlist Service (Pure Worker)

> **Part of Secret Santa Microservices Platform**
> 
> *Note: In production, this would be a separate repository.*

---

## 🏗️ Architectural Role

**Type:** `Pure Event-Driven Worker`

**Responsibility:**  
Domain service managing gift wishlists for users within Secret Santa groups. Each user maintains separate wishlists per group they participate in. Wishlists become visible to assigned Secret Santa only after draw completion.

**Key Characteristics:**
- ✅ **Pure worker** - Kafka-only communication (no REST endpoints)
- ✅ **Domain logic** - Wishlist CRUD operations, visibility control
- ✅ **Database per service** - Isolated PostgreSQL instance
- ✅ **Multi-tenancy** - One wishlist per user per group
- ✅ **Event-driven** - Consumes commands, publishes domain events
- ❌ **No REST endpoints** - Pure asynchronous processing
- ❌ **No HTTP exposure** - Internal service only

**Architecture Pattern:**  
Event-Driven Microservices + Database per Service + Per-Group Multi-tenancy

**Communication Flow:**
```
AddWishlistItemCommand (from API Gateway)
    ↓
Kafka (wishlist.commands topic)
    ↓
Wishlist Service @KafkaListener
    ↓
Business Logic (validate user in group, create item)
    ↓
PostgreSQL (persist wishlist item)
    ↓
Kafka (wishlist.events topic)
    ↓
WishlistItemAddedEvent → Notification Service
```

---

## 📦 Dependencies

### Spring Boot Initializer Selection

When generating from [start.spring.io](https://start.spring.io):

**Project Metadata:**
- Spring Boot: `4.0.2`
- Java: `25`
- Packaging: `Jar`
- Group: `com.secretsanta`
- Artifact: `wishlist-service`
- Name: `wishlist-service`
- Package: `com.secretsanta.wishlist`

**Dependencies to Add:**

| Category | Dependency Name | Identifier | Purpose |
|----------|----------------|------------|---------|
| **Messaging** | Spring for Apache Kafka | `kafka` | Event consumer/producer for domain events |
| **SQL** | Spring Data JPA | `data-jpa` | ORM layer for PostgreSQL database |
| **SQL** | PostgreSQL Driver | `postgresql` | JDBC driver for PostgreSQL connectivity |
| **Developer Tools** | Lombok | `lombok` | Reduce boilerplate code (@Data, @Builder, etc.) |

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
- ❌ **DO NOT add `spring-boot-starter-web`** - This is a pure worker (no REST endpoints)
- ❌ **DO NOT add Spring Boot Actuator** - Optional, add only if monitoring needed
- ❌ **DO NOT add Validation** - Optional, add if using @Valid on entities
- ✅ **Minimal dependencies** - Only essentials for domain logic

---

## 🎯 Domain Responsibility

### Core Business Logic

**Wishlist Management:**
- **Per-Group Isolation** - Each user has separate wishlist for each group
- **Item CRUD** - Create, read, update, delete gift ideas
- **Visibility Control** - Wishlist hidden until Secret Santa draw completes
- **Item Details** - Name, description, URL, price estimate, priority level
- **Status Tracking** - Mark items as "purchased" (by Santa, anonymously)

**Lifecycle Management:**
- **Wishlist Creation** - Auto-created when user joins group
- **Visibility Toggle** - Changes from PRIVATE → SANTA_ONLY after draw
- **Cascading Deletion** - Remove wishlist when user leaves group
- **Historical Preservation** - Keep wishlists after group completion

### Business Rules

1. **One Wishlist Per (User, Group) Pair** - Enforced by unique constraint
2. **Visibility States:**
   - `PRIVATE` - Before draw (only owner can see)
   - `SANTA_ONLY` - After draw (owner + assigned Santa can see)
3. **Ownership:**
   - Only wishlist owner can add/edit/delete items
   - Assigned Santa has read-only access + mark as purchased
4. **Price Guidance:**
   - Item prices should respect group budget limits
   - Prices are estimates (not binding)
5. **Priority Levels:**
   - 1 = High priority (really want)
   - 2 = Medium priority (would like)
   - 3 = Low priority (nice to have)

### Data Scope Example

```
User: Alice (user-123)
├─ Group "Work Team" (group-456)
│  └─ Wishlist (wishlist-789)
│     ├─ Item: Laptop Stand ($50, priority 1)
│     ├─ Item: Coffee Mug ($15, priority 2)
│     └─ Item: Wireless Mouse ($40, priority 1)
│
├─ Group "Family" (group-abc)
│  └─ Wishlist (wishlist-def)
│     ├─ Item: Winter Jacket ($120, priority 1)
│     └─ Item: Board Game ($30, priority 2)
│
└─ Group "College Friends" (group-xyz)
   └─ Wishlist (wishlist-uvw)
      ├─ Item: Concert Tickets ($80, priority 1)
      └─ Item: Vinyl Record ($25, priority 3)
```

---

## 📨 Event Production

Publishes **domain events** to `wishlist.events` topic:

| Event Type | Trigger | Payload | Consumed By |
|------------|---------|---------|-------------|
| `WishlistItemAddedEvent` | Item added to wishlist | itemId, userId, groupId, itemName, price | Notification Service (notify Santa if draw completed) |
| `WishlistItemUpdatedEvent` | Item modified (name, price, etc.) | itemId, userId, groupId, changes | Notification Service (notify Santa of updates) |
| `WishlistItemDeletedEvent` | Item removed from wishlist | itemId, userId, groupId | (No consumers currently) |
| `WishlistSharedEvent` | Santa accessed wishlist | wishlistId, santaUserId, groupId | Analytics Service (future) |

**Event Schema Example:**
```json
{
  "eventType": "WishlistItemAddedEvent",
  "itemId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-abc-123",
  "groupId": "group-xyz-456",
  "itemName": "Wireless Headphones",
  "description": "Noise-cancelling, Bluetooth 5.0",
  "url": "https://example.com/product/12345",
  "price": 299.99,
  "priority": 1,
  "timestamp": 1707562800000
}
```

---

## 📡 Event Consumption

Listens to multiple Kafka topics:

### Commands from API Gateway

| Topic | Event | Action |
|-------|-------|--------|
| `wishlist.commands` | AddWishlistItemCommand | Validate user in group → Create item → Publish WishlistItemAddedEvent |
| `wishlist.commands` | UpdateWishlistItemCommand | Validate ownership → Update item → Publish WishlistItemUpdatedEvent |
| `wishlist.commands` | DeleteWishlistItemCommand | Validate ownership → Delete item → Publish WishlistItemDeletedEvent |

### Events from Other Services

| Topic | Event | Action |
|-------|-------|--------|
| `group.events` | DrawCompletedEvent | Update all wishlists in group: visibility = SANTA_ONLY |
| `group.events` | UserRemovedFromGroupEvent | Delete user's wishlist for that specific group |
| `group.events` | GroupDeletedEvent | Delete all wishlists for the group |
| `user.events` | UserDeletedEvent | Delete ALL wishlists belonging to user |

**Kafka Listener Example:**
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class WishlistEventListener {
    
    private final WishlistService wishlistService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(topics = "wishlist.commands", groupId = "wishlist-service-group")
    public void handleAddItem(AddWishlistItemCommand command) {
        log.info("Processing AddWishlistItemCommand for user: {}", command.getUserId());
        
        // Validate user belongs to group
        if (!wishlistService.isUserInGroup(command.getUserId(), command.getGroupId())) {
            throw new UserNotInGroupException("User not authorized for this group");
        }
        
        // Create item
        WishlistItem item = wishlistService.addItem(command);
        
        // Publish event
        kafkaTemplate.send("wishlist.events", 
            WishlistItemAddedEvent.builder()
                .itemId(item.getId())
                .userId(command.getUserId())
                .groupId(command.getGroupId())
                .itemName(item.getName())
                .price(item.getPrice())
                .timestamp(System.currentTimeMillis())
                .build()
        );
    }
    
    @KafkaListener(topics = "group.events", groupId = "wishlist-service-group")
    public void handleDrawCompleted(DrawCompletedEvent event) {
        log.info("Draw completed for group {}, unlocking wishlists", event.getGroupId());
        
        // Update visibility for all wishlists in group
        wishlistService.updateVisibilityForGroup(
            event.getGroupId(), 
            WishlistVisibility.SANTA_ONLY
        );
    }
    
    @KafkaListener(topics = "user.events", groupId = "wishlist-service-group")
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("User {} deleted, removing all wishlists", event.getUserId());
        
        wishlistService.deleteAllWishlistsForUser(event.getUserId());
    }
}
```

---

## 💾 Database Schema (PostgreSQL)

**Database Name:** `wishlist_db`  
**Docker Port:** `5434` (mapped to internal 5432)

### Tables

```sql
-- Wishlists (one per user per group)
CREATE TABLE wishlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    group_id UUID NOT NULL,
    visibility VARCHAR(50) DEFAULT 'PRIVATE', -- PRIVATE, SANTA_ONLY
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_user_group UNIQUE (user_id, group_id)
);

-- Wishlist Items
CREATE TABLE wishlist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wishlist_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    url VARCHAR(500),
    price DECIMAL(10,2),
    priority INT DEFAULT 2, -- 1=high, 2=medium, 3=low
    status VARCHAR(50) DEFAULT 'AVAILABLE', -- AVAILABLE, PURCHASED
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_wishlist FOREIGN KEY (wishlist_id) 
        REFERENCES wishlists(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_wishlists_user_id ON wishlists(user_id);
CREATE INDEX idx_wishlists_group_id ON wishlists(group_id);
CREATE INDEX idx_wishlists_user_group ON wishlists(user_id, group_id);
CREATE INDEX idx_items_wishlist_id ON wishlist_items(wishlist_id);
CREATE INDEX idx_items_status ON wishlist_items(status);
CREATE INDEX idx_items_priority ON wishlist_items(priority);
```

### JPA Entities

```java
// Wishlist.java
@Entity
@Table(name = "wishlists")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wishlist {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "group_id", nullable = false)
    private String groupId;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private WishlistVisibility visibility;
    
    @OneToMany(mappedBy = "wishlist", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WishlistItem> items = new ArrayList<>();
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (visibility == null) {
            visibility = WishlistVisibility.PRIVATE;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

// WishlistItem.java
@Entity
@Table(name = "wishlist_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wishlist_id", nullable = false)
    private Wishlist wishlist;
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(length = 500)
    private String url;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal price;
    
    private Integer priority; // 1, 2, 3
    
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private ItemStatus status;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = ItemStatus.AVAILABLE;
        }
        if (priority == null) {
            priority = 2;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

// Enums
public enum WishlistVisibility {
    PRIVATE,      // Only owner can see
    SANTA_ONLY    // Owner + assigned Santa can see
}

public enum ItemStatus {
    AVAILABLE,    // Not yet purchased
    PURCHASED     // Santa marked as purchased
}
```

---

## 🚀 Running Locally

### Prerequisites
- Java 25
- Docker & Docker Compose (for Kafka + PostgreSQL)
- Maven 3.9+

### Steps

1. **Start infrastructure:**
   ```bash
   docker-compose up -d postgres-wishlist kafka zookeeper
   ```

2. **Verify PostgreSQL is running:**
   ```bash
   docker ps | grep postgres-wishlist
   ```

3. **Run service:**
   ```bash
   cd wishlist-service
   mvn clean spring-boot:run
   ```

4. **Verify database connection:**
   ```bash
   # Connect to PostgreSQL
   docker exec -it postgres-wishlist psql -U wishlist_admin -d wishlist_db
   
   # List tables
   \dt
   
   # Check wishlists
   SELECT * FROM wishlists;
   
   # Check items
   SELECT * FROM wishlist_items;
   
   # Exit
   \q
   ```

5. **Test with Kafka command:**
   ```bash
   # Publish test command
   docker exec -it kafka kafka-console-producer \
     --broker-list localhost:9092 \
     --topic wishlist.commands
   
   # Paste JSON (then Ctrl+C to exit):
   {
     "commandType": "AddWishlistItemCommand",
     "userId": "user-123",
     "groupId": "group-456",
     "name": "Wireless Mouse",
     "description": "Logitech MX Master 3",
     "url": "https://example.com/mouse",
     "price": 99.99,
     "priority": 1
   }
   ```

6. **Monitor events:**
   ```bash
   # Listen to published events
   docker exec -it kafka kafka-console-consumer \
     --bootstrap-server localhost:9092 \
     --topic wishlist.events \
     --from-beginning
   ```

---

## ⚙️ Configuration

`application.yml`:

```yaml
server:
  port: 8083  # Internal port (not exposed externally)

spring:
  application:
    name: wishlist-service

  datasource:
    url: jdbc:postgresql://localhost:5434/wishlist_db
    username: wishlist_admin
    password: wishlist_pass
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5

  jpa:
    hibernate:
      ddl-auto: update  # Use 'validate' + Flyway in production
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        use_sql_comments: true
        jdbc:
          batch_size: 20
        order_inserts: true
        order_updates: true

  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: wishlist-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
        spring.json.type.mapping: >
          AddWishlistItemCommand:com.secretsanta.common.events.AddWishlistItemCommand,
          UpdateWishlistItemCommand:com.secretsanta.common.events.UpdateWishlistItemCommand,
          DrawCompletedEvent:com.secretsanta.common.events.DrawCompletedEvent
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        spring.json.type.mapping: >
          WishlistItemAddedEvent:com.secretsanta.common.events.WishlistItemAddedEvent

logging:
  level:
    com.secretsanta.wishlist: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    org.springframework.kafka: INFO
```

---

## 🏗️ Architecture Notes

### Per-Group Isolation Pattern

**Why separate wishlists per group?**

1. **Different Contexts:**
   - Work gifts vs family gifts require different items
   - Professional vs personal gift preferences
2. **Budget Isolation:**
   - Work group: $50 limit → Wishlist with office items
   - Family group: $200 limit → Wishlist with expensive items
3. **Privacy:**
   - Colleagues shouldn't see family wishlist
   - Family shouldn't see work wishlist

### Visibility State Machine

```
PRIVATE (initial state)
    │
    │ DrawCompletedEvent received
    ↓
SANTA_ONLY (after draw)
    │
    │ (stays in this state)
    ↓
(no further state changes)
```

**Implementation:**
```java
@Service
@RequiredArgsConstructor
public class WishlistVisibilityService {
    
    private final WishlistRepository wishlistRepository;
    
    @Transactional
    public void unlockWishlistsForGroup(String groupId) {
        List<Wishlist> wishlists = wishlistRepository.findByGroupId(groupId);
        
        wishlists.forEach(wishlist -> {
            wishlist.setVisibility(WishlistVisibility.SANTA_ONLY);
        });
        
        wishlistRepository.saveAll(wishlists);
    }
}
```

### Santa Access Control

**How Santa views receiver's wishlist:**

```java
@Service
@RequiredArgsConstructor
public class WishlistQueryService {
    
    private final WishlistRepository wishlistRepository;
    private final AssignmentRepository assignmentRepository; // From Group Service cache
    
    public Wishlist getWishlistForSanta(String groupId, String santaUserId) {
        // 1. Find who Santa is buying for
        Assignment assignment = assignmentRepository
            .findByGroupIdAndGiverId(groupId, santaUserId)
            .orElseThrow(() -> new AssignmentNotFoundException("No assignment found"));
        
        String receiverUserId = assignment.getReceiverUserId();
        
        // 2. Get receiver's wishlist
        Wishlist wishlist = wishlistRepository
            .findByUserIdAndGroupId(receiverUserId, groupId)
            .orElseThrow(() -> new WishlistNotFoundException("Receiver has no wishlist"));
        
        // 3. Verify visibility (must be SANTA_ONLY)
        if (wishlist.getVisibility() != WishlistVisibility.SANTA_ONLY) {
            throw new WishlistNotAvailableException("Draw not completed yet");
        }
        
        return wishlist;
    }
}
```

---

## 📊 Technology Stack

- **Spring Boot**: 4.0.2
- **Java**: 25
- **Spring Data JPA**: ORM layer
- **PostgreSQL**: 15 (relational database)
- **Apache Kafka**: Event streaming platform
- **Lombok**: Boilerplate reduction
- **Maven**: 3.9+ (build tool)

---

## 🔮 Future Enhancements

- [ ] **Image Upload** - Store product images (AWS S3 integration)
- [ ] **External APIs** - Integrate with Amazon/eBay for product links
- [ ] **Price Tracking** - Monitor item prices, alert if on sale
- [ ] **Collaborative Wishlists** - Family members can suggest items for user
- [ ] **Wishlist Templates** - Common categories (Tech Gadgets, Books, Fashion)
- [ ] **Anonymous Messaging** - Santa can ask questions about wishlist items
- [ ] **Purchase Coordination** - Prevent duplicate purchases in group
- [ ] **Gift Registry Integration** - Import from Amazon Wishlist, etc.

---

**Author:** Michał (mjaracz)  
**Role:** Pure Event-Driven Worker  
**Domain:** Gift Wishlist Management (Per-Group Multi-tenancy)  
**Database:** PostgreSQL (Relational)
