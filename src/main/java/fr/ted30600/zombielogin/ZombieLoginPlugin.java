package fr.ted30600.zombielogin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ZombieLoginPlugin extends JavaPlugin implements Listener {
    private PasswordStore passwords;
    private final Set<UUID> authenticated = new HashSet<>();
    private final Map<UUID, Location> loginLocations = new HashMap<>();
    private final Map<UUID, BukkitTask> kickTasks = new HashMap<>();
    private final Map<UUID, Long> sessionExpiry = new HashMap<>();
    private FileConfiguration cfg;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg = getConfig();
        passwords = new PasswordStore(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        if (getCommand("login") != null) {
            getCommand("login").setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player player)) return true;
                if (args.length != 1) { msg(player, "usage-login"); return true; }
                UUID uuid = player.getUniqueId();
                if (!passwords.isRegistered(uuid)) { msg(player, "not-registered"); return true; }
                if (!passwords.verify(uuid, args[0])) { msg(player, "wrong-password"); return true; }
                authenticate(player);
                msg(player, "login-success");
                return true;
            });
        }

        if (getCommand("register") != null) {
            getCommand("register").setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player player)) return true;
                if (args.length != 2) { msg(player, "usage-register"); return true; }
                UUID uuid = player.getUniqueId();
                if (passwords.isRegistered(uuid)) { msg(player, "already-registered"); return true; }
                int min = cfg.getInt("settings.min-password-length", 4);
                if (args[0].length() < min) {
                    player.sendMessage(Component.text("Mot de passe trop court (minimum " + min + " caractères).", NamedTextColor.RED));
                    return true;
                }
                if (!args[0].equals(args[1])) { msg(player, "password-mismatch"); return true; }
                passwords.register(uuid, args[0]);
                authenticate(player);
                msg(player, "register-success");
                return true;
            });
        }

        getLogger().info("UltraLogin activé - comptes persistants dans players.yml.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        authenticated.remove(uuid);
        loginLocations.put(uuid, player.getLocation().clone());
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        player.sendMessage(Component.text("==============================", NamedTextColor.DARK_GRAY));
        if (passwords.isRegistered(uuid)) msg(player, "login-required");
        else msg(player, "register-required");
        player.sendMessage(Component.text("==============================", NamedTextColor.DARK_GRAY));

        int seconds = cfg.getInt("settings.kick-after-seconds", 60);
        BukkitTask oldTask = kickTasks.remove(uuid);
        if (oldTask != null) oldTask.cancel();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!authenticated.contains(uuid) && player.isOnline()) {
                player.kick(Component.text("Temps de connexion dépassé.", NamedTextColor.RED));
            }
        }, seconds * 20L);
        kickTasks.put(uuid, task);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        authenticated.remove(uuid);
        loginLocations.remove(uuid);
        sessionExpiry.remove(uuid);
        BukkitTask task = kickTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (authenticated.contains(player.getUniqueId())) return;
        String command = event.getMessage().toLowerCase().split(" ")[0];
        if (!command.equals("/login") && !command.equals("/register")) {
            event.setCancelled(true);
            msg(player, "login-required");
        }
    }

    private void authenticate(Player player) {
        UUID uuid = player.getUniqueId();
        authenticated.add(uuid);
        Location location = loginLocations.remove(uuid);
        if (location != null) player.teleport(location);
        player.setInvulnerable(false);
        player.setGameMode(GameMode.SURVIVAL);
        BukkitTask task = kickTasks.remove(uuid);
        if (task != null) task.cancel();
        long minutes = cfg.getLong("settings.session-timeout-minutes", 30);
        sessionExpiry.put(uuid, System.currentTimeMillis() + minutes * 60_000L);
    }

    private void msg(Player player, String key) {
        String value = cfg.getString("messages." + key, "Message manquant: " + key);
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(value));
    }
}
