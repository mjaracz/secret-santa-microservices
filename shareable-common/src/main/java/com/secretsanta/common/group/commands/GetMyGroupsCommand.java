package com.secretsanta.common.group.commands;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.group.events.MyGroupsFetchedEvent;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GetMyGroupsCommand extends BaseCommand {
	@NotBlank(message = "Requester ID is required")
	private String requestedBy;


}
