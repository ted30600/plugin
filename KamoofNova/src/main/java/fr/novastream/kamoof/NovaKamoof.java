package fr.novastream.kamoof;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class NovaKamoof extends JavaPlugin implements Listener {
    private NamespacedKey headKey;
    private final Set<UUID> disguised = new HashSet<>();
    private final Set<UUID> noHeadNextDeath = new HashSet<>();
    private final Set<UUID> tripleHeadNextDeath = new HashSet<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        headKey = new NamespacedKey(this, "victim");
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("undisguise")).setExecutor((s,c,l,a) -> {
            if (s instanceof Player p) undisguise(p);
            return true;
        });
        Objects.requireNonNull(getCommand("novakamoof")).setExecutor((s,c,l,a) -> { command(s,a); return true; });
        getLogger().info("NovaKamoof 1.0.0 activé.");
    }

    private void command(org.bukkit.command.CommandSender s, String[] a) {
        if (!(s instanceof Player p)) return;
        if (a.length == 0) { p.sendMessage("§d/novakamoof givehead <joueur>"); p.sendMessage("§d/novakamoof ritual"); p.sendMessage("§d/novakamoof pacte-ensanglante"); p.sendMessage("§d/novakamoof pacte-oublie"); return; }
        if (a[0].equalsIgnoreCase("givehead") && a.length > 1) {
            Player t=Bukkit.getPlayerExact(a[1]);
            if(t==null){p.sendMessage("§cJoueur introuvable.");return;}
            p.getInventory().addItem(createHead(t)); return;
        }
        if (a[0].equalsIgnoreCase("ritual")) {
            if(countHeads(p)<9){p.sendMessage("§cIl faut 9 têtes de joueurs.");return;}
            removeHeads(p,9);
            p.sendMessage("§dChoisis un pacte avec §f/novakamoof pacte-ensanglante §dou §f/novakamoof pacte-oublie");
        } else if(a[0].equalsIgnoreCase("pacte-ensanglante")) bloodPact(p);
        else if(a[0].equalsIgnoreCase("pacte-oublie")) forgottenPact(p);
    }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        Player p=e.getEntity();
        if(disguised.contains(p.getUniqueId())) undisguise(p);
        if(noHeadNextDeath.remove(p.getUniqueId())) return;
        int n=tripleHeadNextDeath.remove(p.getUniqueId())?3:1;
        for(int i=0;i<n;i++) e.getDrops().add(createHead(p));
    }

    @EventHandler public void onUse(PlayerInteractEvent e) {
        if(e.getAction()!=Action.RIGHT_CLICK_AIR && e.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item=e.getItem();
        if(!isNovaHead(item)) return;
        String raw=item.getItemMeta().getPersistentDataContainer().get(headKey,PersistentDataType.STRING);
        if(raw==null)return;
        OfflinePlayer victim=Bukkit.getOfflinePlayer(UUID.fromString(raw));
        if(victim.getName()==null)return;
        e.setCancelled(true);
        disguise(e.getPlayer(),victim.getName());
    }

    private void disguise(Player p,String name){
        try{
            callNickApi("setNick", p, name);
            callNickApi("setSkin", p, name);
            callNickApi("setUniqueId", p, name);
            callNickApi("setProfileName", p, name);
            callNickApi("refreshPlayer", p);
            disguised.add(p.getUniqueId());
            p.sendMessage(color(getConfig().getString("messages.disguised").replace("%player%",name)));
        }catch(Throwable t){p.sendMessage("§cImpossible d'appliquer le déguisement. Vérifie NickAPI.");getLogger().warning(t.toString());}
    }

    private void undisguise(Player p){
        if(!disguised.remove(p.getUniqueId())) return;
        try{callNickApi("resetAll", p);callNickApi("refreshPlayer", p);}catch(Throwable t){getLogger().warning(t.toString());}
        p.sendMessage(color(getConfig().getString("messages.undisguised")));
    }

    private void callNickApi(String method, Object... args) throws Exception {
        Class<?> api = Class.forName("xyz.haoshoku.nick.api.NickAPI");
        Class<?>[] types = new Class<?>[args.length];
        for (int i=0;i<args.length;i++) types[i] = args[i] instanceof Player ? Player.class : String.class;
        api.getMethod(method, types).invoke(null, args);
    }

    private ItemStack createHead(Player p){
        ItemStack item=new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta=(SkullMeta)item.getItemMeta();
        meta.setOwningPlayer(p);
        meta.setDisplayName("§6Tête de §e"+p.getName());
        meta.getPersistentDataContainer().set(headKey,PersistentDataType.STRING,p.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private boolean isNovaHead(ItemStack i){
        return i!=null && i.getType()==Material.PLAYER_HEAD && i.hasItemMeta() &&
            i.getItemMeta().getPersistentDataContainer().has(headKey,PersistentDataType.STRING);
    }

    private int countHeads(Player p){
        int n=0;
        for(ItemStack i:p.getInventory().getContents()) if(isNovaHead(i)) n+=i.getAmount();
        return n;
    }

    private void removeHeads(Player p,int amount){
        for(int slot=0;slot<p.getInventory().getSize() && amount>0;slot++){
            ItemStack i=p.getInventory().getItem(slot);
            if(!isNovaHead(i))continue;
            int take=Math.min(amount,i.getAmount());
            i.setAmount(i.getAmount()-take);
            amount-=take;
        }
    }

    private void bloodPact(Player p){
        Attribute a=Attribute.MAX_HEALTH;
        double max=p.getAttribute(a).getBaseValue();
        p.getAttribute(a).setBaseValue(max+10.0);
        p.setHealth(Math.min(p.getHealth()+10.0,p.getAttribute(a).getValue()));
        tripleHeadNextDeath.add(p.getUniqueId());
        p.sendMessage(color(getConfig().getString("messages.pact-bloody")));
    }

    private void forgottenPact(Player p){
        noHeadNextDeath.add(p.getUniqueId());
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS,Integer.MAX_VALUE,0,false,false,true));
        p.sendMessage(color(getConfig().getString("messages.pact-forgotten")));
    }

    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s);}
}