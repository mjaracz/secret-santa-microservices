package com.secretsanta.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
class JwtKeyConfig {

    @Bean
    JwtKeyMaterial jwtKeyMaterial(
            @Value("${security.jwt.private-key-base64:}") String privateKeyBase64,
            @Value("${security.jwt.public-key-base64:}") String publicKeyBase64,
            Environment environment
    ) {
        if (!privateKeyBase64.isBlank() && !publicKeyBase64.isBlank()) {
            return parseKeyMaterial(publicKeyBase64, privateKeyBase64);
        }

        if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
            return generateDevelopmentKeyMaterial();
        }

        throw new IllegalStateException(
                "JWT_PRIVATE_KEY_BASE64 and JWT_PUBLIC_KEY_BASE64 are required outside local/test profiles"
        );
    }

    private JwtKeyMaterial parseKeyMaterial(
            String publicKeyBase64,
            String privateKeyBase64
    ) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new X509EncodedKeySpec(
                            Base64.getDecoder().decode(publicKeyBase64)
                    )
            );
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(
                            Base64.getDecoder().decode(privateKeyBase64)
                    )
            );
            return new JwtKeyMaterial(publicKey, privateKey);
        } catch (Exception exception) {
            throw new IllegalStateException("Configured RSA key material is invalid", exception);
        }
    }

    private JwtKeyMaterial generateDevelopmentKeyMaterial() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new JwtKeyMaterial(
                    (RSAPublicKey) keyPair.getPublic(),
                    (RSAPrivateKey) keyPair.getPrivate()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not generate local RSA key pair", exception);
        }
    }
}
