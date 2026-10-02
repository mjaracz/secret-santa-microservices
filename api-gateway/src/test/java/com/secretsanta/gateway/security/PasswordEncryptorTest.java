package com.secretsanta.gateway.security;

import org.junit.jupiter.api.BeforeAll;
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

class PasswordEncryptorTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final OAEPParameterSpec OAEP_PARAMETERS = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
    );

    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void encryptsPasswordSoItCanOnlyBeRecoveredWithThePrivateKey() throws Exception {
        String publicKey = Base64.getEncoder().encodeToString(
                keyPair.getPublic().getEncoded()
        );
        PasswordEncryptor passwordEncryptor = new PasswordEncryptor(publicKey);

        String ciphertext = passwordEncryptor.encrypt(PASSWORD);

        assertThat(ciphertext).doesNotContain(PASSWORD);

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), OAEP_PARAMETERS);
        byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
        assertThat(new String(plaintext, StandardCharsets.UTF_8)).isEqualTo(PASSWORD);
    }

    @Test
    void failsFastWhenPublicKeyConfigurationIsInvalid() {
        assertThatThrownBy(() -> new PasswordEncryptor("invalid-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("USER_AUTH_PUBLIC_KEY_BASE64");
    }
}
