package com.secretsanta.common.user.commands;

import com.secretsanta.common.BaseCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class VerifyEmailCommand extends BaseCommand {

    @NotBlank(message = "Verification token hash is required")
    @Pattern(regexp = "[0-9a-f]{64}", message = "Verification token hash must be a SHA-256 hex value")
    private String tokenHash;
}
