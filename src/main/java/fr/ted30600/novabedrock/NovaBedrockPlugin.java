package fr.ted30600.novabedrock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class NovaBedrockPlugin extends JavaPlugin implements Listener {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Command command = getCommand("bedrock");
        if (command != null) command.setExecutor(this::onCommand);
        getLogger().info("NovaBedrock active - /bedrock");
    }

    private boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est utilisable par un joueur.");
            return true;
        }
        if (!player.hasPermission("novabedrock.use")) {
            player.sendMessage(message("no-permission"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("novabedrock.admin")) {
                player.sendMessage(message("no-permission"));
                return true;
            }
            reloadConfig();
            player.sendMessage(message("reloaded"));
            return true;
        }
        sendBedrockLink(player);
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("auto-send-on-join", true)) return;
        getServer().getScheduler().runTaskLater(this,
                () -> sendBedrockLink(event.getPlayer()), 20L);
    }

    private void sendBedrockLink(Player player) {
        String address = getConfig().getString("bedrock-address", "").trim();
        int port = getConfig().getInt("bedrock-port", 19132);
        String name = getConfig().getString("server-name", "Mon serveur").trim();

        if (address.isEmpty() || port < 1 || port > 65535) {
            player.sendMessage(Component.text(
                    "NovaBedrock : configure bedrock-address et bedrock-port dans config.yml.",
                    NamedTextColor.RED));
            return;
        }

        String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String deepLink = "minecraft://?addExternalServer="
                + encodedName + "|" + address + ":" + port;

        player.sendMessage(LEGACY.deserialize(
                getConfig().getString("messages.prefix", "&b&lBedrock &8» &f"))
                .append(Component.text("Clique sur le bouton ci-dessous.", NamedTextColor.GRAY)));

        Component button = LEGACY.deserialize(
                getConfig().getString("messages.button",
                        "&a[ &f➜ AJOUTER LE SERVEUR BEDROCK &a]"))
                .clickEvent(ClickEvent.openUrl(deepLink))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Ajouter " + name + " à la liste Bedrock",
                                NamedTextColor.GREEN)));

        player.sendMessage(button);
        player.sendMessage(message("added"));
    }

    private Component message(String key) {
        return LEGACY.deserialize(getConfig().getString("messages." + key, ""));
    }
}
