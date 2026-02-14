package com.secretsanta.common.group.commands;

import com.secretsanta.common.BaseCommand;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class UpdateGroupCommand extends BaseCommand {

	@NotBlank(message = "Group ID is required")
	private String groupId;

	@Size(min = 2, max = 255, message = "Group name must be between 2 and 255 characters")
	private String name;

	@Size(max = 1000, message = "Description must be at most 1000 characters")
	private String description;

	@Min(value = 3, message = "A group must have at least 3 members")
	private int maxMembers;
}
