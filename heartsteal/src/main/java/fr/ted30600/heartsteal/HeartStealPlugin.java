package fr.ted30600.heartsteal;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class HeartStealPlugin extends JavaPlugin implements Listener {
    private static final int DEFAULT_HEARTS = 10;
    private static final int MIN_HEARTS = 1;
    private static final int MAX_HEARTS = 40;
    private NamespacedKey heartsKey;

    @Override
    public void onEnable() {
        heartsKey = new NamespacedKey(this, "permanent_hearts");
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("HeartSteal 26.2 active.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int hearts = getHearts(player);
        applyMaxHealth(player, hearts);
        player.setHealth(Math.min(player.getHealth(), hearts * 2.0));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        int victimHearts = getHearts(victim);
        int killerHearts = getHearts(killer);
        int newVictimHearts = Math.max(MIN_HEARTS, victimHearts - 1);
        int newKillerHearts = Math.min(MAX_HEARTS, killerHearts + 1);

        setHearts(victim, newVictimHearts);
        setHearts(killer, newKillerHearts);
        applyMaxHealth(victim, newVictimHearts);
        applyMaxHealth(killer, newKillerHearts);

        victim.sendMessage("§cTu as perdu 1 coeur. §7Il t'en reste §c" + newVictimHearts + "§7.");
        killer.sendMessage("§aTu as gagné 1 coeur permanent ! §7Tu en as maintenant §c" + newKillerHearts + "§7.");

        Bukkit.getScheduler().runTask(this, () -> {
            if (victim.isOnline()) applyMaxHealth(victim, newVictimHearts);
            if (killer.isOnline()) applyMaxHealth(killer, newKillerHearts);
        });
    }

    private int getHearts(Player player) {
        Integer value = player.getPersistentDataContainer().get(heartsKey, PersistentDataType.INTEGER);
        if (value == null) {
            value = DEFAULT_HEARTS;
            player.getPersistentDataContainer().set(heartsKey, PersistentDataType.INTEGER, value);
        }
        return Math.max(MIN_HEARTS, Math.min(MAX_HEARTS, value));
    }

    private void setHearts(Player player, int hearts) {
        int clamped = Math.max(MIN_HEARTS, Math.min(MAX_HEARTS, hearts));
        player.getPersistentDataContainer().set(heartsKey, PersistentDataType.INTEGER, clamped);
    }

    private void applyMaxHealth(Player player, int hearts) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;
        double maxHealth = hearts * 2.0;
        attribute.setBaseValue(maxHealth);
        if (player.isOnline() && player.getHealth() > maxHealth) player.setHealth(maxHealth);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("heartsteal")) return false;
        if (!sender.hasPermission("heartsteal.admin")) {
            sender.sendMessage("§cTu n'as pas la permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/heartsteal reset <joueur>");
            sender.sendMessage("§e/heartsteal set <joueur> <coeurs>");
            sender.sendMessage("§e/heartsteal give <joueur> [nombre]");
            sender.sendMessage("§e/heartsteal take <joueur> [nombre]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable ou hors ligne.");
            return true;
        }

        try {
            String action = args[0].toLowerCase();
            int current = getHearts(target);
            int amount = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
            int result;
            switch (action) {
                case "reset" -> result = DEFAULT_HEARTS;
                case "set" -> result = amount;
                case "give" -> result = current + amount;
                case "take" -> result = current - amount;
                default -> {
                    sender.sendMessage("§cAction inconnue.");
                    return true;
                }
            }
            result = Math.max(MIN_HEARTS, Math.min(MAX_HEARTS, result));
            setHearts(target, result);
            applyMaxHealth(target, result);
            target.setHealth(Math.min(target.getHealth(), result * 2.0));
            sender.sendMessage("§a" + target.getName() + " a maintenant " + result + " coeurs.");
            return true;
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cLe nombre de coeurs doit être un entier.");
            return true;
        }
    }
}
