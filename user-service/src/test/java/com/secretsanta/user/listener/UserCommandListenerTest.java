package com.secretsanta.user.listener;

import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.events.UserCreatedEvent;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import com.secretsanta.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCommandListenerTest {

        @Mock
        private KafkaTemplate<String, String> kafkaTemplate;

        @Mock
        private UserService userService;

        private ObjectMapper objectMapper;
        private KafkaServiceBus serviceBus;
        private UserCommandListener listener;

        @Captor
        private ArgumentCaptor<String> jsonCaptor;

        @BeforeEach
        void setUp() {
                objectMapper = new ObjectMapper();

                RetryTemplate retryTemplate = new RetryTemplate();
                retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
                retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3,
                                Map.of(Exception.class, true, IllegalArgumentException.class, false), true));

                serviceBus = new KafkaServiceBus(kafkaTemplate, objectMapper, retryTemplate);
                listener = new UserCommandListener(serviceBus, userService);
                ReflectionTestUtils.setField(listener, "userEventsTopic", "user.events");
        }

        @Test
        void handles_create_user_command_and_emits_event() throws Exception {
                CreateUserCommand command = CreateUserCommand.builder()
                                .email("santa@northpole.com")
                                .name("Santa Claus")
                                .password("reindeer123")
                                .build();
                command.initDefaults("CREATE_USER");

                UserCreatedEvent expectedEvent = UserCreatedEvent.builder()
                                .userId("user-uuid-123")
                                .email("santa@northpole.com")
                                .name("Santa Claus")
                                .build();
                expectedEvent.initDefaults("USER_CREATED");

                when(userService.createUser(any(CreateUserCommand.class))).thenReturn(expectedEvent);

                String json = objectMapper.writeValueAsString(command);

                // Simulate what @KafkaListener would do
                listener.listen(json);

                verify(userService).createUser(any(CreateUserCommand.class));
                verify(kafkaTemplate).send(eq("user.events"), eq("user-uuid-123"), jsonCaptor.capture());

                String publishedJson = jsonCaptor.getValue();
                assertThat(publishedJson).contains("USER_CREATED");
                assertThat(publishedJson).contains("santa@northpole.com");
                assertThat(publishedJson).contains("user-uuid-123");
        }

        @Test
        void business_rule_violation_is_not_retried() throws Exception {
                CreateUserCommand command = CreateUserCommand.builder()
                                .email("duplicate@test.com")
                                .name("Duplicate User")
                                .password("password123")
                                .build();
                command.initDefaults("CREATE_USER");

                when(userService.createUser(any(CreateUserCommand.class)))
                                .thenThrow(new IllegalArgumentException("Email already registered"));

                String json = objectMapper.writeValueAsString(command);

                // Should not throw — bus catches IllegalArgumentException
                listener.listen(json);

                verify(userService, times(1)).createUser(any(CreateUserCommand.class));
                verifyNoInteractions(kafkaTemplate);
        }
}
