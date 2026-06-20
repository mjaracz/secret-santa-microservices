package com.secretsanta.gateway.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupRequest {
    @Size(min = 2, max = 255, message = "Group name must be between 2 and 255 characters")
    private String name;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @Min(value = 3, message = "A group must have at least 3 members")
    private Integer maxMembers;

    @AssertTrue(message = "At least one group field must be provided")
    public boolean isAnyFieldProvided() {
        return name != null || description != null || maxMembers != null;
    }
}
