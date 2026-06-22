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
public class RefreshSessionCommand extends BaseCommand {

    @NotBlank(message = "Current refresh token hash is required")
    @Pattern(regexp = "[0-9a-f]{64}", message = "Current refresh token hash must be a SHA-256 hex value")
    private String currentTokenHash;

    @NotBlank(message = "Replacement refresh token hash is required")
    @Pattern(regexp = "[0-9a-f]{64}", message = "Replacement refresh token hash must be a SHA-256 hex value")
    private String replacementTokenHash;
}
