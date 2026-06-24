package com.secretsanta.wishlist.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

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
import org.springframework.test.util.ReflectionTestUtils;

import com.secretsanta.common.BaseEvent;
import com.secretsanta.common.CommandFailedEvent;
import com.secretsanta.common.wishlist.commands.AddWishlistItemCommand;
import com.secretsanta.common.wishlist.commands.GetReceiverWishlistCommand;
import com.secretsanta.common.wishlist.dto.WishlistItemDto;
import com.secretsanta.common.wishlist.events.WishlistItemAddedEvent;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import com.secretsanta.wishlist.exception.WishlistException;
import com.secretsanta.wishlist.service.WishlistService;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WishlistCommandListenerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private WishlistService wishlistService;

    @Captor
    private ArgumentCaptor<String> jsonCaptor;

    private ObjectMapper objectMapper;
    private WishlistCommandListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(
                3,
                Map.of(Exception.class, true, IllegalArgumentException.class, false),
                true
        ));

        KafkaServiceBus serviceBus = new KafkaServiceBus(
                kafkaTemplate,
                objectMapper,
                retryTemplate
        );

        listener = new WishlistCommandListener(serviceBus, wishlistService);
        ReflectionTestUtils.setField(listener, "wishlistEventsTopic", "wishlist.events");
    }

    @Test
    void emitsWishlistEventWithCorrelationId() throws Exception {
        AddWishlistItemCommand command = addCommand();
        WishlistItemAddedEvent serviceEvent = WishlistItemAddedEvent.builder()
                .item(WishlistItemDto.builder()
                        .id("item-001")
                        .groupId("11111111-1111-1111-1111-111111111111")
                        .ownerUserId("giver-001")
                        .title("Headphones")
                        .build())
                .build();
        serviceEvent.initDefaults("WISHLIST_ITEM_ADDED");
        when(wishlistService.addItem(any(AddWishlistItemCommand.class))).thenReturn(serviceEvent);

        listener.listen(objectMapper.writeValueAsString(command));

        verify(kafkaTemplate).send(eq("wishlist.events"), eq("item-001"), jsonCaptor.capture());
        BaseEvent publishedEvent = readEvent(jsonCaptor.getValue());
        assertThat(publishedEvent).isInstanceOf(WishlistItemAddedEvent.class);
        assertThat(publishedEvent.getCorrelationId()).isEqualTo(command.getCommandId());
    }

    @Test
    void emitsTypedFailureForWishlistException() throws Exception {
        GetReceiverWishlistCommand command = GetReceiverWishlistCommand.builder()
                .groupId("11111111-1111-1111-1111-111111111111")
                .actorId("giver-001")
                .build();
        command.initDefaults("GET_RECEIVER_WISHLIST");
        when(wishlistService.getReceiverWishlist(any(GetReceiverWishlistCommand.class)))
                .thenThrow(WishlistException.conflict(
                        "WISHLIST_DRAW_NOT_COMPLETED",
                        "Receiver wishlist is available only after draw"
                ));

        listener.listen(objectMapper.writeValueAsString(command));

        verify(kafkaTemplate).send(eq("wishlist.events"), eq(command.getCommandId()), jsonCaptor.capture());
        BaseEvent publishedEvent = readEvent(jsonCaptor.getValue());
        assertThat(publishedEvent).isInstanceOf(CommandFailedEvent.class);
        CommandFailedEvent failedEvent = (CommandFailedEvent) publishedEvent;
        assertThat(failedEvent.getCorrelationId()).isEqualTo(command.getCommandId());
        assertThat(failedEvent.getErrorCode()).isEqualTo("WISHLIST_DRAW_NOT_COMPLETED");
        assertThat(failedEvent.getOriginalCommandType()).isEqualTo("GET_RECEIVER_WISHLIST");
    }

    private AddWishlistItemCommand addCommand() {
        AddWishlistItemCommand command = AddWishlistItemCommand.builder()
                .groupId("11111111-1111-1111-1111-111111111111")
                .title("Headphones")
                .actorId("giver-001")
                .build();
        command.initDefaults("ADD_WISHLIST_ITEM");
        return command;
    }

    private BaseEvent readEvent(String json) {
        try {
            return objectMapper.readValue(json, BaseEvent.class);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
