# Notification Service (Pure Worker) ⭐ MongoDB

> **Part of Secret Santa Microservices Platform**
> 
> *Note: In production, this would be a separate repository.*

---

## 🏗️ Architectural Role

**Type:** `Pure Event-Driven Worker` (Log Aggregator Pattern)

**Responsibility:**  
Cross-cutting notification service that reacts to business events from ALL domain services and sends appropriate notifications to users via multiple channels (email, SMS, push). Logs all notification attempts to MongoDB for audit and analytics.

**Key Characteristics:**
- ✅ **Pure worker** - Kafka consumer only (no event production)
- ✅ **Cross-cutting concern** - Listens to events from ALL services
- ✅ **NoSQL database** - MongoDB for flexible, log-like data storage
- ✅ **Multi-channel** - Email (SMTP), SMS (future), Push (future)
- ✅ **Reactive consumer** - Triggered by any domain event
- ❌ **No REST endpoints** - Pure event consumer
- ❌ **No domain logic** - Only notification delivery and logging

**Architecture Pattern:**  
Event-Driven Microservices + Log Aggregator + Polyglot Persistence (MongoDB)

**Why MongoDB?**  
Notification Service demonstrates **polyglot persistence** - using the right database for the right job. MongoDB is perfect for notifications because of zero foreign keys, flexible schema for different notification types, and log aggregation patterns.

**Communication Flow:**
```
Any Business Event (UserCreated, DrawCompleted, etc.)
    ↓
Notification Service @KafkaListener
    ↓
Determine notification type & recipient
    ↓
Fetch user preferences from MongoDB
    ↓
Render email template (Thymeleaf/plain text)
    ↓
Send via SMTP (Gmail, SendGrid, AWS SES)
    ↓
Log result to MongoDB (SENT/FAILED + metadata)
    ↓
(Optional) Publish EmailSentEvent
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
- Artifact: `notification-service`
- Name: `notification-service`
- Package: `com.secretsanta.notification`

**Dependencies to Add:**

| Category | Dependency Name | Identifier | Purpose |
|----------|----------------|------------|---------|
| **Messaging** | Spring for Apache Kafka | `kafka` | Event consumer (listen to ALL domain events) |
| **NoSQL** | Spring Data MongoDB | `data-mongodb` | Document database for notification logs |
| **I/O** | Java Mail Sender | `mail` | SMTP email sending capability |
| **Developer Tools** | Lombok | `lombok` | Reduce boilerplate code |

### Maven Dependencies (pom.xml)

```xml
<dependencies>
    <!-- Spring Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- Spring Data MongoDB -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>

    <!-- Java Mail Sender (SMTP) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-mail</artifactId>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    
    <!-- Optional: Thymeleaf for HTML email templates -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

**Critical Notes:**
- ❌ **DO NOT add `spring-boot-starter-web`** - Pure worker (no REST)
- ❌ **DO NOT add JPA/PostgreSQL** - Using MongoDB (NoSQL)
- ✅ **MongoDB instead of PostgreSQL** - Demonstrates polyglot persistence

### Why MongoDB for Notification Service?

| Reason | Explanation |
|--------|-------------|
| **Zero Foreign Keys** | userId/groupId are just string references, no JOINs needed |
| **Flexible Schema** | Metadata varies per notification type (email vs SMS vs push) |
| **Document-Oriented** | Each notification is a self-contained document |
| **High Write Throughput** | Can handle thousands of notifications per second |
| **TTL Indexes** | Auto-delete old notifications after 30/90 days |
| **No Complex Queries** | Simple lookups by userId, status, type, date range |
| **Log Aggregation** | Notifications are append-only logs (perfect for MongoDB) |
| **Schema Evolution** | Easy to add new notification types without migrations |

**Portfolio Benefit:** Shows understanding of **polyglot persistence** architecture (PostgreSQL for transactional data, MongoDB for logs/events).

---

## 🎯 Domain Responsibility

### Core Functionality

**Notification Delivery:**
- **Multi-Channel Support** - Email (primary), SMS (future), Push notifications (future)
- **Template Management** - Dynamic email content with Thymeleaf templates
- **SMTP Integration** - Gmail, SendGrid, AWS SES, or any SMTP server
- **Retry Logic** - Exponential backoff for transient SMTP failures
- **Delivery Tracking** - Log every attempt (success/failure) to MongoDB

**Reactive Event Processing:**
- **Universal Listener** - Subscribes to ALL domain event topics
- **Event Routing** - Maps event types to notification templates
- **User Preferences** - Respect opt-out settings (future enhancement)
- **Batch Notifications** - Group multiple events into single email (future)

### Notification Types Handled

| Source Service | Trigger Event | Notification | Template |
|----------------|---------------|--------------|----------|
| User Service | UserCreatedEvent | Welcome email | `welcome.html` |
| User Service | PasswordResetEvent | Password reset link | `password-reset.html` |
| Group Service | UserAddedToGroupEvent | Group invitation | `group-invitation.html` |
| Group Service | DrawCompletedEvent | "You are Santa for [Name]" | `draw-completed.html` |
| Group Service | GroupDeletedEvent | Group archived notice | `group-archived.html` |
| Wishlist Service | WishlistItemAddedEvent | Notify Santa of new item | `wishlist-updated.html` |
| Wishlist Service | WishlistItemUpdatedEvent | Notify Santa of changes | `wishlist-updated.html` |
| Scheduled Job | Cron trigger | Exchange date reminder | `exchange-reminder.html` |

### Business Rules

1. **Notification Attempt Tracking:**
   - Every notification attempt logged to MongoDB (success or failure)
   - Include timestamp, recipient, status, error message (if failed)
   
2. **Retry Policy:**
   - Retry failed deliveries up to 3 times with exponential backoff
   - 1st retry: 1 minute
   - 2nd retry: 5 minutes
   - 3rd retry: 15 minutes
   - After 3 failures: Mark as FAILED and alert monitoring
   
3. **Data Privacy:**
   - Never log Secret Santa assignments in plaintext
   - Encrypt sensitive data in logs
   - Respect user opt-out preferences
   
4. **Data Retention:**
   - Keep notification logs for 30 days (TTL index)
   - Archive critical notifications (password resets) for 90 days

---

## 📨 Event Production

**Optional** - Notification Service typically does NOT produce events, but can publish:

| Topic | Event | Trigger | Consumed By |
|-------|-------|---------|-------------|
| `notification.events` | EmailSentEvent | Email delivered successfully | Analytics Service (future) |
| `notification.events` | EmailFailedEvent | Email failed after all retries | Monitoring/Alerting System |

*Note: Event production is optional and can be added for observability.*

---

## 📡 Event Consumption

**Listens to ALL domain event topics:**

### User Service Events

| Topic | Event | Notification Action |
|-------|-------|---------------------|
| `user.events` | UserCreatedEvent | Send welcome email to new user |
| `user.events` | UserUpdatedEvent | Send profile update confirmation (optional) |
| `user.events` | PasswordResetRequestedEvent | Send password reset link |

### Group Service Events

| Topic | Event | Notification Action |
|-------|-------|---------------------|
| `group.events` | GroupCreatedEvent | Send confirmation to creator (optional) |
| `group.events` | UserAddedToGroupEvent | Send group invitation email to member |
| `group.events` | UserRemovedFromGroupEvent | Send removal notice |
| `group.events` | DrawCompletedEvent | Send assignment emails to ALL group members |
| `group.events` | GroupDeletedEvent | Send archive notification to all members |

### Wishlist Service Events

| Topic | Event | Notification Action |
|-------|-------|---------------------|
| `wishlist.events` | WishlistItemAddedEvent | Notify assigned Santa (if draw completed) |
| `wishlist.events` | WishlistItemUpdatedEvent | Notify Santa of wishlist changes |

**Kafka Listener Example:**
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {
    
    private final EmailService emailService;
    private final NotificationLogRepository notificationRepository;
    private final UserPreferencesRepository preferencesRepository;
    
    /**
     * Handle user registration - send welcome email
     */
    @KafkaListener(topics = "user.events", groupId = "notification-service-group")
    public void handleUserEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        
        if (event instanceof UserCreatedEvent) {
            UserCreatedEvent userCreated = (UserCreatedEvent) event;
            sendWelcomeEmail(userCreated);
        }
    }
    
    private void sendWelcomeEmail(UserCreatedEvent event) {
        log.info("Sending welcome email to: {}", event.getEmail());
        
        NotificationLog log = NotificationLog.builder()
            .notificationId(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .type(NotificationType.WELCOME_EMAIL)
            .channel(NotificationChannel.EMAIL)
            .recipient(event.getEmail())
            .createdAt(LocalDateTime.now())
            .build();
        
        try {
            // Send email via SMTP
            emailService.sendWelcomeEmail(event.getEmail(), event.getName());
            
            // Log success
            log.setStatus(DeliveryStatus.SENT);
            log.setSentAt(LocalDateTime.now());
            log.setSubject("Welcome to Secret Santa!");
            
        } catch (MailException e) {
            log.error("Failed to send welcome email", e);
            
            // Log failure
            log.setStatus(DeliveryStatus.FAILED);
            log.setErrorMessage(e.getMessage());
            log.setRetryCount(0);
        }
        
        // Save to MongoDB
        notificationRepository.save(log);
    }
    
    /**
     * Handle draw completion - send assignment emails to ALL participants
     */
    @KafkaListener(topics = "group.events", groupId = "notification-service-group")
    public void handleGroupEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        
        if (event instanceof DrawCompletedEvent) {
            DrawCompletedEvent drawCompleted = (DrawCompletedEvent) event;
            sendAssignmentEmails(drawCompleted);
        } else if (event instanceof UserAddedToGroupEvent) {
            UserAddedToGroupEvent userAdded = (UserAddedToGroupEvent) event;
            sendGroupInvitation(userAdded);
        }
    }
    
    private void sendAssignmentEmails(DrawCompletedEvent event) {
        log.info("Sending assignment emails for group: {}", event.getGroupId());
        
        // Send email to each participant
        for (Map.Entry<String, String> assignment : event.getAssignments().entrySet()) {
            String giverId = assignment.getKey();
            String receiverId = assignment.getValue();
            
            // Fetch giver's email and receiver's name
            // ... (implementation)
            
            emailService.sendAssignmentEmail(
                giverEmail,
                giverName,
                receiverName,
                event.getGroupName()
            );
        }
    }
    
    /**
     * Handle wishlist updates - notify Santa
     */
    @KafkaListener(topics = "wishlist.events", groupId = "notification-service-group")
    public void handleWishlistEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        
        if (event instanceof WishlistItemAddedEvent) {
            WishlistItemAddedEvent itemAdded = (WishlistItemAddedEvent) event;
            
            // Only notify if draw has been completed
            if (isDrawCompleted(itemAdded.getGroupId())) {
                notifySantaOfWishlistUpdate(itemAdded);
            }
        }
    }
}
```

---

## 💾 Database Schema (MongoDB)

**Database Name:** `notification_db`  
**Docker Port:** `27017` (MongoDB default)

### Collection: `notifications`

**Document Structure:**

```javascript
{
  "_id": ObjectId("65c8f4a3b1234567890abcde"),
  "notificationId": "550e8400-e29b-41d4-a716-446655440000", // UUID
  "userId": "user-uuid-123",
  "type": "DRAW_COMPLETED", // Enum: WELCOME_EMAIL, GROUP_INVITATION, etc.
  "channel": "EMAIL", // Enum: EMAIL, SMS, PUSH
  "recipient": "user@example.com",
  "subject": "You are Secret Santa for John!",
  "body": "Dear Alice, you have been assigned to be Secret Santa for John...",
  "status": "SENT", // Enum: SENT, FAILED, PENDING
  "sentAt": ISODate("2025-02-10T14:30:00.000Z"),
  "createdAt": ISODate("2025-02-10T14:29:55.000Z"),
  "metadata": {
    "groupId": "group-uuid-456",
    "groupName": "Family Secret Santa",
    "receiverName": "John",
    "receiverUserId": "user-uuid-789",
    "retryCount": 0,
    "smtpServer": "smtp.gmail.com",
    "template": "draw-completed"
  },
  "errorMessage": null,
  "expiresAt": ISODate("2025-03-12T14:29:55.000Z") // TTL: 30 days
}
```

### Collection: `user_preferences`

**Document Structure:**

```javascript
{
  "_id": ObjectId("65c8f4a3b1234567890abcdf"),
  "userId": "user-uuid-123",
  "emailEnabled": true,
  "smsEnabled": false,
  "pushEnabled": true,
  "notificationTypes": {
    "welcomeEmail": true,
    "groupInvitation": true,
    "drawCompleted": true,
    "wishlistUpdates": true,
    "exchangeReminder": true
  },
  "updatedAt": ISODate("2025-02-10T08:00:00.000Z")
}
```

### MongoDB Indexes

```javascript
// TTL Index - Auto-delete notifications after 30 days
db.notifications.createIndex(
  { "expiresAt": 1 }, 
  { expireAfterSeconds: 0 }
);

// Query by user
db.notifications.createIndex(
  { "userId": 1, "sentAt": -1 }
);

// Query by status (find failed notifications)
db.notifications.createIndex(
  { "status": 1, "createdAt": -1 }
);

// Query by type and date
db.notifications.createIndex(
  { "type": 1, "sentAt": -1 }
);

// User preferences lookup
db.user_preferences.createIndex(
  { "userId": 1 },
  { unique: true }
);
```

### Spring Data MongoDB Entities

```java
// NotificationLog.java
@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {
    
    @Id
    private String id; // MongoDB ObjectId
    
    @Indexed
    private String notificationId; // UUID for external reference
    
    @Indexed
    private String userId;
    
    @Indexed
    private NotificationType type;
    
    private NotificationChannel channel;
    
    private String recipient; // email, phone, device token
    
    private String subject;
    
    private String body;
    
    @Indexed
    private DeliveryStatus status;
    
    @Indexed
    private LocalDateTime sentAt;
    
    @Indexed
    private LocalDateTime createdAt;
    
    // Flexible metadata (different per notification type)
    private Map<String, Object> metadata;
    
    private String errorMessage;
    
    private Integer retryCount;
    
    @Indexed
    private LocalDateTime expiresAt; // TTL field
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (expiresAt == null) {
            // Set expiration to 30 days from now
            expiresAt = LocalDateTime.now().plusDays(30);
        }
        if (notificationId == null) {
            notificationId = UUID.randomUUID().toString();
        }
    }
}

// UserPreferences.java
@Document(collection = "user_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String userId;
    
    private Boolean emailEnabled;
    private Boolean smsEnabled;
    private Boolean pushEnabled;
    
    private Map<String, Boolean> notificationTypes;
    
    private LocalDateTime updatedAt;
    
    @PrePersist
    public void prePersist() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }
}

// Enums
public enum NotificationType {
    WELCOME_EMAIL,
    GROUP_INVITATION,
    DRAW_COMPLETED,
    WISHLIST_UPDATED,
    EXCHANGE_REMINDER,
    PASSWORD_RESET,
    GROUP_ARCHIVED
}

public enum NotificationChannel {
    EMAIL,
    SMS,
    PUSH
}

public enum DeliveryStatus {
    PENDING,
    SENT,
    FAILED
}
```

---

## 🚀 Running Locally

### Prerequisites
- Java 25
- Docker (Kafka + MongoDB)
- SMTP credentials (Gmail, SendGrid, Mailgun, etc.)

### Steps

1. **Start infrastructure:**
   ```bash
   docker-compose up -d mongodb kafka zookeeper
   ```

2. **Verify MongoDB is running:**
   ```bash
   docker ps | grep mongodb
   
   # Connect to MongoDB shell
   docker exec -it mongodb mongosh
   ```

3. **Configure SMTP credentials:**
   
   Create `application-local.yml` or use environment variables:
   
   ```yaml
   spring:
     mail:
       host: smtp.gmail.com
       port: 587
       username: ${SMTP_USERNAME}
       password: ${SMTP_APP_PASSWORD}
       properties:
         mail:
           smtp:
             auth: true
             starttls:
               enable: true
   ```
   
   **For Gmail:**
   - Use App Password (not regular password)
   - Enable 2FA on Google Account
   - Generate App Password: https://myaccount.google.com/apppasswords

4. **Run service:**
   ```bash
   cd notification-service
   
   # Set SMTP credentials
   export SMTP_USERNAME=your-email@gmail.com
   export SMTP_APP_PASSWORD=your-app-password
   
   mvn spring-boot:run
   ```

5. **Verify MongoDB connection:**
   ```bash
   # In mongosh
   use notification_db
   
   # Show collections
   show collections
   
   # Find notifications
   db.notifications.find().limit(5).pretty()
   
   # Count by status
   db.notifications.aggregate([
     { $group: { _id: "$status", count: { $sum: 1 } } }
   ])
   ```

6. **Test notification by publishing event:**
   ```bash
   # Publish UserCreatedEvent to trigger welcome email
   docker exec -it kafka kafka-console-producer \
     --broker-list localhost:9092 \
     --topic user.events
   
   # Paste JSON:
   {
     "eventType": "UserCreatedEvent",
     "userId": "test-user-123",
     "email": "test@example.com",
     "name": "John Doe",
     "timestamp": 1707562800000
   }
   ```

7. **Check email was sent:**
   - Check your Gmail inbox
   - Check MongoDB for log entry:
   ```javascript
   db.notifications.find({ userId: "test-user-123" }).pretty()
   ```

---

## ⚙️ Configuration

`application.yml`:

```yaml
server:
  port: 8084  # Internal port (not exposed)

spring:
  application:
    name: notification-service

  # MongoDB Configuration
  data:
    mongodb:
      host: localhost
      port: 27017
      database: notification_db
      auto-index-creation: true

  # SMTP Configuration (Gmail example)
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${SMTP_USERNAME}
    password: ${SMTP_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
          connectiontimeout: 5000
          timeout: 5000
          writetimeout: 5000
    default-encoding: UTF-8

  # Kafka Configuration
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: notification-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
        spring.json.type.mapping: >
          UserCreatedEvent:com.secretsanta.common.events.UserCreatedEvent,
          DrawCompletedEvent:com.secretsanta.common.events.DrawCompletedEvent

  # Thymeleaf for email templates
  thymeleaf:
    prefix: classpath:/templates/email/
    suffix: .html
    mode: HTML
    encoding: UTF-8

# Notification Settings
notification:
  retry:
    max-attempts: 3
    backoff-multiplier: 2
    initial-delay: 60000  # 1 minute
  ttl:
    days: 30  # Auto-delete logs after 30 days

logging:
  level:
    com.secretsanta.notification: DEBUG
    org.springframework.mail: DEBUG
    org.springframework.kafka: INFO
    org.springframework.data.mongodb: DEBUG
```

---

## 📧 Email Templates

**Location:** `src/main/resources/templates/email/`

### welcome.html

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Welcome to Secret Santa!</title>
</head>
<body>
    <h1>Welcome, <span th:text="${userName}">User</span>!</h1>
    <p>Thank you for joining Secret Santa Platform.</p>
    <p>You can now create groups, join exchanges, and manage your wishlists.</p>
    <p>Happy gift giving!</p>
</body>
</html>
```

### draw-completed.html

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Your Secret Santa Assignment</title>
</head>
<body>
    <h1>🎅 Your Secret Santa Assignment</h1>
    <p>Hi <span th:text="${giverName}">Giver</span>,</p>
    <p>The draw for <strong th:text="${groupName}">Group</strong> has been completed!</p>
    <p>You are Secret Santa for: <strong th:text="${receiverName}">Receiver</strong></p>
    <p>Check their wishlist to find the perfect gift!</p>
    <p>Budget: <span th:text="${budgetMin}">50</span> - <span th:text="${budgetMax}">100</span> PLN</p>
    <p>Exchange Date: <span th:text="${exchangeDate}">2025-12-24</span></p>
</body>
</html>
```

---

## 🏗️ Architecture Notes

### Why MongoDB Instead of PostgreSQL?

**Comparison:**

| Aspect | PostgreSQL Approach | MongoDB Approach ✅ |
|--------|---------------------|---------------------|
| **Foreign Keys** | Would need FK to users, groups | Just string references |
| **Schema Changes** | Requires migrations for new notification types | Add fields dynamically |
| **Query Pattern** | Complex JOINs for notifications | Simple document lookups |
| **Write Throughput** | ~5K inserts/sec | ~50K inserts/sec |
| **Flexibility** | Fixed schema (email has X fields) | Flexible metadata per type |
| **TTL** | Manual cleanup job | Built-in TTL indexes |

**PostgreSQL Schema (Complex):**
```sql
-- Would require multiple tables
notifications (id, user_id, type, status, ...)
email_notifications (notification_id, subject, body, ...)
sms_notifications (notification_id, phone, message, ...)
push_notifications (notification_id, device_token, ...)
notification_metadata (notification_id, key, value) -- EAV anti-pattern
```

**MongoDB (Simple):**
```javascript
// Single document with flexible structure
{
  userId: "...",
  type: "...",
  status: "...",
  metadata: { /* any structure */ },
  emailDetails: { /* nested if email */ },
  smsDetails: { /* nested if sms */ }
}
```

### Portfolio Value

**This service demonstrates:**
1. ✅ **Polyglot Persistence** - Right database for right job
2. ✅ **NoSQL Expertise** - MongoDB schemas, indexes, TTL
3. ✅ **Document Modeling** - Flexible, self-contained documents
4. ✅ **Cross-Cutting Concerns** - Service that listens to ALL events
5. ✅ **Log Aggregation Pattern** - Centralized notification tracking

**On Interview:**
```
Recruiter: "Why MongoDB for notifications?"

You: "Notification Service has different requirements than transactional services:
      
      1. Zero foreign keys - userId/groupId are just references, no JOINs
      2. Flexible schema - Email metadata differs from SMS/Push
      3. High write throughput - Thousands of notifications per second
      4. TTL expiration - Auto-delete old logs (MongoDB native feature)
      5. Log aggregation - Append-only pattern (perfect for document DB)
      
      This demonstrates polyglot persistence - PostgreSQL for transactional
      domains (User, Group, Wishlist) and MongoDB for logs/events."

Recruiter: *impressed* 💼
```

---

## 📊 Technology Stack

- **Spring Boot**: 4.0.2
- **Java**: 25
- **Spring Data MongoDB**: Document database ORM
- **MongoDB**: 6.0+ (NoSQL database)
- **Spring Mail**: JavaMailSender (SMTP)
- **Apache Kafka**: Event streaming
- **Thymeleaf**: HTML email templates
- **Lombok**: Boilerplate reduction
- **Maven**: 3.9+

---

## 🔮 Future Enhancements

- [ ] **SMS Notifications** - Twilio integration
- [ ] **Push Notifications** - Firebase Cloud Messaging (FCM)
- [ ] **Slack/Discord Webhooks** - Team notifications
- [ ] **Rate Limiting** - Per-user quotas (prevent spam)
- [ ] **A/B Testing** - Test different email templates
- [ ] **Analytics Dashboard** - Delivery rates, open rates
- [ ] **Batch Notifications** - Group multiple events into digest emails
- [ ] **Scheduled Reminders** - Cron jobs for exchange date reminders
- [ ] **Multi-language Support** - i18n email templates

---

**Author:** Michał (mjaracz)  
**Role:** Pure Event-Driven Worker (Cross-Cutting Concern)  
**Domain:** Notification Delivery & Logging  
**Database:** MongoDB (NoSQL - Polyglot Persistence) ⭐  
**Pattern:** Log Aggregator + Event Consumer
