package com.secretsanta.common.wishlist.commands;

import com.secretsanta.common.BaseCommand;

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
public class AddWishlistItemCommand extends BaseCommand {

    @NotBlank(message = "Group ID is required")
    private String groupId;

    @NotBlank(message = "Wishlist item title is required")
    @Size(max = 255, message = "Wishlist item title must be at most 255 characters")
    private String title;

    @Size(max = 2048, message = "Wishlist item description must be at most 2048 characters")
    private String description;

    @Size(max = 2048, message = "Wishlist item URL must be at most 2048 characters")
    private String url;
}
