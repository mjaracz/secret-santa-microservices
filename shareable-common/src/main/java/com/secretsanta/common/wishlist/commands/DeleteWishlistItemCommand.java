package com.secretsanta.common.wishlist.commands;

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
public class DeleteWishlistItemCommand extends BaseCommand {

    @NotBlank(message = "Group ID is required")
    private String groupId;

    @NotBlank(message = "Wishlist item ID is required")
    private String itemId;
}
