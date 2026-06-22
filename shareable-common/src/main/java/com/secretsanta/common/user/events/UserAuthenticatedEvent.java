package com.secretsanta.common.user.events;

import com.secretsanta.common.BaseEvent;
import com.secretsanta.common.user.dto.AuthenticatedUserDto;
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
public class UserAuthenticatedEvent extends BaseEvent {

    private AuthenticatedUserDto user;
    private long refreshTokenExpiresAt;
}
