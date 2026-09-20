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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;

public final class BanHammerPlugin extends JavaPlugin implements Listener {
    private NamespacedKey hammerKey;
    private static final Component BAN_TITLE = Component.text("Ban Hammer", NamedTextColor.RED);
    private static final Component UNBAN_TITLE = Component.text("Unban Hammer", NamedTextColor.GREEN);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        hammerKey = new NamespacedKey(this, "hammer_type");
        Bukkit.getPluginManager().registerEvents(this, this);
        registerRecipes();
        Objects.requireNonNull(getCommand("banhammer")).setExecutor(this::command);
        Objects.requireNonNull(getCommand("unbanhammer")).setExecutor(this::command);
        getLogger().info("BanHammer activé.");
    }

    private boolean command(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }
        if (!player.hasPermission("banhammer.use")) {
            player.sendMessage(Component.text("Tu n'as pas la permission.", NamedTextColor.RED));
            return true;
        }

        String type = label.equalsIgnoreCase("banhammer") ? "ban" : "unban";
        player.getInventory().addItem(createHammer(type));
        player.sendMessage(Component.text("Marteau donné.", NamedTextColor.GREEN));
        return true;
    }

    private ItemStack createHammer(String type) {
        String path = type.equals("ban") ? "ban-hammer" : "unban-hammer";
        Material material = Material.valueOf(
                Objects.requireNonNull(getConfig().getString(path + ".material"))
        );

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String name = getConfig().getString(path + ".name", type);
        meta.displayName(Component.text(name.replaceAll("§.", "")));
        meta.getPersistentDataContainer().set(
                hammerKey, PersistentDataType.STRING, type
        );
        item.setItemMeta(meta);
        return item;
    }

    private void registerRecipes() {
        registerRecipe("ban", "ban_hammer_recipe", "ban-hammer");
        registerRecipe("unban", "unban_hammer_recipe", "unban-hammer");
    }

    private void registerRecipe(String type, String keyName, String path) {
        String[] shape = getConfig().getStringList(
                path + ".recipe.shape"
        ).toArray(new String[0]);

        ShapedRecipe recipe = new ShapedRecipe(
                new NamespacedKey(this, keyName),
                createHammer(type)
        );
        recipe.shape(shape);

        var section = getConfig().getConfigurationSection(
                path + ".recipe.ingredients"
        );
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.valueOf(
                        Objects.requireNonNull(
                                getConfig().getString(
                                        path + ".recipe.ingredients." + key
                                )
                        )
                );
                recipe.setIngredient(key.charAt(0), material);
            }
        }

        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onHammerUse(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        String type = item.getItemMeta().getPersistentDataContainer()
                .get(hammerKey, PersistentDataType.STRING);
        if (type == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("banhammer.use")) {
            player.sendMessage(Component.text("Tu n'as pas la permission.", NamedTextColor.RED));
            return;
        }

        if (type.equals("ban")) openBanGui(player);
        else openUnbanGui(player);
    }

    private void openBanGui(Player viewer) {
        Inventory inventory = Bukkit.createInventory(null, 27, BAN_TITLE);
        int slot = 0;

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer)) continue;
            if (slot >= 27) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(target.getName(), NamedTextColor.WHITE));
            meta.lore(List.of(
                    Component.text("Clique pour bannir", NamedTextColor.RED)
            ));
            head.setItemMeta(meta);
            inventory.setItem(slot++, head);
        }

        viewer.openInventory(inventory);
    }

    private void openUnbanGui(Player viewer) {
        Inventory inventory = Bukkit.createInventory(null, 27, UNBAN_TITLE);
        int slot = 0;

        for (BanList.Entry<?> entry : Bukkit.getBanList(BanList.Type.NAME).getEntries()) {
            if (slot >= 27) break;

            String name = entry.getTarget();
            OfflinePlayer target = Bukkit.getOfflinePlayer(name);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(name, NamedTextColor.WHITE));
            meta.lore(List.of(
                    Component.text("Clique pour débannir", NamedTextColor.GREEN)
            ));
            head.setItemMeta(meta);
            inventory.setItem(slot++, head);
        }

        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component title = event.getView().title();
        boolean banGui = title.equals(BAN_TITLE);
        boolean unbanGui = title.equals(UNBAN_TITLE);
        if (!banGui && !unbanGui) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
        if (!(clicked.getItemMeta() instanceof SkullMeta meta)) return;

        OfflinePlayer target = meta.getOwningPlayer();
        if (target == null || target.getName() == null) return;

        String name = target.getName();

        if (banGui) {
            if (!player.hasPermission("banhammer.ban")) return;

            Bukkit.getBanList(BanList.Type.NAME).addBan(
                    name,
                    "Banni avec le Ban Hammer",
                    null,
                    player.getName()
            );

            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                online.kick(Component.text(
                        "Tu as été banni avec le Ban Hammer.",
                        NamedTextColor.RED
                ));
            }

            player.sendMessage(Component.text(
                    name + " a été banni.", NamedTextColor.RED
            ));
        } else {
            if (!player.hasPermission("banhammer.unban")) return;

            Bukkit.getBanList(BanList.Type.NAME).pardon(name);
            player.sendMessage(Component.text(
                    name + " a été débanni.", NamedTextColor.GREEN
            ));
        }

        player.closeInventory();
    }
}
