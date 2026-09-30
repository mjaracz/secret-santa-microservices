package com.secretsanta.user.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Component
public class PasswordDecryptor {

    private static final OAEPParameterSpec OAEP_PARAMETERS = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
    );

    private final PrivateKey privateKey;

    public PasswordDecryptor(
            @Value("${security.credentials.private-key-base64}") String privateKeyBase64) {
        try {
            byte[] encodedKey = Base64.getDecoder().decode(privateKeyBase64);
            try {
                this.privateKey = KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(encodedKey));
            } finally {
                java.util.Arrays.fill(encodedKey, (byte) 0);
            }
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "USER_AUTH_PRIVATE_KEY_BASE64 must contain a valid PKCS#8 RSA private key",
                    exception
            );
        }
    }

    public String decrypt(String encryptedPassword) {
        try {
            byte[] ciphertext = Base64.getDecoder().decode(encryptedPassword);
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_PARAMETERS);
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Authentication credentials could not be decrypted",
                    exception
            );
        }
    }
}
