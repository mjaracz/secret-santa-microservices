package com.secretsanta.gateway.security;

import com.secretsanta.common.user.UserRole;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record AuthenticatedActor(
        String userId,
        Set<UserRole> roles
) {

    public static AuthenticatedActor from(Jwt jwt) {
        List<String> roleClaims = jwt.getClaimAsStringList("roles");
        EnumSet<UserRole> roles = EnumSet.noneOf(UserRole.class);
        if (roleClaims != null) {
            roleClaims.stream()
                    .map(UserRole::valueOf)
                    .forEach(roles::add);
        }
        return new AuthenticatedActor(jwt.getSubject(), Set.copyOf(roles));
    }
}
