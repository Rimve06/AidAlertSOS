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
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * Privacy helpers (plain Java so they can be unit tested on a PC):
 *  - hashed phone keys (phoneIndex) — passwords are handled by Firebase Auth, never by this app,
 *  - hybrid encryption (AES-GCM for the data, RSA-OAEP for the AES key) so phones that
 *    relay an SOS packet offline can carry it but can never read it.
 */
public final class Crypto {
    private Crypto() {}

    private static final SecureRandom RNG = new SecureRandom();

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

    /** Key of phoneIndex/{key}: a hash of the phone number, so the database never holds numbers as keys. */
    public static String phoneKey(String phone) {
        return sha256Hex("aidalert|" + normalizePhone(phone)).substring(0, 20);
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

    /** Password-protects a secret (PBKDF2 + AES-GCM) so it can be stored on the server and restored on another device. */
    public static String wrapSecret(String secret, String password) {
        try {
            byte[] salt = new byte[16], iv = new byte[12];
            SecureRandom rnd = new SecureRandom();
            rnd.nextBytes(salt);
            rnd.nextBytes(iv);
            javax.crypto.SecretKeyFactory f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] k = f.generateSecret(new javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 60000, 256)).getEncoded();
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder e = Base64.getEncoder();
            return e.encodeToString(salt) + "." + e.encodeToString(iv) + "." + e.encodeToString(ct);
        } catch (Exception ex) {
            return "";
        }
    }

    /** @return the secret, or null when the password is wrong / the text is damaged */
    public static String unwrapSecret(String wrapped, String password) {
        try {
            String[] p = wrapped.split("\\.");
            if (p.length != 3) return null;
            Base64.Decoder dec = Base64.getDecoder();
            javax.crypto.SecretKeyFactory f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] k = f.generateSecret(new javax.crypto.spec.PBEKeySpec(password.toCharArray(), dec.decode(p[0]), 60000, 256)).getEncoded();
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(k, "AES"), new GCMParameterSpec(128, dec.decode(p[1])));
            return new String(c.doFinal(dec.decode(p[2])), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }
}
