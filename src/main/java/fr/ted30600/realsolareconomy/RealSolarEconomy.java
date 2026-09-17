package fr.ted30600.realsolareconomy;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RealSolarEconomy extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private Connection db;
    private final Map<UUID, TradeRequest> requests = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> outgoing = new ConcurrentHashMap<>();
    private final Map<UUID, String> paymentPrompts = new ConcurrentHashMap<>();
    private int tradeExpiry;

    @Override public void onEnable() {
        saveDefaultConfig(); saveResource("messages.yml", false); saveResource("shop.yml", false);
        tradeExpiry = getConfig().getInt("trade-expiry-seconds", 60);
        try { initDb(); } catch (Exception e) { getLogger().severe("Database initialization failed: " + e.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        for (String c : List.of("shop","bank","trade","rich","economy")) { PluginCommand pc=getCommand(c); if(pc!=null){pc.setExecutor(this);pc.setTabCompleter(this);} }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("RealSolarEconomy enabled for Paper 1.21.x. Bedrock clients are supported when connected through Geyser.");
    }
    @Override public void onDisable(){try{if(db!=null)db.close();}catch(Exception ignored){}}

    private void initDb() throws SQLException {
        getDataFolder().mkdirs();
        db=DriverManager.getConnection("jdbc:sqlite:"+new File(getDataFolder(),"economy.db"));
        try(Statement s=db.createStatement()){
            s.executeUpdate("PRAGMA journal_mode=WAL");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS accounts(uuid TEXT PRIMARY KEY,name TEXT NOT NULL,diamond REAL NOT NULL DEFAULT 0,netherite REAL NOT NULL DEFAULT 0,updated INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS transactions(id TEXT PRIMARY KEY,actor TEXT,target TEXT,currency TEXT,amount REAL,old_balance REAL,new_balance REAL,type TEXT,created INTEGER NOT NULL)");
        }
    }
    private void ensure(Player p) throws SQLException {
        try(PreparedStatement s=db.prepareStatement("INSERT OR IGNORE INTO accounts(uuid,name,updated) VALUES(?,?,?)")){s.setString(1,p.getUniqueId().toString());s.setString(2,p.getName());s.setLong(3,System.currentTimeMillis());s.executeUpdate();}
        try(PreparedStatement s=db.prepareStatement("UPDATE accounts SET name=?,updated=? WHERE uuid=?")){s.setString(1,p.getName());s.setLong(2,System.currentTimeMillis());s.setString(3,p.getUniqueId().toString());s.executeUpdate();}
    }
    private double balance(UUID u,String currency) throws SQLException {
        try(PreparedStatement s=db.prepareStatement("SELECT "+column(currency)+" FROM accounts WHERE uuid=?")){s.setString(1,u.toString());try(ResultSet r=s.executeQuery()){return r.next()?r.getDouble(1):0;}}
    }
    private String column(String c){return c.equalsIgnoreCase("diamond")?"diamond":"netherite";}
    private String tx(){return UUID.randomUUID().toString();}
    private void change(UUID actor, UUID target, String currency, double amount, String type, String targetName) throws SQLException {
        if(amount==0) return; String col=column(currency); db.setAutoCommit(false);
        try{
            double old=balance(target,currency), next=old+amount; if(next< -0.000001)throw new IllegalArgumentException(msg("errors.negative"));
            try(PreparedStatement s=db.prepareStatement("UPDATE accounts SET "+col+"=?,updated=? WHERE uuid=?")){s.setDouble(1,next);s.setLong(2,System.currentTimeMillis());s.setString(3,target.toString());if(s.executeUpdate()!=1)throw new SQLException("account missing");}
            try(PreparedStatement s=db.prepareStatement("INSERT INTO transactions(id,actor,target,currency,amount,old_balance,new_balance,type,created) VALUES(?,?,?,?,?,?,?,?,?)")){s.setString(1,tx());s.setString(2,actor.toString());s.setString(3,target.toString());s.setString(4,currency);s.setDouble(5,amount);s.setDouble(6,old);s.setDouble(7,next);s.setString(8,type);s.setLong(9,System.currentTimeMillis());s.executeUpdate();}
            db.commit();
        }catch(Exception e){db.rollback();if(e instanceof IllegalArgumentException iae)throw iae;throw new SQLException(e);}finally{db.setAutoCommit(true);}
    }
    private String msg(String key){String s=getConfig().getString("messages."+key); if(s==null){s=getStringFromMessages(key);} return ChatColor.translateAlternateColorCodes('&',s==null?key:s);}
    private String getStringFromMessages(String key){try{org.bukkit.configuration.file.FileConfiguration f=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(getDataFolder(),"messages.yml"));return f.getString(key,key);}catch(Exception e){return key;}}
    private void tell(CommandSender s,String key,Object... args){String m=msg(key);for(int i=0;i<args.length;i++)m=m.replace("{"+i+"}",String.valueOf(args[i]));s.sendMessage(m);}
    private ItemStack item(Material m,String name,String... lore){ItemStack i=new ItemStack(m);ItemMeta x=i.getItemMeta();x.setDisplayName(ChatColor.translateAlternateColorCodes('&',name));if(lore.length>0){List<String>l=new ArrayList<>();for(String a:lore)l.add(ChatColor.translateAlternateColorCodes('&',a));x.setLore(l);}i.setItemMeta(x);return i;}

    private void openBank(Player p){Inventory inv=Bukkit.createInventory(p,27,ChatColor.DARK_GREEN+"RealSolarEconomy • Bank");try{ensure(p);double d=balance(p.getUniqueId(),"diamond"),n=balance(p.getUniqueId(),"netherite");inv.setItem(4,item(Material.DIAMOND,"&bDiamants en banque", "&7Solde: &f"+fmt(d)));inv.setItem(11,item(Material.DIAMOND_BLOCK,"&aDéposer diamant","&7Clique: +1 diamant depuis l'inventaire"));inv.setItem(12,item(Material.GLASS,"&cRetirer diamant","&7Clique: -1 diamant vers l'inventaire"));inv.setItem(14,item(Material.NETHERITE_INGOT,"&8Netherite en banque","&7Solde: &f"+fmt(n)));inv.setItem(15,item(Material.NETHERITE_BLOCK,"&aDéposer netherite","&7Clique: +1 lingot depuis l'inventaire"));inv.setItem(16,item(Material.NETHERITE_SCRAP,"&cRetirer netherite","&7Clique: -1 lingot vers l'inventaire"));inv.setItem(20,item(Material.EMERALD,"&aPaiement","&7Clique puis écris: joueur montant monnaie"));inv.setItem(21,item(Material.CHEST,"&6Trade","&7Utilise /trade <joueur>"));inv.setItem(22,item(Material.BOOK,"&eHistorique","&7Dernières transactions"));inv.setItem(23,item(Material.GOLD_INGOT,"&6Classements","&7Ouvre /rich"));}catch(Exception e){p.sendMessage(ChatColor.RED+e.getMessage());}p.openInventory(inv);}
    private void openShop(Player p){Inventory inv=Bukkit.createInventory(p,27,ChatColor.GOLD+"RealSolarEconomy • Shop");inv.setItem(10,item(Material.DIAMOND,"&bAcheter 1 diamant","&7Prix: &f"+getConfig().getDouble("shop.diamond-buy",10)));inv.setItem(12,item(Material.DIAMOND_BLOCK,"&bVendre 1 diamant","&7Prix: &f"+getConfig().getDouble("shop.diamond-sell",5)));inv.setItem(14,item(Material.NETHERITE_INGOT,"&8Acheter 1 netherite","&7Prix: &f"+getConfig().getDouble("shop.netherite-buy",100)));inv.setItem(16,item(Material.NETHERITE_BLOCK,"&8Vendre 1 netherite","&7Prix: &f"+getConfig().getDouble("shop.netherite-sell",50)));p.openInventory(inv);}
    private String fmt(double x){return String.format(Locale.US,"%.2f",x);}

    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] a){
        String n=c.getName().toLowerCase();
        if(n.equals("shop")){if(s instanceof Player p)openShop(p);else tell(s,"errors.player");return true;}
        if(n.equals("bank")){if(s instanceof Player p)openBank(p);else tell(s,"errors.player");return true;}
        if(n.equals("rich")){if(!(s instanceof Player p)){tell(s,"errors.player");return true;}if(a.length==0)openRich(p,"diamond");else if(a[0].equalsIgnoreCase("diamond")||a[0].equalsIgnoreCase("netherite"))openRich(p,a[0]);else tell(p,"errors.usage");return true;}
        if(n.equals("trade")){if(!(s instanceof Player p)){tell(s,"errors.player");return true;}return tradeCommand(p,a);}
        if(n.equals("economy"))return economyCommand(s,a);
        return true;
    }
    private boolean tradeCommand(Player p,String[] a){
        if(a.length==0){tell(p,"errors.usage");return true;} if(a[0].equalsIgnoreCase("accept")){TradeRequest r=requests.remove(p.getUniqueId());if(r==null||r.expires<System.currentTimeMillis()){tell(p,"trade.none");return true;}Player from=Bukkit.getPlayer(r.from);if(from==null){tell(p,"trade.offline");return true;}outgoing.remove(from.getUniqueId());openTrade(from,p);return true;}if(a[0].equalsIgnoreCase("deny")){TradeRequest r=requests.remove(p.getUniqueId());if(r!=null){Player f=Bukkit.getPlayer(r.from);if(f!=null)tell(f,"trade.denied",p.getName());}return true;}if(a[0].equalsIgnoreCase("cancel")){UUID t=outgoing.remove(p.getUniqueId());if(t!=null){requests.remove(t);tell(p,"trade.cancelled");}else tell(p,"trade.none");return true;}Player target=Bukkit.getPlayerExact(a[0]);if(target==null||target.equals(p)){tell(p,"trade.invalid");return true;}if(requests.containsKey(target.getUniqueId())){tell(p,"trade.busy");return true;}TradeRequest r=new TradeRequest(p.getUniqueId(),System.currentTimeMillis()+tradeExpiry*1000L);requests.put(target.getUniqueId(),r);outgoing.put(p.getUniqueId(),target.getUniqueId());tell(p,"trade.sent",target.getName());tell(target,"trade.received",p.getName(),tradeExpiry);return true;
    }
    private void openTrade(Player a,Player b){Inventory inv=Bukkit.createInventory(null,54,ChatColor.DARK_AQUA+"Trade: "+a.getName()+" ↔ "+b.getName());inv.setItem(22,item(Material.LIME_DYE,"&aCONFIRMER","&7Les deux joueurs doivent confirmer"));inv.setItem(31,item(Material.RED_DYE,"&cANNULER"));a.openInventory(inv);b.openInventory(inv);}
    private boolean economyCommand(CommandSender s,String[] a){if(a.length==0||a[0].equalsIgnoreCase("admin")){if(a.length==1&&a[0].equalsIgnoreCase("admin")&&s instanceof Player p){openAdmin(p);return true;}tell(s,"errors.usage");return true;}if(a[0].equalsIgnoreCase("reload")){if(!s.hasPermission("realsolareconomy.admin.reload")){tell(s,"errors.permission");return true;}reloadConfig();tradeExpiry=getConfig().getInt("trade-expiry-seconds",60);tell(s,"admin.reloaded");return true;}if(a.length<2){tell(s,"errors.usage");return true;}Player target=Bukkit.getPlayerExact(a[1]);if(target==null){tell(s,"errors.offline");return true;}try{ensure(target);if(a[0].equalsIgnoreCase("balance")){if(!s.hasPermission("realsolareconomy.admin.balance")){tell(s,"errors.permission");return true;}tell(s,"admin.balance",target.getName(),fmt(balance(target.getUniqueId(),"diamond")),fmt(balance(target.getUniqueId(),"netherite")));return true;}if(a.length<4){tell(s,"errors.usage");return true;}String cur=a[2].toLowerCase();if(!cur.equals("diamond")&&!cur.equals("netherite")){tell(s,"errors.currency");return true;}double amount=Double.parseDouble(a[3]);if(amount<0){tell(s,"errors.amount");return true;}if(!s.hasPermission("realsolareconomy.admin.modify")){tell(s,"errors.permission");return true;}if(a[0].equalsIgnoreCase("give"))change(s instanceof Player p?p.getUniqueId():target.getUniqueId(),target.getUniqueId(),cur,amount,"ADMIN_GIVE",target.getName());else if(a[0].equalsIgnoreCase("take")){if(balance(target.getUniqueId(),cur)<amount){tell(s,"errors.insufficient");return true;}change(s instanceof Player p?p.getUniqueId():target.getUniqueId(),target.getUniqueId(),cur,-amount,"ADMIN_TAKE",target.getName());}else if(a[0].equalsIgnoreCase("set")){double old=balance(target.getUniqueId(),cur);change(s instanceof Player p?p.getUniqueId():target.getUniqueId(),target.getUniqueId(),cur,amount-old,"ADMIN_SET",target.getName());}else {tell(s,"errors.usage");}return true;}catch(NumberFormatException e){tell(s,"errors.amount");}catch(Exception e){getLogger().warning(e.getMessage());tell(s,"errors.database");}return true;}
    private void openAdmin(Player p){Inventory i=Bukkit.createInventory(p,27,ChatColor.DARK_RED+"RealSolarEconomy • Admin");i.setItem(11,item(Material.PLAYER_HEAD,"&eGestion comptes","&7Commandes economy balance/give/take/set"));i.setItem(13,item(Material.BOOK,"&eTransactions","&7Journal SQLite"));i.setItem(15,item(Material.REDSTONE,"&eReload","&7/economy reload"));p.openInventory(i);}
    private void openRich(Player p,String cur){Inventory i=Bukkit.createInventory(p,54,ChatColor.GOLD+"Rich • "+cur);try(PreparedStatement s=db.prepareStatement("SELECT name,"+column(cur)+" FROM accounts ORDER BY "+column(cur)+" DESC LIMIT 45")){try(ResultSet r=s.executeQuery()){int pos=0;while(r.next()){i.setItem(pos++,item(cur.equals("diamond")?Material.DIAMOND:Material.NETHERITE_INGOT,"&e#"+pos+" &f"+r.getString(1),"&7Solde: &f"+fmt(r.getDouble(2))));}}}catch(Exception e){p.sendMessage(ChatColor.RED+"Erreur classement.");}p.openInventory(i);}

    @EventHandler public void click(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;String title=e.getView().getTitle();if(title.contains("RealSolarEconomy • Bank")){e.setCancelled(true);int slot=e.getRawSlot();try{ensure(p);if(slot==11)deposit(p,"diamond");else if(slot==12)withdraw(p,"diamond");else if(slot==15)deposit(p,"netherite");else if(slot==16)withdraw(p,"netherite");else if(slot==20){paymentPrompts.put(p.getUniqueId(),"pending");p.closeInventory();p.sendMessage(ChatColor.YELLOW+"Écris dans le chat: <joueur> <montant> <diamond|netherite>");}}catch(Exception x){p.sendMessage(ChatColor.RED+x.getMessage());}}else if(title.contains("RealSolarEconomy • Shop")){e.setCancelled(true);int slot=e.getRawSlot();if(slot==10)shop(p,"diamond",true);if(slot==12)shop(p,"diamond",false);if(slot==14)shop(p,"netherite",true);if(slot==16)shop(p,"netherite",false);}}
    private void deposit(Player p,String cur)throws SQLException{Material m=cur.equals("diamond")?Material.DIAMOND:Material.NETHERITE_INGOT;if(!p.getInventory().containsAtLeast(m,1)){tell(p,"errors.noitem");return;}p.getInventory().removeItem(new ItemStack(m,1));change(p.getUniqueId(),p.getUniqueId(),cur,1,"DEPOSIT",p.getName());openBank(p);}
    private void withdraw(Player p,String cur)throws SQLException{if(balance(p.getUniqueId(),cur)<1){tell(p,"errors.insufficient");return;}Material m=cur.equals("diamond")?Material.DIAMOND:Material.NETHERITE_INGOT;change(p.getUniqueId(),p.getUniqueId(),cur,-1,"WITHDRAW",p.getName());HashMap<Integer,ItemStack> left=p.getInventory().addItem(new ItemStack(m,1));if(!left.isEmpty())p.getWorld().dropItemNaturally(p.getLocation(),left.values().iterator().next());openBank(p);}
    private void shop(Player p,String cur,boolean buy){try{double price=getConfig().getDouble("shop."+cur+(buy?"-buy":"-sell"));Material m=cur.equals("diamond")?Material.DIAMOND:Material.NETHERITE_INGOT;if(buy){if(balance(p.getUniqueId(),cur)<price){tell(p,"errors.insufficient");return;}change(p.getUniqueId(),p.getUniqueId(),cur,-price,"SHOP_BUY",p.getName());p.getInventory().addItem(new ItemStack(m,1));}else{if(!p.getInventory().containsAtLeast(m,1)){tell(p,"errors.noitem");return;}p.getInventory().removeItem(new ItemStack(m,1));change(p.getUniqueId(),p.getUniqueId(),cur,price,"SHOP_SELL",p.getName());}openShop(p);}catch(Exception e){tell(p,"errors.database");}}
    @EventHandler public void chat(AsyncPlayerChatEvent e){Player p=e.getPlayer();if(!paymentPrompts.containsKey(p.getUniqueId()))return;e.setCancelled(true);paymentPrompts.remove(p.getUniqueId());String[] a=e.getMessage().trim().split("\\s+");if(a.length!=3){tell(p,"errors.payment_usage");return;}Player t=Bukkit.getPlayerExact(a[0]);try{double amount=Double.parseDouble(a[1]);String cur=a[2].toLowerCase();if(t==null||t.equals(p)){tell(p,"errors.invalid");return;}if(amount<=0||(!cur.equals("diamond")&&!cur.equals("netherite"))){tell(p,"errors.invalid");return;}ensure(p);ensure(t);if(balance(p.getUniqueId(),cur)<amount){tell(p,"errors.insufficient");return;}change(p.getUniqueId(),p.getUniqueId(),cur,-amount,"PAYMENT",t.getName());change(p.getUniqueId(),t.getUniqueId(),cur,amount,"PAYMENT",p.getName());tell(p,"payment.sent",t.getName(),fmt(amount),cur);tell(t,"payment.received",p.getName(),fmt(amount),cur);}catch(Exception x){tell(p,"errors.database");}}

    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){String n=c.getName().toLowerCase();if(n.equals("trade")){if(a.length==1)return filter(a[0],List.of("accept","deny","cancel"));if(a.length==1&&s instanceof Player)return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();}if(n.equals("rich")&&a.length==1)return filter(a[0],List.of("diamond","netherite"));if(n.equals("economy")){if(a.length==1)return filter(a[0],List.of("admin","balance","give","take","set","reload"));if(a.length==2&&List.of("balance","give","take","set").contains(a[0].toLowerCase()))return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();if(a.length==3&&List.of("give","take","set").contains(a[0].toLowerCase()))return List.of("diamond","netherite");}return List.of();}
    private List<String> filter(String q,List<String>x){return x.stream().filter(v->v.toLowerCase().startsWith(q.toLowerCase())).toList();}
    private static final class TradeRequest{final UUID from;final long expires;TradeRequest(UUID f,long e){from=f;expires=e;}}
}
