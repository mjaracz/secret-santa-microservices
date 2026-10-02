package com.secretsanta.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class PasswordEncryptor {

    private static final OAEPParameterSpec OAEP_PARAMETERS = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
    );

    private final PublicKey publicKey;

    public PasswordEncryptor(
            @Value("${security.credentials.public-key-base64}") String publicKeyBase64) {
        try {
            byte[] encodedKey = Base64.getDecoder().decode(publicKeyBase64);
            this.publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encodedKey));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "USER_AUTH_PUBLIC_KEY_BASE64 must contain a valid X.509 RSA public key",
                    exception
            );
        }
    }

    public String encrypt(String password) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_PARAMETERS);
            byte[] encrypted = cipher.doFinal(
                    password.getBytes(StandardCharsets.UTF_8)
            );
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Could not encrypt authentication credentials",
                    exception
            );
        }
    }
}
