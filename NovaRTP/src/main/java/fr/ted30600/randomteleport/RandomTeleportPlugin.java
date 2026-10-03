package fr.ted30600.randomteleport;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class RandomTeleportPlugin extends JavaPlugin implements Listener {
    private final Random random = new Random();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("NovaRTP actif pour Paper 1.21.10.");
    }

    @Override
    public void onDisable() {
        cooldowns.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }
        if (!player.hasPermission("randomteleport.use")) {
            player.sendMessage(ChatColor.RED + "Tu n'as pas la permission.");
            return true;
        }
        openMenu(player);
        return true;
    }

    private void openMenu(Player player) {
        int rows = Math.max(1, Math.min(6, getConfig().getInt("menu.rows", 3)));
        String title = color(getConfig().getString("menu.title", "&8Random Teleport | Menu"));
        Inventory inventory = Bukkit.createInventory(new RtpMenuHolder(), rows * 9, title);
        fillBackground(inventory);

        if (getConfig().getBoolean("worlds.overworld.enabled", true))
            inventory.setItem(11, createItem(material("worlds.overworld.material", Material.GRASS_BLOCK), getConfig().getString("worlds.overworld.name", "&aOverworld"), getConfig().getStringList("worlds.overworld.lore")));
        if (getConfig().getBoolean("worlds.nether.enabled", true))
            inventory.setItem(13, createItem(material("worlds.nether.material", Material.NETHERRACK), getConfig().getString("worlds.nether.name", "&cNether"), getConfig().getStringList("worlds.nether.lore")));
        if (getConfig().getBoolean("worlds.end.enabled", true))
            inventory.setItem(15, createItem(material("worlds.end.material", Material.END_STONE), getConfig().getString("worlds.end.name", "&5The End"), getConfig().getStringList("worlds.end.lore")));

        int closeSlot = getConfig().getInt("close.slot", 17);
        if (closeSlot >= 0 && closeSlot < inventory.getSize())
            inventory.setItem(closeSlot, createItem(material("close.material", Material.OAK_DOOR), getConfig().getString("close.name", "&cFermer"), getConfig().getStringList("close.lore")));

        player.openInventory(inventory);
    }

    private void fillBackground(Inventory inventory) {
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof RtpMenuHolder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        if (slot == 11 && getConfig().getBoolean("worlds.overworld.enabled", true))
            startTeleport(player, World.Environment.NORMAL);
        else if (slot == 13 && getConfig().getBoolean("worlds.nether.enabled", true))
            startTeleport(player, World.Environment.NETHER);
        else if (slot == 15 && getConfig().getBoolean("worlds.end.enabled", true))
            startTeleport(player, World.Environment.THE_END);
        else if (slot == getConfig().getInt("close.slot", 17))
            player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RtpMenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    private void startTeleport(Player player, World.Environment environment) {
        long cooldown = getConfig().getLong("teleport.cooldown-seconds", 0);
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long remaining = (last + cooldown * 1000L) - now;
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "Attends encore " + ((remaining + 999) / 1000) + " seconde(s).");
            return;
        }

        World world = findWorld(environment);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Le monde demandé n'existe pas sur ce serveur.");
            return;
        }

        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Recherche d'un emplacement sûr...");
        Location location = findSafeLocation(world, getConfig().getInt("teleport.attempts", 40));
        if (location == null) {
            player.sendMessage(ChatColor.RED + "Aucun emplacement sûr trouvé. Réessaie.");
            return;
        }

        cooldowns.put(player.getUniqueId(), now);
        player.teleportAsync(location).thenAccept(success -> Bukkit.getScheduler().runTask(this, () -> {
            player.sendMessage(success ? ChatColor.GREEN + "Téléportation effectuée !" : ChatColor.RED + "La téléportation a échoué.");
        }));
    }

    private World findWorld(World.Environment environment) {
        for (World world : Bukkit.getWorlds()) if (world.getEnvironment() == environment) return world;
        return null;
    }

    private Location findSafeLocation(World world, int attempts) {
        int min = Math.max(0, getConfig().getInt("teleport.min-distance", 500));
        int max = Math.max(min + 1, getConfig().getInt("teleport.max-distance", 5000));

        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = min + random.nextDouble() * (max - min);
            int x = (int) Math.round(Math.cos(angle) * distance);
            int z = (int) Math.round(Math.sin(angle) * distance);

            if (world.getEnvironment() == World.Environment.NETHER) {
                Location nether = findNetherSafeLocation(world, x, z);
                if (nether != null) return nether;
            } else {
                int y = world.getHighestBlockYAt(x, z);
                if (y <= world.getMinHeight() + 1 || y >= world.getMaxHeight() - 2) continue;
                Location location = new Location(world, x + 0.5, y + 1.0, z + 0.5);
                if (isSafe(location)) return location;
            }
        }
        return null;
    }

    private Location findNetherSafeLocation(World world, int x, int z) {
        int minY = Math.max(world.getMinHeight() + 2, 32);
        int maxY = Math.min(world.getMaxHeight() - 3, 118);
        for (int y = maxY; y >= minY; y--) {
            Location location = new Location(world, x + 0.5, y, z + 0.5);
            if (isSafe(location) && location.getBlock().getRelative(0, -1, 0).getType().isSolid()) return location;
        }
        return null;
    }

    private boolean isSafe(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block floor = feet.getRelative(0, -1, 0);
        return floor.getType().isSolid() && !floor.isLiquid() && feet.getType().isAir() && head.getType().isAir() && !feet.isLiquid() && !head.isLiquid();
    }

    private Material material(String path, Material fallback) {
        String value = getConfig().getString(path);
        if (value == null) return fallback;
        Material material = Material.matchMaterial(value);
        return material != null ? material : fallback;
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) coloredLore.add(color(line));
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static final class RtpMenuHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
