package fr.ted30600.zombielogin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public final class PasswordStore {
    private final JavaPlugin plugin;
    private final SecureRandom random = new SecureRandom();
    private final File file;
    private FileConfiguration data;

    public PasswordStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    private void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Impossible de créer le dossier du plugin.");
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isRegistered(UUID uuid) {
        return data.contains("players." + uuid);
    }

    public void register(UUID uuid, String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        data.set("players." + uuid, encode(salt, sha256(salt, password)));
        save();
    }

    public boolean verify(UUID uuid, String password) {
        String stored = data.getString("players." + uuid);
        if (stored == null) return false;
        String[] parts = stored.split(":", 2);
        if (parts.length != 2) return false;
        try {
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            byte[] actual = sha256(salt, password);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder les comptes: " + e.getMessage());
        }
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
