package com.secretsanta.group.listener

import com.secretsanta.common.BaseCommand
import com.secretsanta.common.CommandFailedEvent
import com.secretsanta.common.group.commands.AddMemberCommand
import com.secretsanta.common.group.commands.CreateGroupCommand
import com.secretsanta.common.group.commands.DeleteGroupCommand
import com.secretsanta.common.group.commands.DrawNamesCommand
import com.secretsanta.common.group.commands.GetMyGroupsCommand
import com.secretsanta.common.group.commands.UpdateGroupCommand
import com.secretsanta.common.group.events.DrawCompletedEvent
import com.secretsanta.common.group.events.GroupCreatedEvent
import com.secretsanta.common.group.events.GroupDeletedEvent
import com.secretsanta.common.group.events.GroupUpdatedEvent
import com.secretsanta.common.group.events.MemberAddedEvent
import com.secretsanta.common.group.events.MyGroupsFetchedEvent
import com.secretsanta.group.service.DrawService
import com.secretsanta.group.service.GroupQueryService
import com.secretsanta.group.service.GroupService
import com.secretsanta.infrastructure.kafka.KafkaServiceBus
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class GroupCommandListener(
    private val serviceBus: KafkaServiceBus,
    private val groupService: GroupService,
    private val drawService: DrawService,
    private val groupQueryService: GroupQueryService,
    @param:Value("\${kafka.topics.group-events}")
    private val groupEventsTopic: String
) {
    init {
        serviceBus.registerCommandHandler(GetMyGroupsCommand::class.java, ::onGetMyGroups)
        serviceBus.registerCommandHandler(CreateGroupCommand::class.java, ::onCreateGroup)
        serviceBus.registerCommandHandler(UpdateGroupCommand::class.java, ::onUpdateGroup)
        serviceBus.registerCommandHandler(DeleteGroupCommand::class.java, ::onDeleteGroup)
        serviceBus.registerCommandHandler(AddMemberCommand::class.java, ::onAddMember)
        serviceBus.registerCommandHandler(DrawNamesCommand::class.java, ::onDrawNames)
    }

    @KafkaListener(
        topics = ["\${kafka.topics.group-commands}"],
        groupId = "\${spring.kafka.consumer.group-id}"
    )
    fun listen(message: String) {
        serviceBus.handleCommandMessage(message, this::emitFailure)
    }

    private fun onCreateGroup(command: CreateGroupCommand) {
        val event: GroupCreatedEvent = groupService.createGroup(command)
        event.setCorrelationId(command.getCommandId())
        serviceBus.emitEvent(groupEventsTopic, event.getGroupId(), event)
    }

    private fun onUpdateGroup(command: UpdateGroupCommand) {
        val event: GroupUpdatedEvent = groupService.updateGroup(command)
        event.setCorrelationId(command.getCommandId())
        serviceBus.emitEvent(groupEventsTopic, event.getGroupId(), event)
    }

    private fun onDeleteGroup(command: DeleteGroupCommand) {
        val event: GroupDeletedEvent = groupService.deleteGroup(command)
        event.setCorrelationId(command.getCommandId())
        serviceBus.emitEvent(groupEventsTopic, event.getGroupId(), event)
    }

    private fun onAddMember(command: AddMemberCommand) {
        val event: MemberAddedEvent = groupService.addMember(command)
        event.setCorrelationId(command.getCommandId())
        serviceBus.emitEvent(groupEventsTopic, event.getGroupId(), event)
    }

    private fun onDrawNames(command: DrawNamesCommand) {
        val event: DrawCompletedEvent = drawService.drawNames(command)
        event.setCorrelationId(command.getCommandId())
        serviceBus.emitEvent(groupEventsTopic, event.getGroupId(), event)
    }

    private fun onGetMyGroups(command: GetMyGroupsCommand) {
        val event: MyGroupsFetchedEvent = groupQueryService.getMyGroups(command)
        event.setCorrelationId(command.getCommandId())
        serviceBus.emitEvent(
            groupEventsTopic,
            event.getCorrelationId(),
            event
        )
    }

    private fun emitFailure(command: BaseCommand, reason: String) {
        val event = CommandFailedEvent.builder()
            .correlationId(command.getCommandId())
            .reason(reason)
            .originalCommandType(command.getCommandType())
            .build()
        event.initDefaults("COMMAND_FAILED")
        serviceBus.emitEvent(groupEventsTopic, command.getCommandId(), event)
    }
}
