package com.svp.tracker.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.svp.tracker.config.BankingPlaidProperties;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PlaidAccessTokenCryptoTest {

    private static BankingPlaidProperties propsWithKey(String key) {
        return new BankingPlaidProperties(false, "", "", "sandbox", "plaid", key);
    }

    @Test
    void disabledWhenKeyBlankPassesThrough() {
        PlaidAccessTokenCrypto crypto = new PlaidAccessTokenCrypto(propsWithKey(""));
        assertThat(crypto.isEnabled()).isFalse();
        assertThat(crypto.seal("access-sandbox-abc")).isEqualTo("access-sandbox-abc");
        assertThat(crypto.open("access-sandbox-abc")).isEqualTo("access-sandbox-abc");
    }

    @Test
    void roundTripWithBase64RawKey() {
        byte[] raw = new byte[32];
        Arrays.fill(raw, (byte) 7);
        String b64 = Base64.getEncoder().encodeToString(raw);
        PlaidAccessTokenCrypto crypto = new PlaidAccessTokenCrypto(propsWithKey(b64));
        assertThat(crypto.isEnabled()).isTrue();
        String plain = "access-sandbox-test-token-value";
        String sealed = crypto.seal(plain);
        assertThat(sealed).startsWith(PlaidAccessTokenCrypto.SEAL_PREFIX);
        assertThat(sealed).isNotEqualTo(plain);
        assertThat(crypto.open(sealed)).isEqualTo(plain);
    }

    @Test
    void roundTripWithPassphraseDerivedKey() {
        PlaidAccessTokenCrypto crypto = new PlaidAccessTokenCrypto(propsWithKey("a-long-test-passphrase-for-sha256"));
        assertThat(crypto.isEnabled()).isTrue();
        String plain = "access-development-xyz";
        String sealed = crypto.seal(plain);
        assertThat(crypto.open(sealed)).isEqualTo(plain);
    }

    @Test
    void sealIsIdempotentWhenAlreadySealed() {
        PlaidAccessTokenCrypto crypto = new PlaidAccessTokenCrypto(propsWithKey("passphrase-one-two-three-four-five"));
        String plain = "token";
        String once = crypto.seal(plain);
        assertThat(crypto.seal(once)).isEqualTo(once);
    }

    @Test
    void openPlaintextWhenEnabledReturnsUnchanged() {
        PlaidAccessTokenCrypto crypto = new PlaidAccessTokenCrypto(propsWithKey("another-passphrase-here"));
        String legacy = "access-sandbox-plain";
        assertThat(crypto.open(legacy)).isEqualTo(legacy);
    }

    @Test
    void decryptWithWrongKeyFails() {
        byte[] k1 = new byte[32];
        byte[] k2 = new byte[32];
        Arrays.fill(k1, (byte) 1);
        Arrays.fill(k2, (byte) 2);
        PlaidAccessTokenCrypto enc = new PlaidAccessTokenCrypto(propsWithKey(Base64.getEncoder().encodeToString(k1)));
        PlaidAccessTokenCrypto dec = new PlaidAccessTokenCrypto(propsWithKey(Base64.getEncoder().encodeToString(k2)));
        String sealed = enc.seal("secret-token");
        assertThatThrownBy(() -> dec.open(sealed)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidBase64PayloadThrows() {
        PlaidAccessTokenCrypto crypto = new PlaidAccessTokenCrypto(propsWithKey("x".repeat(40)));
        assertThatThrownBy(() -> crypto.open(PlaidAccessTokenCrypto.SEAL_PREFIX + "not-valid-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void non32ByteBase64FallsBackToSha256Passphrase() {
        // 16 decoded bytes — not 32, so treat whole string as passphrase
        String shortB64 = Base64.getEncoder().encodeToString("sixteen-byte-pad".getBytes(StandardCharsets.UTF_8));
        assertThat(shortB64).hasSizeGreaterThan(10);
        PlaidAccessTokenCrypto crypto = new PlaidAccessTokenCrypto(propsWithKey(shortB64));
        assertThat(crypto.isEnabled()).isTrue();
        String sealed = crypto.seal("t");
        assertThat(crypto.open(sealed)).isEqualTo("t");
    }
}
