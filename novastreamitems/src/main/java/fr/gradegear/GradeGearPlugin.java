package fr.gradegear;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GradeGearPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String EMBEDDED_PACK = "resource-pack/NovaStream-Items-resource-pack.zip";
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "NovaStream Items" + ChatColor.DARK_GRAY + "] " + ChatColor.RESET;

    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private NamespacedKey itemIdKey;

    @Override
    public void onEnable() {
        itemIdKey = new NamespacedKey(this, "item_id");
        saveResource(EMBEDDED_PACK, false);
        loadDefinitions();
        Bukkit.getPluginManager().registerEvents(this, this);

        if (getCommand("customitems") != null) {
            getCommand("customitems").setExecutor(this);
            getCommand("customitems").setTabCompleter(this);
        }

        registerRecipes();
        getLogger().info("NovaStream Items activé : " + definitions.size() + " objets personnalisés.");
        getLogger().info("Pack de ressources intégré : " + new File(getDataFolder(), EMBEDDED_PACK).getPath());
        if (getResourcePackUrl().isBlank()) {
            getLogger().warning("Aucune URL de pack configurée. Définis resource-pack-url dans config.yml.");
        }
    }

    @Override
    public void onDisable() {
        definitions.clear();
    }

    private void loadDefinitions() {
        definitions.clear();
        for (Grade grade : Grade.values()) {
            for (Category category : Category.values()) {
                String id = grade.key + "_" + category.key;
                int modelData = 1000 + grade.ordinal() * 100 + category.ordinal() + 1;
                definitions.put(id, new Definition(id, grade.displayName + " " + category.displayName,
                        category.material, modelData, grade, category));
            }
        }
    }

    private void registerRecipes() {
        for (Definition definition : definitions.values()) {
            NamespacedKey key = new NamespacedKey(this, "recipe_" + definition.id);
            Bukkit.removeRecipe(key);
            ShapedRecipe recipe = new ShapedRecipe(key, createItem(definition));
            recipe.shape(definition.category.recipeShape);
            recipe.setIngredient('G', definition.grade.recipeMaterial);
            recipe.setIngredient('S', Material.STICK);
            Bukkit.addRecipe(recipe);
        }
    }

    private ItemStack createItem(Definition definition) {
        ItemStack item = new ItemStack(definition.category.material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(definition.grade.color + definition.displayName);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of(definition.id));
        meta.setCustomModelDataComponent(customModelData);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.setLore(List.of(
                ChatColor.DARK_GRAY + "NovaStream Items",
                ChatColor.GRAY + "Grade : " + definition.grade.color + definition.grade.displayName,
                ChatColor.GRAY + "Type : " + ChatColor.WHITE + definition.category.displayName,
                ChatColor.DARK_GRAY + "ID : " + definition.id
        ));
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, definition.id);

        int level = definition.grade.enchantmentLevel;
        switch (definition.category) {
            case PICKAXE -> {
                meta.addEnchant(Enchantment.EFFICIENCY, level, true);
                meta.addEnchant(Enchantment.UNBREAKING, level, true);
            }
            case SWORD -> {
                meta.addEnchant(Enchantment.SHARPNESS, level, true);
                meta.addEnchant(Enchantment.UNBREAKING, level, true);
            }
            case AXE -> {
                meta.addEnchant(Enchantment.EFFICIENCY, level, true);
                meta.addEnchant(Enchantment.SHARPNESS, Math.max(1, level - 1), true);
                meta.addEnchant(Enchantment.UNBREAKING, level, true);
            }
            case HELMET, CHESTPLATE, LEGGINGS, BOOTS -> {
                meta.addEnchant(Enchantment.PROTECTION, level, true);
                meta.addEnchant(Enchantment.UNBREAKING, level, true);
            }
            case BLOCK -> { }
        }

        item.setItemMeta(meta);
        return item;
    }

    private void sendPack(Player player) {
        String url = getResourcePackUrl();
        if (url.isBlank() || !getConfig().getBoolean("send-pack-on-join", true)) return;
        String prompt = getConfig().getString("resource-pack-prompt",
                "Télécharge le pack NovaStream Items pour voir les textures personnalisées.");
        Bukkit.getScheduler().runTaskLater(this, () -> player.setResourcePack(url, prompt), 40L);
    }

    private String getResourcePackUrl() {
        return getConfig().getString("resource-pack-url", "").trim();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sendPack(event.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("novastreamitems.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Tu n'as pas la permission.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                sender.sendMessage(PREFIX + ChatColor.AQUA + "Objets disponibles :");
                sender.sendMessage(ChatColor.GRAY + String.join(", ", definitions.keySet()));
            }
            case "give" -> giveItem(sender, args);
            case "pack" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Cette commande doit être exécutée par un joueur.");
                    return true;
                }
                String url = getResourcePackUrl();
                if (url.isBlank()) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Aucune URL de pack n'est configurée.");
                    return true;
                }
                player.setResourcePack(url, getConfig().getString("resource-pack-prompt", "Pack NovaStream Items"));
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Pack envoyé.");
            }
            case "reload" -> {
                reloadConfig();
                loadDefinitions();
                registerRecipes();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuration et recettes rechargées.");
            }
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage : /customitems give <joueur> <objet> [quantité]");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Joueur introuvable ou hors ligne.");
            return;
        }

        Definition definition = definitions.get(args[2].toLowerCase(Locale.ROOT));
        if (definition == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Objet inconnu. Utilise /customitems list.");
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(PREFIX + ChatColor.RED + "La quantité doit être un nombre.");
                return;
            }
        }

        ItemStack item = createItem(definition);
        item.setAmount(amount);
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        sender.sendMessage(PREFIX + ChatColor.GREEN + amount + "x " + definition.displayName
                + ChatColor.GREEN + " donné à " + target.getName() + ".");
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(PREFIX + ChatColor.AQUA + "NovaStream Items — Équipements personnalisés");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " list " + ChatColor.GRAY + "Voir les IDs des objets");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " give <joueur> <objet> [quantité]");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " pack " + ChatColor.GRAY + "Recevoir le pack configuré");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload " + ChatColor.GRAY + "Recharger la configuration");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return partial(args[0], List.of("help", "list", "give", "pack", "reload"));
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return partial(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return partial(args[2], new ArrayList<>(definitions.keySet()));
        }
        return Collections.emptyList();
    }

    private List<String> partial(String input, List<String> values) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private record Definition(String id, String displayName, Material material, int modelData, Grade grade, Category category) { }

    private enum Grade {
        COMMON("commun", "Commun", ChatColor.WHITE, Material.IRON_INGOT, 1),
        RARE("rare", "Rare", ChatColor.BLUE, Material.GOLD_INGOT, 2),
        EPIC("epique", "Épique", ChatColor.LIGHT_PURPLE, Material.DIAMOND, 3),
        LEGENDARY("legendaire", "Légendaire", ChatColor.GOLD, Material.NETHERITE_INGOT, 4);

        private final String key;
        private final String displayName;
        private final ChatColor color;
        private final Material recipeMaterial;
        private final int enchantmentLevel;

        Grade(String key, String displayName, ChatColor color, Material recipeMaterial, int enchantmentLevel) {
            this.key = key;
            this.displayName = displayName;
            this.color = color;
            this.recipeMaterial = recipeMaterial;
            this.enchantmentLevel = enchantmentLevel;
        }
    }

    private enum Category {
        PICKAXE("pioche", "Pioche", Material.NETHERITE_PICKAXE, new String[]{"GGG", " S", " S"}),
        SWORD("epee", "Épée", Material.NETHERITE_SWORD, new String[]{" G", " G", " S"}),
        AXE("hache", "Hache", Material.NETHERITE_AXE, new String[]{"GG", "GS", " S"}),
        HELMET("casque", "Casque", Material.NETHERITE_HELMET, new String[]{"GGG", "G G", ""}),
        CHESTPLATE("plastron", "Plastron", Material.NETHERITE_CHESTPLATE, new String[]{"G G", "GGG", "GGG"}),
        LEGGINGS("jambieres", "Jambières", Material.NETHERITE_LEGGINGS, new String[]{"GGG", "G G", "G G"}),
        BOOTS("bottes", "Bottes", Material.NETHERITE_BOOTS, new String[]{"G G", "G G", ""}),
        BLOCK("bloc", "Bloc", Material.AMETHYST_BLOCK, new String[]{"GGG", "GGG", "GGG"});

        private final String key;
        private final String displayName;
        private final Material material;
        private final String[] recipeShape;

        Category(String key, String displayName, Material material, String[] recipeShape) {
            this.key = key;
            this.displayName = displayName;
            this.material = material;
            this.recipeShape = recipeShape;
        }
    }
}
