package com.secretsanta.gateway.service;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.user.UserRole;
import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.common.user.dto.AuthenticatedUserDto;
import com.secretsanta.common.user.events.UserAuthenticatedEvent;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.SignInRequest;
import com.secretsanta.gateway.security.AccessToken;
import com.secretsanta.gateway.security.GatewayTokenService;
import com.secretsanta.gateway.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthGatewayServiceTest {

    @Mock
    private CommandDispatcher dispatcher;

    @Mock
    private GatewayTokenService tokenService;

    @Mock
    private JwtService jwtService;

    private AuthGatewayService service;

    @BeforeEach
    void setUp() {
        service = new AuthGatewayService(dispatcher, tokenService, jwtService);
        ReflectionTestUtils.setField(service, "authCommandsTopic", "auth.commands");
    }

    @Test
    void sendsOnlyRefreshTokenHashThroughKafka() {
        when(tokenService.generate()).thenReturn("raw-refresh-token");
        when(tokenService.hash("raw-refresh-token"))
                .thenReturn("a".repeat(64));
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
        when(dispatcher.send(
                eq("auth.commands"),
                any(BaseCommand.class),
                eq("AUTHENTICATE_USER")
        )).thenReturn(Mono.just(CommandResponse.success("command-123", event)));
        when(jwtService.issue(event.getUser()))
                .thenReturn(new AccessToken("access-token", 900));

        AuthenticationResult result = service.signIn(
                new SignInRequest(
                        "user@example.com",
                        "correct-horse-battery-staple"
                )
        ).block();

        ArgumentCaptor<BaseCommand> commandCaptor =
                ArgumentCaptor.forClass(BaseCommand.class);
        verify(dispatcher).send(
                eq("auth.commands"),
                commandCaptor.capture(),
                eq("AUTHENTICATE_USER")
        );
        AuthenticateUserCommand command =
                (AuthenticateUserCommand) commandCaptor.getValue();
        assertThat(command.getRefreshTokenHash())
                .isEqualTo("a".repeat(64));
        assertThat(command.getRefreshTokenHash())
                .isNotEqualTo("raw-refresh-token");
        assertThat(result.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(result.authenticationResponse().getAccessToken())
                .isEqualTo("access-token");
    }
}
