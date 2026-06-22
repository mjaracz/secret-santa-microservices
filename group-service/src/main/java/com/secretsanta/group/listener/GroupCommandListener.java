package com.secretsanta.group.listener;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.BaseEvent;
import com.secretsanta.common.CommandFailedEvent;
import com.secretsanta.common.group.commands.AddMemberCommand;
import com.secretsanta.common.group.commands.CreateGroupCommand;
import com.secretsanta.common.group.commands.DeleteGroupCommand;
import com.secretsanta.common.group.commands.DrawNamesCommand;
import com.secretsanta.common.group.commands.UpdateGroupCommand;
import com.secretsanta.common.group.events.DrawCompletedEvent;
import com.secretsanta.common.group.events.GroupCreatedEvent;
import com.secretsanta.common.group.events.GroupDeletedEvent;
import com.secretsanta.common.group.events.GroupUpdatedEvent;
import com.secretsanta.common.group.events.MemberAddedEvent;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import com.secretsanta.group.service.DrawService;
import com.secretsanta.group.service.GroupService;
import com.secretsanta.group.exception.GroupCommandException;

import java.util.function.Function;
import java.util.function.Supplier;

@Component
public class GroupCommandListener {

    private final KafkaServiceBus serviceBus;
    private final GroupService groupService;
    private final DrawService drawService;

    @Value("${kafka.topics.group-events}")
    private String groupEventsTopic;

    public GroupCommandListener(KafkaServiceBus serviceBus, GroupService groupService, DrawService drawService) {
        this.serviceBus = serviceBus;
        this.groupService = groupService;
        this.drawService = drawService;
        serviceBus.registerCommandHandler(CreateGroupCommand.class, this::onCreateGroup);
        serviceBus.registerCommandHandler(UpdateGroupCommand.class, this::onUpdateGroup);
        serviceBus.registerCommandHandler(DeleteGroupCommand.class, this::onDeleteGroup);
        serviceBus.registerCommandHandler(AddMemberCommand.class, this::onAddMember);
        serviceBus.registerCommandHandler(DrawNamesCommand.class, this::onDrawNames);
    }

    @KafkaListener(
            topics = "${kafka.topics.group-commands}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void listen(String message) {
        serviceBus.handleCommandMessage(message, this::emitFailure);
    }

    private void onCreateGroup(CreateGroupCommand command) {
        handle(command, () -> groupService.createGroup(command), GroupCreatedEvent::getGroupId);
    }

    private void onUpdateGroup(UpdateGroupCommand command) {
        handle(command, () -> groupService.updateGroup(command), GroupUpdatedEvent::getGroupId);
    }

    private void onDeleteGroup(DeleteGroupCommand command) {
        handle(command, () -> groupService.deleteGroup(command), GroupDeletedEvent::getGroupId);
    }

    private void onAddMember(AddMemberCommand command) {
        handle(command, () -> groupService.addMember(command), MemberAddedEvent::getGroupId);
    }

    private void onDrawNames(DrawNamesCommand command) {
        handle(command, () -> drawService.drawNames(command), DrawCompletedEvent::getGroupId);
    }

    private <T extends BaseEvent> void handle(
            BaseCommand command,
            Supplier<T> handler,
            Function<T, String> keyExtractor
    ) {
        try {
            T event = handler.get();
            event.setCorrelationId(command.getCommandId());
            serviceBus.emitEvent(groupEventsTopic, keyExtractor.apply(event), event);
        } catch (GroupCommandException exception) {
            emitFailure(
                    command,
                    exception.getErrorCode(),
                    exception.getMessage()
            );
        }
    }

    private void emitFailure(BaseCommand command, String reason) {
        emitFailure(command, null, reason);
    }

    private void emitFailure(
            BaseCommand command,
            String errorCode,
            String reason
    ) {
        CommandFailedEvent failedEvent = CommandFailedEvent.builder()
                .correlationId(command.getCommandId())
                .errorCode(errorCode)
                .reason(reason)
                .originalCommandType(command.getCommandType())
                .build();
        failedEvent.initDefaults("COMMAND_FAILED");
        serviceBus.emitEvent(groupEventsTopic, command.getCommandId(), failedEvent);
    }
}
