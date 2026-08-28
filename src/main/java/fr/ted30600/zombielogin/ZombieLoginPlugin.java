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
    private final PasswordStore passwords = new PasswordStore();
    private final Set<UUID> authenticated = new HashSet<>();
    private final Map<UUID, Location> loginLocations = new HashMap<>();
    private final Map<UUID, BukkitTask> kickTasks = new HashMap<>();
    private final Map<UUID, Long> sessionExpiry = new HashMap<>();
    private FileConfiguration cfg;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg = getConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("login").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) return true;
            if (args.length != 1) { msg(player, "usage-login"); return true; }
            if (!passwords.isRegistered(player.getUniqueId())) { msg(player, "not-registered"); return true; }
            if (!passwords.verify(player.getUniqueId(), args[0])) { msg(player, "wrong-password"); return true; }
            authenticate(player);
            msg(player, "login-success");
            return true;
        });
        getCommand("register").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) return true;
            if (args.length != 2) { msg(player, "usage-register"); return true; }
            if (passwords.isRegistered(player.getUniqueId())) { msg(player, "already-registered"); return true; }
            int min = cfg.getInt("settings.min-password-length", 4);
            if (args[0].length() < min) {
                player.sendMessage(Component.text("Mot de passe trop court (minimum " + min + " caractères).", NamedTextColor.RED));
                return true;
            }
            if (!args[0].equals(args[1])) { msg(player, "password-mismatch"); return true; }
            passwords.register(player.getUniqueId(), args[0]);
            authenticate(player);
            msg(player, "register-success");
            return true;
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        authenticated.remove(player.getUniqueId());
        loginLocations.put(player.getUniqueId(), player.getLocation().clone());
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        player.sendMessage(Component.text("==============================", NamedTextColor.DARK_GRAY));
        if (passwords.isRegistered(player.getUniqueId())) msg(player, "login-required");
        else msg(player, "register-required");
        player.sendMessage(Component.text("==============================", NamedTextColor.DARK_GRAY));

        int seconds = cfg.getInt("settings.kick-after-seconds", 60);
        kickTasks.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!authenticated.contains(player.getUniqueId()) && player.isOnline())
                player.kick(Component.text("Temps de connexion dépassé.", NamedTextColor.RED));
        }, seconds * 20L));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        authenticated.remove(id);
        loginLocations.remove(id);
        sessionExpiry.remove(id);
        BukkitTask task = kickTasks.remove(id);
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
        UUID id = player.getUniqueId();
        authenticated.add(id);
        Location loc = loginLocations.remove(id);
        if (loc != null) player.teleport(loc);
        player.setInvulnerable(false);
        player.setGameMode(GameMode.SURVIVAL);
        BukkitTask task = kickTasks.remove(id);
        if (task != null) task.cancel();
        long minutes = cfg.getLong("settings.session-timeout-minutes", 30);
        sessionExpiry.put(id, System.currentTimeMillis() + minutes * 60_000L);
    }

    private void msg(Player player, String key) {
        String value = cfg.getString("messages." + key, "Message manquant: " + key);
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(value));
    }
}
