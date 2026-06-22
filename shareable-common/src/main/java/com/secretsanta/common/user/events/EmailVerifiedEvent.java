package com.secretsanta.common.user.events;

import com.secretsanta.common.BaseEvent;
import com.secretsanta.common.user.UserAccountStatus;
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
public class EmailVerifiedEvent extends BaseEvent {

    private String userId;
    private UserAccountStatus status;
}
