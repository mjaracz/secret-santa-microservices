package com.secretsanta.user.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordDecryptorTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final OAEPParameterSpec OAEP_PARAMETERS = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
    );

    private static KeyPair keyPair;

    private PasswordDecryptor passwordDecryptor;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        keyPair = generator.generateKeyPair();
    }

    @BeforeEach
    void createDecryptor() {
        String privateKey = Base64.getEncoder().encodeToString(
                keyPair.getPrivate().getEncoded()
        );
        passwordDecryptor = new PasswordDecryptor(privateKey);
    }

    @Test
    void decryptsPasswordEncryptedWithMatchingPublicKey() throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(), OAEP_PARAMETERS);
        String ciphertext = Base64.getEncoder().encodeToString(
                cipher.doFinal(PASSWORD.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(passwordDecryptor.decrypt(ciphertext)).isEqualTo(PASSWORD);
    }

    @Test
    void rejectsMalformedCiphertext() {
        assertThatThrownBy(() -> passwordDecryptor.decrypt("not-base64"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsFastWhenPrivateKeyConfigurationIsInvalid() {
        assertThatThrownBy(() -> new PasswordDecryptor("invalid-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("USER_AUTH_PRIVATE_KEY_BASE64");
    }
}
