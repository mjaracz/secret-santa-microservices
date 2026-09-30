package com.secretsanta.gateway.service;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.common.user.events.UserAuthenticatedEvent;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.LoginRequest;
import com.secretsanta.gateway.dto.LoginResponse;
import com.secretsanta.gateway.security.JwtTokenService;
import com.secretsanta.gateway.security.PasswordEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationGatewayServiceTest {

    private static final String USER_COMMANDS_TOPIC = "user.commands";
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PLAINTEXT_PASSWORD = "correct-horse-battery-staple";
    private static final String ENCRYPTED_PASSWORD = "encrypted-value";

    @Mock
    private CommandDispatcher dispatcher;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private PasswordEncryptor passwordEncryptor;

    private AuthenticationGatewayService authenticationGatewayService;

    @BeforeEach
    void setUp() {
        authenticationGatewayService = new AuthenticationGatewayService(
                dispatcher,
                jwtTokenService,
                passwordEncryptor
        );
        ReflectionTestUtils.setField(
                authenticationGatewayService,
                "userCommandsTopic",
                USER_COMMANDS_TOPIC
        );
    }

    @Test
    void sendsEncryptedCredentialsAndReturnsBearerTokenAfterAuthentication() {
        when(passwordEncryptor.encrypt(PLAINTEXT_PASSWORD))
                .thenReturn(ENCRYPTED_PASSWORD);
        UserAuthenticatedEvent event = UserAuthenticatedEvent.builder()
                .userId(USER_ID)
                .build();
        event.initDefaults("USER_AUTHENTICATED");
        when(dispatcher.send(
                eq(USER_COMMANDS_TOPIC),
                any(AuthenticateUserCommand.class),
                eq("AUTHENTICATE_USER")
        )).thenReturn(Mono.just(CommandResponse.success("command-1", event)));
        when(jwtTokenService.issueFor(USER_ID))
                .thenReturn(new JwtTokenService.IssuedToken("signed-token", 900));

        ResponseEntity<LoginResponse> response = authenticationGatewayService
                .login(new LoginRequest("user@example.com", PLAINTEXT_PASSWORD))
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new LoginResponse(
                "signed-token",
                "Bearer",
                900,
                USER_ID
        ));

        ArgumentCaptor<BaseCommand> commandCaptor = ArgumentCaptor.forClass(BaseCommand.class);
        verify(dispatcher).send(
                eq(USER_COMMANDS_TOPIC),
                commandCaptor.capture(),
                eq("AUTHENTICATE_USER")
        );
        AuthenticateUserCommand command = (AuthenticateUserCommand) commandCaptor.getValue();
        assertThat(command.getEncryptedPassword()).isEqualTo(ENCRYPTED_PASSWORD);
        assertThat(command.getEncryptedPassword()).doesNotContain(PLAINTEXT_PASSWORD);
        assertThat(command.getCommandType()).isEqualTo("AUTHENTICATE_USER");
        verify(jwtTokenService).issueFor(USER_ID);
    }

    @Test
    void returnsUnauthorizedForInvalidCredentialsWithoutIssuingToken() {
        when(passwordEncryptor.encrypt(PLAINTEXT_PASSWORD))
                .thenReturn(ENCRYPTED_PASSWORD);
        when(dispatcher.send(
                eq(USER_COMMANDS_TOPIC),
                any(AuthenticateUserCommand.class),
                eq("AUTHENTICATE_USER")
        )).thenReturn(Mono.just(CommandResponse.failure(
                "command-1",
                "AUTH_INVALID_CREDENTIALS",
                "Invalid email or password",
                "AUTHENTICATE_USER"
        )));

        ResponseEntity<LoginResponse> response = authenticationGatewayService
                .login(new LoginRequest("user@example.com", PLAINTEXT_PASSWORD))
                .block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNull();
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void returnsGatewayTimeoutWhenAuthenticationReplyTimesOut() {
        when(passwordEncryptor.encrypt(PLAINTEXT_PASSWORD))
                .thenReturn(ENCRYPTED_PASSWORD);
        when(dispatcher.send(
                eq(USER_COMMANDS_TOPIC),
                any(AuthenticateUserCommand.class),
                eq("AUTHENTICATE_USER")
        )).thenReturn(Mono.just(CommandResponse.failure(
                "command-1",
                CommandResponse.REQUEST_TIMEOUT_ERROR_CODE,
                "Request timed out",
                "AUTHENTICATE_USER"
        )));

        ResponseEntity<LoginResponse> response = authenticationGatewayService
                .login(new LoginRequest("user@example.com", PLAINTEXT_PASSWORD))
                .block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void rejectsPasswordLongerThanBcryptLimitBeforeEncryptionOrKafka() {
        String oversizedPassword = "a".repeat(73);

        ResponseEntity<LoginResponse> response = authenticationGatewayService
                .login(new LoginRequest("user@example.com", oversizedPassword))
                .block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(passwordEncryptor, dispatcher, jwtTokenService);
    }

    @Test
    void mapsUnexpectedDispatcherErrorToServiceUnavailable() {
        when(passwordEncryptor.encrypt(PLAINTEXT_PASSWORD))
                .thenReturn(ENCRYPTED_PASSWORD);
        when(dispatcher.send(
                eq(USER_COMMANDS_TOPIC),
                any(AuthenticateUserCommand.class),
                eq("AUTHENTICATE_USER")
        )).thenReturn(Mono.error(new IllegalStateException("broker unavailable")));

        ResponseEntity<LoginResponse> response = authenticationGatewayService
                .login(new LoginRequest("user@example.com", PLAINTEXT_PASSWORD))
                .block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(jwtTokenService, never()).issueFor(any());
    }
}
