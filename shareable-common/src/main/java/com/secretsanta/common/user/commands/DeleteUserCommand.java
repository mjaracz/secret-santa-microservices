package com.secretsanta.common.user.commands;

import com.secretsanta.common.BaseCommand;

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
public class DeleteUserCommand extends BaseCommand {

	@NotBlank(message = "User ID is required")
	private String userId;
}
