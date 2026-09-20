package fr.ted30600.banhammer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BanHammerPlugin extends JavaPlugin implements Listener {
    private NamespacedKey itemKey;
    private static final String BAN_GUI = "§cBan Hammer";
    private static final String UNBAN_GUI = "§aUnban Hammer";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "hammer_type");
        Bukkit.getPluginManager().registerEvents(this, this);
        registerRecipes();
        Objects.requireNonNull(getCommand("banhammer")).setExecutor(this::command);
        Objects.requireNonNull(getCommand("unbanhammer")).setExecutor(this::command);
        getLogger().info("BanHammer activé pour Paper 1.21.10");
    }

    private boolean command(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (!p.hasPermission("banhammer.use")) {
            p.sendMessage(Component.text("Tu n'as pas la permission.", NamedTextColor.RED));
            return true;
        }
        if (label.equalsIgnoreCase("banhammer")) p.getInventory().addItem(createHammer("ban"));
        else p.getInventory().addItem(createHammer("unban"));
        p.sendMessage(Component.text("Marteau donné.", NamedTextColor.GREEN));
        return true;
    }

    private ItemStack createHammer(String type) {
        String path = type.equals("ban") ? "ban-hammer" : "unban-hammer";
        Material material = Material.valueOf(getConfig().getString(path + ".material"));
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(getConfig().getString(path + ".name").replace("§", "")));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    private void registerRecipes() {
        registerRecipe("ban", "ban_hammer_recipe", "ban-hammer");
        registerRecipe("unban", "unban_hammer_recipe", "unban-hammer");
    }

    private void registerRecipe(String type, String keyName, String path) {
        Material resultMaterial = Material.valueOf(getConfig().getString(path + ".material"));
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, keyName), createHammer(type));
        List<String> shape = getConfig().getStringList(path + ".recipe.shape");
        recipe.shape(shape.toArray(new String[0]));
        for (String key : getConfig().getConfigurationSection(path + ".recipe.ingredients").getKeys(false)) {
            Material m = Material.valueOf(getConfig().getString(path + ".recipe.ingredients." + key));
            recipe.setIngredient(key.charAt(0), m);
        }
        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!e.getAction().isRightClick()) return;
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String type = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (type == null) return;
        e.setCancelled(true);
        if (!e.getPlayer().hasPermission("banhammer.use")) return;
        if (type.equals("ban")) openBanGui(e.getPlayer());
        else openUnbanGui(e.getPlayer());
    }

    private void openBanGui(Player viewer) {
        Inventory inv = Bukkit.createInventory(null, 27, BAN_GUI);
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer)) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            var meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(target.getName(), NamedTextColor.WHITE));
            meta.lore(List.of(Component.text("Clique pour bannir", NamedTextColor.RED)));
            head.setItemMeta(meta);
            if (slot < 27) inv.setItem(slot++, head);
        }
        viewer.openInventory(inv);
    }

    private void openUnbanGui(Player viewer) {
        Inventory inv = Bukkit.createInventory(null, 27, UNBAN_GUI);
        int slot = 0;
        BanList<?> list = Bukkit.getBanList(BanList.Type.NAME);
        for (BanList.Entry<?> entry : list.getEntries()) {
            String name = entry.getTarget();
            OfflinePlayer target = Bukkit.getOfflinePlayer(name);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            var meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(name, NamedTextColor.WHITE));
            meta.lore(List.of(Component.text("Clique pour débannir", NamedTextColor.GREEN)));
            head.setItemMeta(meta);
            if (slot < 27) inv.setItem(slot++, head);
        }
        viewer.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().title().toString();
        if (!title.contains("Ban Hammer") && !title.contains("Unban Hammer")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() != Material.PLAYER_HEAD) return;
        ItemMeta meta = e.getCurrentItem().getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.SkullMeta skull)) return;
        OfflinePlayer target = skull.getOwningPlayer();
        if (target == null || target.getName() == null) return;

        if (title.contains("Ban Hammer")) {
            if (!p.hasPermission("banhammer.ban")) return;
            String name = target.getName();
            Bukkit.getBanList(BanList.Type.NAME).addBan(name, "Banni avec le Ban Hammer", null, p.getName());
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) online.kick(Component.text("Tu as été banni.", NamedTextColor.RED));
            p.sendMessage(Component.text(name + " a été banni.", NamedTextColor.RED));
        } else {
            if (!p.hasPermission("banhammer.unban")) return;
            Bukkit.getBanList(BanList.Type.NAME).pardon(target.getName());
            p.sendMessage(Component.text(target.getName() + " a été débanni.", NamedTextColor.GREEN));
        }
        p.closeInventory();
    }
}
