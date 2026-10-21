package fr.novastream.novahardcore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class NovaHardcore extends JavaPlugin implements Listener {
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private File dataFile;
    private YamlConfiguration playerData;
    private String worldName;
    private String fallbackWorld;
    private int borderSize;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        worldName = getConfig().getString("world-name", "hardcore");
        fallbackWorld = getConfig().getString("fallback-world", "world");
        borderSize = getConfig().getInt("border-size", 2000);
        dataFile = new File(getDataFolder(), "players.yml");
        loadPlayerData();
        getServer().getPluginManager().registerEvents(this, this);
        World world = Bukkit.getWorld(worldName);
        if (world != null) applyBorder(world);
        getLogger().info("NovaHardcore activé. Monde : " + worldName);
    }

    @Override
    public void onDisable() {
        savePlayerData();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private World fallback() {
        World world = Bukkit.getWorld(fallbackWorld);
        if (world == null && !Bukkit.getWorlds().isEmpty()) world = Bukkit.getWorlds().get(0);
        return world;
    }

    private void sendToFallback(Player player) {
        World world = fallback();
        if (world != null) player.teleport(world.getSpawnLocation());
    }

    private void loadPlayerData() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        playerData = YamlConfiguration.loadConfiguration(dataFile);
        loadIds(playerData.getStringList("participants"), participants);
        loadIds(playerData.getStringList("eliminated"), eliminated);
    }

    private void loadIds(List<String> values, Set<UUID> target) {
        for (String value : values) {
            try { target.add(UUID.fromString(value)); }
            catch (IllegalArgumentException ignored) { getLogger().warning("UUID invalide dans players.yml : " + value); }
        }
    }

    private void savePlayerData() {
        if (playerData == null) return;
        playerData.set("participants", participants.stream().map(UUID::toString).toList());
        playerData.set("eliminated", eliminated.stream().map(UUID::toString).toList());
        try { playerData.save(dataFile); }
        catch (IOException ex) { getLogger().severe("Sauvegarde impossible : " + ex.getMessage()); }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!player.getWorld().getName().equalsIgnoreCase(worldName)) return;
        participants.add(player.getUniqueId());
        if (eliminated.add(player.getUniqueId())) {
            String message = getConfig().getString("messages.eliminated", "&c{player} a été éliminé !");
            Bukkit.broadcastMessage(color(message.replace("{player}", player.getName())));
            savePlayerData();
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!eliminated.contains(player.getUniqueId())) return;
        World world = fallback();
        if (world != null) event.setRespawnLocation(world.getSpawnLocation());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (eliminated.contains(player.getUniqueId()) &&
                player.getWorld().getName().equalsIgnoreCase(worldName)) {
            Bukkit.getScheduler().runTask(this, () -> sendToFallback(player));
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (eliminated.contains(player.getUniqueId()) &&
                player.getWorld().getName().equalsIgnoreCase(worldName)) {
            Bukkit.getScheduler().runTask(this, () -> sendToFallback(player));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color("&6NovaHardcore &7- /hardcore join, leave, classement, status, setborder <blocs>, reset"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Commande réservée aux joueurs."); return true; }
                if (!player.hasPermission("novahardcore.play")) { player.sendMessage(color("&cPermission manquante.")); return true; }
                if (eliminated.contains(player.getUniqueId())) {
                    player.sendMessage(color(getConfig().getString("messages.already-eliminated")));
                    return true;
                }
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    player.sendMessage(color("&cMonde absent. Un administrateur doit lancer : /mv create " + worldName + " NORMAL"));
                    return true;
                }
                participants.add(player.getUniqueId());
                player.teleport(world.getSpawnLocation());
                player.sendMessage(color(getConfig().getString("messages.joined")));
                savePlayerData();
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Commande réservée aux joueurs."); return true; }
                if (player.getWorld().getName().equalsIgnoreCase(worldName)) sendToFallback(player);
                player.sendMessage(color("&aTu as quitté le monde Hardcore. Ta vie est conservée."));
            }
            case "classement", "ranking", "top" -> {
                sender.sendMessage(color("&6&l— NovaHardcore : Survivants —"));
                List<UUID> alive = participants.stream()
                        .filter(id -> !eliminated.contains(id))
                        .sorted(Comparator.comparing(id -> {
                            String name = Bukkit.getOfflinePlayer(id).getName();
                            return name == null ? id.toString() : name.toLowerCase(Locale.ROOT);
                        })).toList();
                if (alive.isEmpty()) sender.sendMessage(color("&7Aucun survivant enregistré."));
                for (int i = 0; i < alive.size(); i++) {
                    String name = Bukkit.getOfflinePlayer(alive.get(i)).getName();
                    sender.sendMessage(color("&e" + (i + 1) + ". &f" + (name == null ? alive.get(i).toString() : name)));
                }
                sender.sendMessage(color("&cJoueurs éliminés : " + eliminated.size()));
            }
            case "status" -> {
                long alive = participants.stream().filter(id -> !eliminated.contains(id)).count();
                sender.sendMessage(color("&6NovaHardcore &7| &aSurvivants : " + alive +
                        " &7| &cÉliminés : " + eliminated.size() + " &7| Monde : " + worldName));
            }
            case "setborder" -> {
                if (!sender.hasPermission("novahardcore.admin")) { sender.sendMessage(color("&cPermission manquante.")); return true; }
                if (args.length < 2) { sender.sendMessage(color("&cUtilisation : /hardcore setborder <diamètre>")); return true; }
                try {
                    int size = Integer.parseInt(args[1]);
                    if (size < 100 || size > 60000000) throw new NumberFormatException();
                    borderSize = size;
                    getConfig().set("border-size", size);
                    saveConfig();
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) applyBorder(world);
                    sender.sendMessage(color("&aBordure réglée à " + size + " blocs."));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(color("&cIndique un nombre entre 100 et 60000000."));
                }
            }
            case "reset" -> {
                if (!sender.hasPermission("novahardcore.admin")) { sender.sendMessage(color("&cPermission manquante.")); return true; }
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    sender.sendMessage(color(getConfig().getString("messages.reset-warning")));
                    sender.sendMessage(color("&cConfirme avec : /hardcore reset confirm"));
                    return true;
                }
                resetWorld(sender);
            }
            default -> sender.sendMessage(color("&cCommande inconnue. Utilise /hardcore."));
        }
        return true;
    }

    private void applyBorder(World world) {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(borderSize);
    }

    private void resetWorld(CommandSender sender) {
        World world = Bukkit.getWorld(worldName);
        World safeWorld = fallback();
        if (safeWorld == null) { sender.sendMessage(color("&cMonde de secours introuvable.")); return; }
        if (world == null) { sender.sendMessage(color("&cLe monde Hardcore n'est pas chargé.")); return; }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getName().equalsIgnoreCase(worldName)) {
                player.teleport(safeWorld.getSpawnLocation());
            }
        }

        Bukkit.getScheduler().runTask(this, () -> {
            if (!Bukkit.unloadWorld(world, false)) {
                sender.sendMessage(color("&cImpossible de décharger le monde. Vérifie Multiverse-Core et réessaie."));
                return;
            }
            File folder = new File(Bukkit.getWorldContainer(), worldName);
            if (folder.exists() && !deleteRecursively(folder)) {
                sender.sendMessage(color("&cSuppression impossible. Arrête le serveur et vérifie les fichiers du monde."));
                return;
            }
            participants.clear();
            eliminated.clear();
            savePlayerData();
            World recreated = Bukkit.createWorld(new WorldCreator(worldName));
            if (recreated == null) {
                sender.sendMessage(color("&cLe monde n'a pas pu être recréé. Utilise /mv create " + worldName + " NORMAL."));
                return;
            }
            applyBorder(recreated);
            sender.sendMessage(color("&aNouvelle partie Hardcore créée. Le classement est remis à zéro."));
        });
    }

    private boolean deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) if (!deleteRecursively(child)) return false;
        }
        return file.delete();
    }
}
