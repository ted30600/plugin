package fr.ted30600.eventplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public final class EventPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private BukkitTask actionBarTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (getCommand("event") != null) {
            getCommand("event").setExecutor(this);
            getCommand("event").setTabCompleter(this);
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        startActionBarTask();
        getLogger().info("EventPlugin active.");
    }

    @Override
    public void onDisable() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
    }

    private void startActionBarTask() {
        if (actionBarTask != null) actionBarTask.cancel();

        actionBarTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            String message = getConfig().getString("event.actionbar", "§e§lEVENT §8» §f/event");
            Component actionBar = legacy(message);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendActionBar(actionBar);
            }
        }, 0L, 40L); // Toutes les 2 secondes.
    }

    private Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    private void openEventMenu(Player player) {
        int size = getConfig().getInt("interface.size", 27);
        if (size < 9 || size > 54 || size % 9 != 0) size = 27;

        String title = getConfig().getString("interface.title", "§6§lInformations de l'evenement");
        Inventory inventory = Bukkit.createInventory(null, size, legacy(title));

        inventory.setItem(11, item(Material.NAME_TAG,
                getConfig().getString("interface.name-item", "§e§lNom"),
                List.of("§f" + value("event.name"))));

        inventory.setItem(13, item(Material.WRITABLE_BOOK,
                getConfig().getString("interface.title-item", "§6§lTitre"),
                List.of(value("event.title"))));

        inventory.setItem(15, item(Material.BOOK,
                getConfig().getString("interface.description-item", "§b§lDescription"),
                wrap(value("event.description"))));

        player.openInventory(inventory);
    }

    private ItemStack item(Material material, String displayName, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(legacy(displayName));
        meta.lore(lore.stream().map(this::legacy).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private List<String> wrap(String text) {
        if (text == null || text.isBlank()) return List.of("§7Aucune description.");
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() + word.length() + 1 > 35) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(' ');
            current.append(word);
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    private String value(String path) {
        return getConfig().getString(path, "§7Non configure");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String configuredTitle = getConfig().getString("interface.title", "§6§lInformations de l'evenement");
        if (event.getView().title().equals(legacy(configuredTitle))) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("event")) return false;

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(legacy("§cCette commande doit etre utilisee en jeu."));
                return true;
            }
            openEventMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("event.admin")) {
                sender.sendMessage(legacy(value("messages.no-permission")));
                return true;
            }
            reloadConfig();
            startActionBarTask();
            sender.sendMessage(legacy(value("messages.reloaded")));
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (!sender.hasPermission("event.admin")) {
                sender.sendMessage(legacy(value("messages.no-permission")));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(legacy("§cUsage: /event set <name|title|description|actionbar> <texte>"));
                return true;
            }

            String key = switch (args[1].toLowerCase()) {
                case "name" -> "event.name";
                case "title" -> "event.title";
                case "description" -> "event.description";
                case "actionbar" -> "event.actionbar";
                default -> null;
            };

            if (key == null) {
                sender.sendMessage(legacy("§cChamp inconnu. Utilise: name, title, description ou actionbar."));
                return true;
            }

            String text = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            getConfig().set(key, text);
            saveConfig();
            if (key.equals("event.actionbar")) startActionBarTask();
            sender.sendMessage(legacy(value("messages.updated")));
            return true;
        }

        sender.sendMessage(legacy("§e/event §7→ afficher les informations de l'evenement"));
        sender.sendMessage(legacy("§e/event set <name|title|description|actionbar> <texte>"));
        sender.sendMessage(legacy("§e/event reload §7→ recharger la configuration"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("set", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return List.of("name", "title", "description", "actionbar");
        }
        return List.of();
    }
}
