package fr.novastream.novaanticheat;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NovaAntiCheat extends JavaPlugin implements Listener {
    private final Map<UUID, Map<String, Integer>> violations = new HashMap<>();
    private final Map<UUID, Location> sampledLocations = new HashMap<>();
    private final Map<UUID, Long> teleportGraceUntil = new HashMap<>();
    private final Map<UUID, Deque<Long>> swings = new HashMap<>();
    private final Map<UUID, Deque<Long>> attacks = new HashMap<>();
    private final Map<UUID, Deque<Long>> recentHits = new HashMap<>();
    private final Map<UUID, OreStats> oreStats = new HashMap<>();
    private final Map<UUID, KnockbackProbe> knockback = new HashMap<>();
    private final Set<UUID> pendingHitChecks = new HashSet<>();
    private BukkitTask sampler;

    private static final Set<Material> COMMON_ROCK = EnumSet.of(
            Material.STONE, Material.DEEPSLATE, Material.ANDESITE, Material.DIORITE,
            Material.GRANITE, Material.TUFF, Material.COBBLED_DEEPSLATE, Material.COBBLESTONE
    );
    private static final Set<Material> RARE_ORES = EnumSet.of(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.ANCIENT_DEBRIS
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        sampler = Bukkit.getScheduler().runTaskTimer(this, this::samplePlayers, 1L, 1L);
        getLogger().info("NovaAntiCheat chargé. Les sanctions automatiques restent désactivées par défaut.");
    }

    @Override
    public void onDisable() {
        if (sampler != null) sampler.cancel();
        violations.clear();
        sampledLocations.clear();
        teleportGraceUntil.clear();
        swings.clear();
        attacks.clear();
        recentHits.clear();
        oreStats.clear();
        knockback.clear();
        pendingHitChecks.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sampledLocations.put(event.getPlayer().getUniqueId(), event.getPlayer().getLocation().clone());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        violations.remove(id);
        sampledLocations.remove(id);
        teleportGraceUntil.remove(id);
        swings.remove(id);
        attacks.remove(id);
        recentHits.remove(id);
        oreStats.remove(id);
        knockback.remove(id);
        pendingHitChecks.remove(id);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        teleportGraceUntil.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 1500L);
        if (event.getTo() != null) sampledLocations.put(event.getPlayer().getUniqueId(), event.getTo().clone());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!getConfig().getBoolean("phase.enabled", true) || event.getTo() == null) return;
        Player player = event.getPlayer();
        if (isExempt(player)) return;
        Location to = event.getTo();
        if (event.getFrom().getWorld() != to.getWorld()) return;
        if (event.getFrom().distanceSquared(to) < 0.0001) return;

        // Check collision only at the player's feet and head; do not punish a single collision sample.
        Location feet = to.clone();
        Location head = to.clone().add(0, Math.max(0.2, player.getHeight() - 0.2), 0);
        if (isSolidNonPassable(feet) || isSolidNonPassable(head)) {
            flag(player, "Phase", "déplacement dans un bloc solide", 2);
        }
    }

    private boolean isSolidNonPassable(Location location) {
        Block block = location.getBlock();
        return !block.isPassable() && block.getType().isSolid();
    }

    private void samplePlayers() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Location current = player.getLocation();
            Location previous = sampledLocations.put(id, current.clone());
            if (previous == null || previous.getWorld() != current.getWorld()) continue;
            if (now < teleportGraceUntil.getOrDefault(id, 0L) || isExempt(player)) continue;

            double horizontal = Math.hypot(current.getX() - previous.getX(), current.getZ() - previous.getZ());
            if (getConfig().getBoolean("movement.speed.enabled", true)) {
                double max = getConfig().getDouble("movement.speed.max-horizontal-per-tick", 0.85);
                if (horizontal > max) flag(player, "Speed", String.format(java.util.Locale.US, "%.2f blocs/tick", horizontal), 2);
            }

            if (getConfig().getBoolean("movement.fly.enabled", true)) {
                checkFly(player, previous, current);
            }

            KnockbackProbe probe = knockback.get(id);
            if (probe != null) {
                probe.samples++;
                if (probe.samples >= 5) {
                    double moved = Math.hypot(current.getX() - probe.startX, current.getZ() - probe.startZ);
                    if (moved < getConfig().getDouble("combat.knockback.min-displacement", 0.08)
                            && !player.isBlocking() && !player.isInsideVehicle() && !player.isFlying()) {
                        flag(player, "AntiKnockback", "réponse au recul très faible", 2);
                    }
                    knockback.remove(id);
                }
            }
        }
    }

    private void checkFly(Player player, Location previous, Location current) {
        if (player.isOnGround() || player.isFlying() || player.isGliding() || player.isSwimming()
                || player.isInsideVehicle() || player.isClimbing() || player.isRiptiding()) return;
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return;
        double dy = current.getY() - previous.getY();
        double vy = player.getVelocity().getY();
        // Conservative signals only; this is not a packet-level physics simulation.
        if (Math.abs(dy) < 0.015 && Math.abs(vy) < 0.035) {
            flag(player, "Fly", "suspension inhabituelle dans les airs", 3);
        }
    }

    private boolean isExempt(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return true;
        if (player.isFlying() || player.getAllowFlight() || player.isInsideVehicle()) return true;
        if (player.isGliding() || player.isSwimming() || player.isRiptiding() || player.isClimbing()) return true;
        if (player.hasPotionEffect(PotionEffectType.SPEED)
                || player.hasPotionEffect(PotionEffectType.JUMP_BOOST)
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return true;
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        if (!getConfig().getBoolean("combat.autoclicker.enabled", true)) return;
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Deque<Long> times = swings.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        times.addLast(now);
        trimTimes(times, now - 1000L);
        int cps = times.size();
        if (cps > getConfig().getInt("combat.autoclicker.max-cps", 18)) {
            flag(player, "AutoClicker", cps + " swings/s", 2);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (event.getEntity() instanceof Player victim) {
            long now = System.currentTimeMillis();
            UUID attackerId = attacker.getUniqueId();
            Deque<Long> times = attacks.computeIfAbsent(attackerId, ignored -> new ArrayDeque<>());
            times.addLast(now);
            trimTimes(times, now - 1000L);

            if (getConfig().getBoolean("combat.reach.enabled", true)) {
                double distance = attacker.getEyeLocation().distance(victim.getBoundingBox().getCenter().toLocation(victim.getWorld()));
                double limit = attacker.getGameMode() == GameMode.CREATIVE
                        ? getConfig().getDouble("combat.reach.creative-distance", 5.0)
                        : getConfig().getDouble("combat.reach.max-distance", 3.6);
                if (distance > limit) flag(attacker, "Reach", String.format(java.util.Locale.US, "%.2f blocs", distance), 2);
            }

            if (getConfig().getBoolean("combat.killaura.enabled", true)) {
                Deque<Long> hitTimes = recentHits.computeIfAbsent(attackerId, ignored -> new ArrayDeque<>());
                hitTimes.addLast(now);
                trimTimes(hitTimes, now - 1000L);
                if (hitTimes.size() >= getConfig().getInt("combat.killaura.max-hits-per-second", 16)) {
                    flag(attacker, "KillAura", hitTimes.size() + " attaques/s", 2);
                }
            }
        }
        if (event.getEntity() instanceof Player victim && getConfig().getBoolean("combat.knockback.enabled", true)) {
            // Only evaluate meaningful player-caused damage; the result is an alert heuristic.
            Vector velocity = victim.getVelocity();
            if (velocity.lengthSquared() > 0.02) {
                knockback.put(victim.getUniqueId(), new KnockbackProbe(victim.getLocation().getX(),
                        victim.getLocation().getZ(), 0));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        // Keep this event registered for future packet/velocity correlation; no direct punishment here.
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            knockback.remove(player.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!getConfig().getBoolean("xray.enabled", true)) return;
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();
        if (!COMMON_ROCK.contains(type) && !RARE_ORES.contains(type)) return;

        long now = System.currentTimeMillis();
        long windowMs = Math.max(1, getConfig().getLong("xray.window-minutes", 15)) * 60_000L;
        OreStats stats = oreStats.computeIfAbsent(player.getUniqueId(), ignored -> new OreStats());
        stats.trim(now - windowMs);
        boolean common = COMMON_ROCK.contains(type);
        boolean rare = RARE_ORES.contains(type);
        stats.events.addLast(new OreEvent(now, common, rare));
        if (common) stats.common++;
        if (rare) stats.rare++;

        int minimum = getConfig().getInt("xray.minimum-stone-breaks", 80);
        double ratioLimit = getConfig().getDouble("xray.rare-ore-ratio-percent", 8.0);
        if (stats.common < minimum || stats.rare < 3) return;
        double ratio = 100.0 * stats.rare / Math.max(1, stats.common);
        if (ratio >= ratioLimit) {
            flag(player, "XRay", String.format(java.util.Locale.US, "ratio de minerais rares %.1f%%", ratio), 1);
            stats.lastAlertAt = now;
        }
    }

    private void flag(Player player, String check, String detail, int weight) {
        if (player.hasPermission("novaanticheat.bypass")) return;
        Map<String, Integer> playerVl = violations.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        int vl = playerVl.merge(check, weight, Integer::sum);
        long now = System.currentTimeMillis();
        long previousAlert = lastAlertTime(player.getUniqueId(), check);
        long cooldown = getConfig().getLong("alerts.cooldown-seconds", 10) * 1000L;
        if (now - previousAlert >= cooldown) {
            setLastAlertTime(player.getUniqueId(), check, now);
            String prefix = color(getConfig().getString("alerts.prefix", "&8[&bNovaAC&8] "));
            String message = prefix + ChatColor.YELLOW + player.getName() + ChatColor.GRAY
                    + " [" + check + " VL " + vl + "]: " + detail;
            if (getConfig().getBoolean("alerts.console", true)) getLogger().warning(ChatColor.stripColor(message));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("novaanticheat.alerts")) online.sendMessage(message);
            }
        }
        if (getConfig().getBoolean("punishments.auto-ban", false)
                && vl >= getConfig().getInt("punishments.violation-threshold", 20)) {
            String reason = "NovaAntiCheat: détection répétée (" + check + ")";
            Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), reason, null, "NovaAntiCheat");
            player.kickPlayer(color("&cBanni : &f" + reason));
            getLogger().warning("Bannissement automatique de " + player.getName() + " (" + check + ").");
        }
    }

    private final Map<UUID, Map<String, Long>> lastAlerts = new HashMap<>();
    private long lastAlertTime(UUID id, String check) {
        return lastAlerts.computeIfAbsent(id, ignored -> new HashMap<>()).getOrDefault(check, 0L);
    }
    private void setLastAlertTime(UUID id, String check, long time) {
        lastAlerts.computeIfAbsent(id, ignored -> new HashMap<>()).put(check, time);
    }

    private void trimTimes(Deque<Long> times, long cutoff) {
        while (!times.isEmpty() && times.peekFirst() < cutoff) times.removeFirst();
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
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.AQUA + "NovaAntiCheat actif — Paper 1.21.10");
            sender.sendMessage(ChatColor.GRAY + "Sanctions automatiques : "
                    + (getConfig().getBoolean("punishments.auto-ban", false) ? ChatColor.RED + "activées" : ChatColor.GREEN + "désactivées"));
            sender.sendMessage(ChatColor.GRAY + "Joueurs suivis : " + violations.size());
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("vl")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Joueur introuvable ou hors ligne.");
                return true;
            }
            Map<String, Integer> levels = violations.getOrDefault(target.getUniqueId(), Map.of());
            sender.sendMessage(ChatColor.AQUA + "Violations de " + target.getName() + " : " + levels);
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Joueur introuvable ou hors ligne.");
                return true;
            }
            if (args[2].equalsIgnoreCase("confirm")) {
                violations.remove(target.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + "Violations réinitialisées pour " + target.getName() + ".");
            } else sender.sendMessage(ChatColor.YELLOW + "Confirme avec /novaac reset <joueur> confirm");
            return true;
        }
        sender.sendMessage(ChatColor.AQUA + "NovaAntiCheat : /novaac status | reload | vl <joueur> | reset <joueur> confirm");
        return true;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private static final class OreStats {
        int common;
        int rare;
        long lastAlertAt;
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
    private record KnockbackProbe(double startX, double startZ, int samples) {}
}
