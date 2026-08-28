package fr.novastream.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class NovaStreamItems extends JavaPlugin implements CommandExecutor {
    private NamespacedKey itemIdKey;

    @Override
    public void onEnable() {
        itemIdKey = new NamespacedKey(this, "custom_item");
        getCommand("novaitems").setExecutor(this);
        getLogger().info("NovaStreamItems activé - Paper 1.21.10");
    }

    public ItemStack createPaladiumPickaxe() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Pioche en Paladium", NamedTextColor.LIGHT_PURPLE));
        meta.lore(java.util.List.of(
                Component.text("Outil custom NovaStream", NamedTextColor.GRAY),
                Component.text("Minerai : Paladium", NamedTextColor.LIGHT_PURPLE)
        ));
        meta.setCustomModelData(1001);
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, "paladium_pickaxe");
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("Usage : /novaitems give paladium_pickaxe [joueur] [quantité]", NamedTextColor.YELLOW));
            return true;
        }
        if (!(sender instanceof Player player) || !player.hasPermission("novastreamitems.admin")) {
            sender.sendMessage(Component.text("Pas de permission.", NamedTextColor.RED));
            return true;
        }
        if (!args[1].equalsIgnoreCase("paladium_pickaxe")) {
            sender.sendMessage(Component.text("Item inconnu. Disponible : paladium_pickaxe", NamedTextColor.RED));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[3]))); }
            catch (NumberFormatException ignored) { }
        }
        ItemStack pickaxe = createPaladiumPickaxe();
        pickaxe.setAmount(amount);
        Player target = player;
        if (args.length >= 3) {
            Player online = getServer().getPlayerExact(args[2]);
            if (online == null) {
                sender.sendMessage(Component.text("Joueur introuvable.", NamedTextColor.RED));
                return true;
            }
            target = online;
        }
        target.getInventory().addItem(pickaxe);
        sender.sendMessage(Component.text("Pioche en Paladium donnée à " + target.getName() + " x" + amount, NamedTextColor.GREEN));
        return true;
    }
}
