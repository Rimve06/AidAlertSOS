package com.aidalert.sos.net;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * Privacy helpers (plain Java so they can be unit tested on a PC):
 *  - hashed user ids and password hashing (PBKDF2),
 *  - hybrid encryption (AES-GCM for the data, RSA-OAEP for the AES key) so phones that
 *    relay an SOS packet offline can carry it but can never read it.
 */
public final class Crypto {
    private Crypto() {}

    private static final SecureRandom RNG = new SecureRandom();
    private static final int PBKDF2_ITERATIONS = 60000;

    private static OAEPParameterSpec oaep() {
        return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
    }

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    private static byte[] unb64(String s) {
        return Base64.getDecoder().decode(s);
    }

    // ---------------------------------------------------------------- identity

    /** Digits only; Bangladesh numbers 01XXXXXXXXX and +8801XXXXXXXXX become the same 8801XXXXXXXXX. */
    public static String normalizePhone(String raw) {
        if (raw == null) return "";
        String d = raw.replaceAll("[^0-9]", "");
        if (d.startsWith("00")) d = d.substring(2);
        if (d.length() == 11 && d.startsWith("0")) d = "880" + d.substring(1);
        return d;
    }

    public static String sha256Hex(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : h) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Public user id: a hash of the phone number, so ids in the database do not reveal numbers. */
    public static String userId(String phone) {
        return sha256Hex("aidalert|" + normalizePhone(phone)).substring(0, 20);
    }

    // ---------------------------------------------------------------- passwords

    public static String newSalt() {
        byte[] s = new byte[16];
        RNG.nextBytes(s);
        return b64(s);
    }

    public static String hashPassword(String password, String saltB64) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), unb64(saltB64), PBKDF2_ITERATIONS, 256);
            return b64(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean verifyPassword(String password, String saltB64, String expectedHashB64) {
        byte[] a = hashPassword(password, saltB64).getBytes(StandardCharsets.UTF_8);
        byte[] b = expectedHashB64.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    // ---------------------------------------------------------------- hybrid encryption

    /** Returns {publicKeyBase64, privateKeyBase64}. */
    public static String[] generateKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            return new String[]{b64(kp.getPublic().getEncoded()), b64(kp.getPrivate().getEncoded())};
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Output format: base64(rsaWrappedKey).base64(iv).base64(ciphertext). */
    public static String encrypt(String publicKeyB64, byte[] plain) {
        try {
            byte[] aes = new byte[32];
            byte[] iv = new byte[12];
            RNG.nextBytes(aes);
            RNG.nextBytes(iv);

            Cipher data = Cipher.getInstance("AES/GCM/NoPadding");
            data.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aes, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = data.doFinal(plain);

            PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(unb64(publicKeyB64)));
            Cipher wrap = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            wrap.init(Cipher.ENCRYPT_MODE, pub, oaep());
            byte[] wrapped = wrap.doFinal(aes);

            return b64(wrapped) + "." + b64(iv) + "." + b64(ct);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] decrypt(String privateKeyB64, String packet) {
        try {
            String[] p = packet.split("\\.");
            if (p.length != 3) throw new IllegalArgumentException("bad packet");
            PrivateKey priv = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(unb64(privateKeyB64)));
            Cipher wrap = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            wrap.init(Cipher.DECRYPT_MODE, priv, oaep());
            byte[] aes = wrap.doFinal(unb64(p[0]));

            Cipher data = Cipher.getInstance("AES/GCM/NoPadding");
            data.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aes, "AES"), new GCMParameterSpec(128, unb64(p[1])));
            return data.doFinal(unb64(p[2]));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
