package com.secretsanta.common;

import com.secretsanta.common.user.UserAccountStatus;
import com.secretsanta.common.user.UserRole;
import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.dto.AuthenticatedUserDto;
import com.secretsanta.common.user.events.EmailVerificationRequestedEvent;
import com.secretsanta.common.user.events.UserAuthenticatedEvent;
import com.secretsanta.common.user.events.UserCreatedEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class UserRegistrationContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void serializesAndDeserializesCreateUserCommand() throws Exception {
		CreateUserCommand command = CreateUserCommand.builder()
			.email("santa@example.com")
			.name("Santa Claus")
			.password("very-secure-password")
			.build();

		command.initDefaults("CREATE_USER");

		String json = objectMapper.writeValueAsString(command);

		BaseCommand deserialized = objectMapper.readValue(
			json,
			BaseCommand.class
		);

		assertThat(deserialized)
			.isInstanceOf(CreateUserCommand.class);

		CreateUserCommand restoredCommand =
			(CreateUserCommand) deserialized;

		assertThat(restoredCommand.getCommandType())
			.isEqualTo("CREATE_USER");

		assertThat(restoredCommand.getCommandId())
			.isEqualTo(command.getCommandId());

		assertThat(restoredCommand.getEmail())
			.isEqualTo("santa@example.com");

		assertThat(restoredCommand.getName())
			.isEqualTo("Santa Claus");

		assertThat(restoredCommand.getPassword())
			.isEqualTo("very-secure-password");
	}

	@Test
	public void recognizesCreateUserSubtypeFromCommandType()
		throws Exception {

		String json = """
                {
                  "commandType": "CREATE_USER",
                  "commandId": "command-123",
                  "timestamp": 1710000000000,
                  "email": "santa@example.com",
                  "name": "Santa Claus",
                  "password": "very-secure-password"
                }
                """;

		BaseCommand command = objectMapper.readValue(
			json,
			BaseCommand.class
		);

		assertThat(command)
			.isInstanceOf(CreateUserCommand.class);

		CreateUserCommand createUserCommand =
			(CreateUserCommand) command;

		assertThat(createUserCommand.getEmail())
			.isEqualTo("santa@example.com");

		assertThat(createUserCommand.getCommandType())
			.isEqualTo("CREATE_USER");
	}

	@Test
	public void serializesUserCreatedEventWithPendingStatus()
		throws Exception {

		UserCreatedEvent event = UserCreatedEvent.builder()
			.userId("user-123")
			.email("santa@example.com")
			.name("Santa Claus")
			.status(UserAccountStatus.PENDING_VERIFICATION)
			.correlationId("command-123")
			.build();

		event.initDefaults("USER_CREATED");

		String json = objectMapper.writeValueAsString(event);

		assertThat(json)
			.contains("\"eventType\":\"USER_CREATED\"")
			.contains("\"status\":\"PENDING_VERIFICATION\"")
			.doesNotContain(
				"password",
				"passwordHash",
				"verificationToken"
			);

		BaseEvent deserialized = objectMapper.readValue(
			json,
			BaseEvent.class
		);

		assertThat(deserialized)
			.isInstanceOf(UserCreatedEvent.class);

		UserCreatedEvent restoredEvent =
			(UserCreatedEvent) deserialized;

		assertThat(restoredEvent.getStatus())
			.isEqualTo(
				UserAccountStatus.PENDING_VERIFICATION
			);

		assertThat(restoredEvent.getCorrelationId())
			.isEqualTo("command-123");
	}

	@Test
	public void serializesCommandFailedEventWithErrorCode()
		throws Exception {

		CommandFailedEvent event = CommandFailedEvent.builder()
			.correlationId("command-123")
			.reason("Email is already registered")
			.originalCommandType("CREATE_USER")
			.errorCode("USER_EMAIL_ALREADY_EXISTS")
			.build();

		event.initDefaults("COMMAND_FAILED");

		String json = objectMapper.writeValueAsString(event);

		assertThat(json)
			.contains(
				"\"errorCode\":\"USER_EMAIL_ALREADY_EXISTS\""
			);

		BaseEvent deserialized = objectMapper.readValue(
			json,
			BaseEvent.class
		);

		assertThat(deserialized)
			.isInstanceOf(CommandFailedEvent.class);

		CommandFailedEvent restoredEvent =
			(CommandFailedEvent) deserialized;

		assertThat(restoredEvent.getErrorCode())
			.isEqualTo("USER_EMAIL_ALREADY_EXISTS");

		assertThat(restoredEvent.getReason())
			.isEqualTo("Email is already registered");
	}


	@Test
	public void acceptsLegacyCommandFailedEventWithoutErrorCode()
		throws Exception {

		String legacyJson = """
                {
                  "eventType": "COMMAND_FAILED",
                  "eventId": "event-123",
                  "timestamp": 1710000000000,
                  "correlationId": "command-123",
                  "reason": "Email is already registered",
                  "originalCommandType": "CREATE_USER"
                }
                """;

		BaseEvent deserialized = objectMapper.readValue(
			legacyJson,
			BaseEvent.class
		);

		assertThat(deserialized)
			.isInstanceOf(CommandFailedEvent.class);

		CommandFailedEvent restoredEvent =
			(CommandFailedEvent) deserialized;

		assertThat(restoredEvent.getErrorCode()).isNull();

		assertThat(restoredEvent.getReason())
			.isEqualTo("Email is already registered");
	}

	@Test
	void recognizesAuthenticationCommandWithoutRawRefreshToken()
		throws Exception {

		AuthenticateUserCommand command = AuthenticateUserCommand.builder()
			.email("user@example.com")
			.password("correct-horse-battery-staple")
			.refreshTokenHash("a".repeat(64))
			.build();
		command.initDefaults("AUTHENTICATE_USER");

		String json = objectMapper.writeValueAsString(command);
		BaseCommand restored = objectMapper.readValue(
			json,
			BaseCommand.class
		);

		assertThat(restored)
			.isInstanceOf(AuthenticateUserCommand.class);
		assertThat(json).contains("\"refreshTokenHash\"");
		assertThat(json).doesNotContain("refresh_token");
	}

	@Test
	void serializesAuthenticatedUserRole() throws Exception {
		UserAuthenticatedEvent event = UserAuthenticatedEvent.builder()
			.user(AuthenticatedUserDto.builder()
				.userId("user-123")
				.email("user@example.com")
				.name("User")
				.role(UserRole.USER)
				.build())
			.refreshTokenExpiresAt(1_800_000_000_000L)
			.build();
		event.initDefaults("USER_AUTHENTICATED");

		BaseEvent restored = objectMapper.readValue(
			objectMapper.writeValueAsString(event),
			BaseEvent.class
		);

		assertThat(restored)
			.isInstanceOf(UserAuthenticatedEvent.class);
		assertThat(
			((UserAuthenticatedEvent) restored).getUser().getRole()
		).isEqualTo(UserRole.USER);
	}

	@Test
	void keepsVerificationTokenOutOfUserCreatedEvent()
		throws Exception {

		UserCreatedEvent created = UserCreatedEvent.builder()
			.userId("user-123")
			.email("user@example.com")
			.name("User")
			.build();
		created.initDefaults("USER_CREATED");
		EmailVerificationRequestedEvent verification =
			EmailVerificationRequestedEvent.builder()
				.userId("user-123")
				.verificationToken("raw-verification-token")
				.build();
		verification.initDefaults("EMAIL_VERIFICATION_REQUESTED");

		assertThat(objectMapper.writeValueAsString(created))
			.doesNotContain("raw-verification-token")
			.doesNotContain("verificationToken");
		assertThat(objectMapper.writeValueAsString(verification))
			.contains("raw-verification-token");
	}
}
