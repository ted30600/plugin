package fr.novastream.novaanticheat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NovaAntiCheat extends JavaPlugin implements Listener {
    private final Map<UUID, Integer> movementVl = new HashMap<>();
    private final Map<UUID, OreStats> oreStats = new HashMap<>();
    private final Map<UUID, Long> lastAlert = new HashMap<>();

    private static final Set<Material> COMMON_ROCK = EnumSet.of(
            Material.STONE, Material.DEEPSLATE, Material.ANDESITE,
            Material.DIORITE, Material.GRANITE, Material.TUFF,
            Material.COBBLED_DEEPSLATE, Material.COBBLESTONE
    );
    private static final Set<Material> RARE_ORES = EnumSet.of(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.ANCIENT_DEBRIS
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("NovaAntiCheat initialisé pour Paper 1.21.10.");
    }

    @Override
    public void onDisable() {
        movementVl.clear();
        oreStats.clear();
        lastAlert.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!getConfig().getBoolean("movement.enabled", true)) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getWorld() != event.getTo().getWorld()) return;
        if (event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getZ() == event.getTo().getZ()) return;

        Player player = event.getPlayer();
        if (isMovementExempt(player)) return;

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double max = getConfig().getDouble("movement.max-horizontal-per-tick", 0.95);

        // Un seul tick rapide peut venir de la latence : plusieurs dépassements sont requis.
        UUID id = player.getUniqueId();
        if (horizontal > max) {
            int vl = movementVl.merge(id, 1, Integer::sum);
            if (vl >= getConfig().getInt("movement.alert-level", 3)) {
                alert(player, "Déplacement anormal (" + String.format("%.2f", horizontal)
                        + " bloc/tick, VL " + vl + ")");
                movementVl.put(id, 0);
            }
        } else {
            movementVl.computeIfPresent(id, (ignored, vl) -> Math.max(0, vl - 1));
        }
    }

    private boolean isMovementExempt(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return true;
        if (player.isFlying() || player.getAllowFlight() || player.isInsideVehicle()) return true;
        if (player.isGliding() || player.isSwimming() || player.isRiptiding()) return true;
        if (player.hasPotionEffect(PotionEffectType.SPEED)) return true;
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!getConfig().getBoolean("xray.enabled", true)) return;
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();
        if (!COMMON_ROCK.contains(type) && !RARE_ORES.contains(type)) return;

        long now = System.currentTimeMillis();
        long windowMs = getConfig().getLong("xray.window-minutes", 15) * 60_000L;
        OreStats stats = oreStats.computeIfAbsent(player.getUniqueId(), ignored -> new OreStats());
        stats.trim(now - windowMs);

        if (COMMON_ROCK.contains(type)) stats.common++;
        if (RARE_ORES.contains(type)) stats.rare++;
        stats.events.addLast(new OreEvent(now, COMMON_ROCK.contains(type), RARE_ORES.contains(type)));

        int minimum = getConfig().getInt("xray.minimum-stone-breaks", 80);
        double ratioLimit = getConfig().getDouble("xray.rare-ore-ratio-percent", 8.0);
        if (stats.common < minimum || stats.rare < 3) return;

        double ratio = 100.0 * stats.rare / Math.max(1, stats.common);
        if (ratio >= ratioLimit) {
            alert(player, "Profil de minage inhabituel pouvant évoquer X-Ray (ratio "
                    + String.format("%.1f", ratio) + "%). Vérification staff recommandée.");
            // Empêche une alerte à chaque bloc : réinitialisation de la fenêtre d'alerte.
            stats.common = 0;
            stats.rare = 0;
            stats.events.clear();
        }
    }

    private void alert(Player player, String detail) {
        String prefix = color(getConfig().getString("alerts.prefix", "&8[&bNovaAC&8] "));
        String message = prefix + ChatColor.YELLOW + player.getName() + ChatColor.GRAY + ": " + detail;
        if (getConfig().getBoolean("alerts.console", true)) {
            getLogger().warning(ChatColor.stripColor(message));
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("novaanticheat.alerts")) online.sendMessage(message);
        }
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("novaac")) return false;
        if (!sender.hasPermission("novaanticheat.admin")) {
            sender.sendMessage(ChatColor.RED + "Tu n'as pas la permission.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Configuration NovaAntiCheat rechargée.");
            return true;
        }
        sender.sendMessage(ChatColor.AQUA + "NovaAntiCheat " + ChatColor.GRAY + "- /novaac reload");
        return true;
    }

    private static final class OreStats {
        int common;
        int rare;
        final Deque<OreEvent> events = new ArrayDeque<>();

        void trim(long cutoff) {
            while (!events.isEmpty() && events.peekFirst().time < cutoff) {
                OreEvent event = events.removeFirst();
                if (event.common) common = Math.max(0, common - 1);
                if (event.rare) rare = Math.max(0, rare - 1);
            }
        }
    }

    private record OreEvent(long time, boolean common, boolean rare) {}
}
