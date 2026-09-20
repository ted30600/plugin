package fr.ted30600.banhammer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BanHammerPlugin extends JavaPlugin implements Listener {
    private NamespacedKey hammerKey;

    private static final Component BAN_TITLE =
            Component.text("Ban Hammer", NamedTextColor.RED);
    private static final Component UNBAN_TITLE =
            Component.text("Unban Hammer", NamedTextColor.GREEN);

    // Joueurs actuellement sous contrat de ban.
    private final Map<UUID, BanContract> banContracts = new HashMap<>();

    // Contrats d'unban actifs, indexés par le joueur qui les a activés.
    private final Map<String, UnbanContract> unbanContracts = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        hammerKey = new NamespacedKey(this, "hammer_type");
        Bukkit.getPluginManager().registerEvents(this, this);

        registerRecipes();

        Objects.requireNonNull(getCommand("banhammer"))
                .setExecutor(this::command);
        Objects.requireNonNull(getCommand("unbanhammer"))
                .setExecutor(this::command);

        getLogger().info("BanHammer activé.");
    }

    private boolean command(CommandSender sender, Command command,
                            String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        if (!player.hasPermission("banhammer.use")) {
            player.sendMessage(Component.text(
                    "Tu n'as pas la permission.", NamedTextColor.RED));
            return true;
        }

        String type = label.equalsIgnoreCase("banhammer")
                ? "ban" : "unban";

        player.getInventory().addItem(createHammer(type));
        player.sendMessage(Component.text(
                "Marteau donné.", NamedTextColor.GREEN));
        return true;
    }

    private ItemStack createHammer(String type) {
        String path = type.equals("ban")
                ? "ban-hammer" : "unban-hammer";

        Material material = Material.valueOf(
                Objects.requireNonNull(
                        getConfig().getString(path + ".material")));

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String name = getConfig().getString(path + ".name", type);
        meta.displayName(Component.text(name.replaceAll("§.", "")));

        meta.getPersistentDataContainer().set(
                hammerKey, PersistentDataType.STRING, type);

        item.setItemMeta(meta);
        return item;
    }

    private void removeOneHammer(Player player, String type) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || !item.hasItemMeta()) continue;

            String value = item.getItemMeta()
                    .getPersistentDataContainer()
                    .get(hammerKey, PersistentDataType.STRING);

            if (!type.equals(value)) continue;

            if (item.getAmount() <= 1) {
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - 1);
            }
            return;
        }
    }

    private void registerRecipes() {
        registerRecipe("ban", "ban_hammer_recipe", "ban-hammer");
        registerRecipe("unban", "unban_hammer_recipe", "unban-hammer");
    }

    private void registerRecipe(String type, String keyName, String path) {
        String[] shape = getConfig().getStringList(
                path + ".recipe.shape").toArray(new String[0]);

        ShapedRecipe recipe = new ShapedRecipe(
                new NamespacedKey(this, keyName),
                createHammer(type));

        recipe.shape(shape);

        var section = getConfig().getConfigurationSection(
                path + ".recipe.ingredients");

        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.valueOf(
                        Objects.requireNonNull(getConfig().getString(
                                path + ".recipe.ingredients." + key)));
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
            player.sendMessage(Component.text(
                    "Tu n'as pas la permission.", NamedTextColor.RED));
            return;
        }

        if (type.equals("ban")) {
            openBanGui(player);
        } else {
            openUnbanGui(player);
        }
    }

    private void openBanGui(Player viewer) {
        Inventory inventory = Bukkit.createInventory(
                null, 27, BAN_TITLE);

        int slot = 0;

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer)) continue;
            if (slot >= 27) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            meta.setOwningPlayer(target);
            meta.displayName(Component.text(
                    target.getName(), NamedTextColor.WHITE));
            meta.lore(List.of(
                    Component.text(
                            "Clique pour lancer le contrat de 30 minutes",
                            NamedTextColor.RED),
                    Component.text(
                            "Le joueur doit mourir avant la fin.",
                            NamedTextColor.GRAY)
            ));

            head.setItemMeta(meta);
            inventory.setItem(slot++, head);
        }

        viewer.openInventory(inventory);
    }

    private void openUnbanGui(Player viewer) {
        Inventory inventory = Bukkit.createInventory(
                null, 27, UNBAN_TITLE);

        int slot = 0;

        for (BanList.Entry<?> entry :
                Bukkit.getBanList(BanList.Type.NAME).getEntries()) {

            if (slot >= 27) break;

            String name = entry.getTarget();
            OfflinePlayer target = Bukkit.getOfflinePlayer(name);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            meta.setOwningPlayer(target);
            meta.displayName(Component.text(
                    name, NamedTextColor.WHITE));
            meta.lore(List.of(
                    Component.text(
                            "Clique pour lancer le contrat de 10 minutes",
                            NamedTextColor.GREEN),
                    Component.text(
                            "Le bloc devra être cassé.",
                            NamedTextColor.GRAY)
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
        if (clicked == null ||
                clicked.getType() != Material.PLAYER_HEAD) return;

        if (!(clicked.getItemMeta() instanceof SkullMeta meta)) return;

        OfflinePlayer target = meta.getOwningPlayer();
        if (target == null || target.getName() == null) return;

        String name = target.getName();

        if (banGui) {
            startBanContract(player, target);
        } else {
            startUnbanContract(player, name);
        }
    }

    /*
     * BAN:
     * - Le marteau est consommé dès que le contrat est activé.
     * - 30 minutes pour tuer la cible.
     * - À sa mort, elle est bannie automatiquement.
     * - Si 30 minutes passent, le contrat expire.
     */
    private void startBanContract(Player creator, OfflinePlayer target) {
        if (!creator.hasPermission("banhammer.ban")) return;

        Player online = Bukkit.getPlayerExact(target.getName());
        if (online == null) {
            creator.sendMessage(Component.text(
                    "Ce joueur n'est plus connecté.", NamedTextColor.RED));
            return;
        }

        UUID uuid = online.getUniqueId();

        if (banContracts.containsKey(uuid)) {
            creator.sendMessage(Component.text(
                    "Ce joueur a déjà un contrat actif.",
                    NamedTextColor.RED));
            return;
        }

        removeOneHammer(creator, "ban");

        long end = System.currentTimeMillis() + 30L * 60L * 1000L;
        BanContract contract = new BanContract(
                uuid, online.getName(), creator.getUniqueId(), end);

        banContracts.put(uuid, contract);

        creator.closeInventory();

        creator.sendMessage(Component.text(
                "Contrat activé sur " + online.getName()
                        + " : 30 minutes.", NamedTextColor.RED));

        online.sendMessage(Component.text(
                "⚠ Un contrat de Ban Hammer est actif sur toi !",
                NamedTextColor.RED));

        contract.task = Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {
                    long remaining =
                            contract.endTime - System.currentTimeMillis();

                    if (remaining <= 0) {
                        cancelBanContract(uuid);
                        return;
                    }

                    long minutes = remaining / 60000L;
                    long seconds = (remaining / 1000L) % 60L;

                    Player targetPlayer = Bukkit.getPlayer(uuid);
                    if (targetPlayer != null) {
                        targetPlayer.sendActionBar(Component.text(
                                "Ban Hammer : " + minutes + "m "
                                        + String.format("%02d", seconds),
                                NamedTextColor.RED));
                    }
                },
                0L, 20L);
    }

    @EventHandler
    public void onTargetDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        BanContract contract = banContracts.get(victim.getUniqueId());

        if (contract == null) return;

        if (System.currentTimeMillis() > contract.endTime) {
            cancelBanContract(victim.getUniqueId());
            return;
        }

        Bukkit.getBanList(BanList.Type.NAME).addBan(
                victim.getName(),
                "Banni par un contrat Ban Hammer",
                null,
                Bukkit.getOfflinePlayer(contract.creator).getName());

        cancelBanContract(victim.getUniqueId());

        Bukkit.getScheduler().runTask(this, () -> {
            Player online = Bukkit.getPlayer(victim.getUniqueId());
            if (online != null) {
                online.kick(Component.text(
                        "Tu as été banni par le Ban Hammer.",
                        NamedTextColor.RED));
            }
        });
    }

    private void cancelBanContract(UUID uuid) {
        BanContract contract = banContracts.remove(uuid);

        if (contract != null && contract.task != null) {
            contract.task.cancel();
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendActionBar(Component.text(
                    "Contrat Ban Hammer terminé.",
                    NamedTextColor.GRAY));
        }
    }

    /*
     * UNBAN:
     * - Le marteau est consommé immédiatement.
     * - Un bloc temporaire apparaît à côté du joueur qui active le contrat.
     * - Les autres joueurs doivent casser ce bloc.
     * - Ils ont 10 minutes.
     * - Le joueur banni est débanni si le bloc est cassé à temps.
     */
    private void startUnbanContract(Player creator, String targetName) {
        if (!creator.hasPermission("banhammer.unban")) return;

        if (unbanContracts.containsKey(targetName)) {
            creator.sendMessage(Component.text(
                    "Ce joueur a déjà un contrat d'unban.",
                    NamedTextColor.RED));
            return;
        }

        if (Bukkit.getBanList(BanList.Type.NAME)
                .getBanEntry(targetName) == null) {
            creator.sendMessage(Component.text(
                    "Ce joueur n'est plus banni.",
                    NamedTextColor.RED));
            return;
        }

        removeOneHammer(creator, "unban");

        Block block = findNearbyAirBlock(creator.getLocation());
        Material temporaryMaterial = Material.valueOf(
                getConfig().getString(
                        "unban-contract.block", "RESPAWN_ANCHOR"));

        String oldData = block.getBlockData().getAsString();

        block.setType(temporaryMaterial);

        long end = System.currentTimeMillis() + 10L * 60L * 1000L;

        UnbanContract contract = new UnbanContract(
                targetName,
                creator.getUniqueId(),
                block.getLocation(),
                oldData,
                end);

        unbanContracts.put(targetName, contract);
        creator.closeInventory();

        creator.sendMessage(Component.text(
                "Contrat d'unban activé pour " + targetName
                        + " : 10 minutes.", NamedTextColor.GREEN));

        contract.task = Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {
                    long remaining =
                            contract.endTime - System.currentTimeMillis();

                    if (remaining <= 0) {
                        expireUnbanContract(targetName);
                        return;
                    }

                    long minutes = remaining / 60000L;
                    long seconds = (remaining / 1000L) % 60L;

                    Player p = Bukkit.getPlayer(contract.creator);
                    if (p != null) {
                        p.sendActionBar(Component.text(
                                "Unban : " + minutes + "m "
                                        + String.format("%02d", seconds),
                                NamedTextColor.GREEN));
                    }
                },
                0L, 20L);
    }

    private Block findNearbyAirBlock(Location location) {
        Block base = location.getBlock();

        Block[] candidates = {
                base.getRelative(1, 0, 0),
                base.getRelative(-1, 0, 0),
                base.getRelative(0, 0, 1),
                base.getRelative(0, 0, -1)
        };

        for (Block block : candidates) {
            if (block.getType().isAir()) return block;
        }

        return base.getRelative(1, 0, 0);
    }

    @EventHandler
    public void onContractBlockBreak(BlockBreakEvent event) {
        Player breaker = event.getPlayer();

        for (UnbanContract contract :
                List.copyOf(unbanContracts.values())) {

            if (!sameLocation(event.getBlock().getLocation(),
                    contract.blockLocation)) {
                continue;
            }

            event.setCancelled(true);

            // Le créateur doit laisser les autres joueurs casser le bloc.
            if (breaker.getUniqueId().equals(contract.creator)) {
                breaker.sendMessage(Component.text(
                        "Un autre joueur doit casser ce bloc.",
                        NamedTextColor.RED));
                return;
            }

            if (System.currentTimeMillis() > contract.endTime) {
                expireUnbanContract(contract.targetName);
                return;
            }

            event.setDropItems(false);

            Bukkit.getBanList(BanList.Type.NAME)
                    .pardon(contract.targetName);

            restoreBlock(contract);
            if (contract.task != null) contract.task.cancel();
            unbanContracts.remove(contract.targetName);

            breaker.sendMessage(Component.text(
                    contract.targetName + " a été débanni !",
                    NamedTextColor.GREEN));

            Player creator = Bukkit.getPlayer(contract.creator);
            if (creator != null) {
                creator.sendMessage(Component.text(
                        "Le bloc a été cassé : "
                                + contract.targetName + " est débanni.",
                        NamedTextColor.GREEN));
            }
            return;
        }
    }

    private boolean sameLocation(Location a, Location b) {
        return a.getWorld() != null && b.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private void expireUnbanContract(String targetName) {
        UnbanContract contract = unbanContracts.remove(targetName);
        if (contract == null) return;

        if (contract.task != null) contract.task.cancel();
        restoreBlock(contract);

        Player creator = Bukkit.getPlayer(contract.creator);
        if (creator != null) {
            creator.sendMessage(Component.text(
                    "Le contrat d'unban de " + targetName
                            + " a expiré.",
                    NamedTextColor.RED));
        }
    }

    private void restoreBlock(UnbanContract contract) {
        if (contract.blockLocation.getWorld() == null) return;

        Block block = contract.blockLocation.getBlock();

        try {
            block.setBlockData(Bukkit.createBlockData(contract.oldBlockData));
        } catch (IllegalArgumentException ignored) {
            block.setType(Material.AIR);
        }
    }

    private static final class BanContract {
        final UUID target;
        final String targetName;
        final UUID creator;
        final long endTime;
        BukkitTask task;

        BanContract(UUID target, String targetName,
                    UUID creator, long endTime) {
            this.target = target;
            this.targetName = targetName;
            this.creator = creator;
            this.endTime = endTime;
        }
    }

    private static final class UnbanContract {
        final String targetName;
        final UUID creator;
        final Location blockLocation;
        final String oldBlockData;
        final long endTime;
        BukkitTask task;

        UnbanContract(String targetName, UUID creator,
                      Location blockLocation, String oldBlockData,
                      long endTime) {
            this.targetName = targetName;
            this.creator = creator;
            this.blockLocation = blockLocation;
            this.oldBlockData = oldBlockData;
            this.endTime = endTime;
        }
    }
}
