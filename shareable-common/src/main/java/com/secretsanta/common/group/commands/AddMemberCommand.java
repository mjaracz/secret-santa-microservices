package com.secretsanta.common.group.commands;

import com.secretsanta.common.BaseCommand;

import jakarta.validation.constraints.Email;
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
public class AddMemberCommand extends BaseCommand {

	@NotBlank(message = "Group ID is required")
	private String groupId;

	@NotBlank(message = "User ID is required")
	private String userId;

	@Email(message = "Email should be valid")
	private String userEmail;

	@NotBlank(message = "Name is required")
	@Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
	private String userName;

	private String role;
}
