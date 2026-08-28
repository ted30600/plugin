package fr.ted30600.zombielogin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PasswordStore {
    private final Map<UUID, String> hashes = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public boolean isRegistered(UUID uuid) {
        return hashes.containsKey(uuid);
    }

    public void register(UUID uuid, String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        hashes.put(uuid, encode(salt, sha256(salt, password)));
    }

    public boolean verify(UUID uuid, String password) {
        String stored = hashes.get(uuid);
        if (stored == null) return false;
        String[] parts = stored.split(":", 2);
        if (parts.length != 2) return false;
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);
        byte[] actual = sha256(salt, password);
        return MessageDigest.isEqual(expected, actual);
    }

    private String encode(byte[] salt, byte[] hash) {
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    private byte[] sha256(byte[] salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return digest.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
