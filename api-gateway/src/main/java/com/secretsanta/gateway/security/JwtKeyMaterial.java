package com.secretsanta.gateway.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public record JwtKeyMaterial(
        RSAPublicKey publicKey,
        RSAPrivateKey privateKey
) {
}
