package fr.ted30600.bananaplugin;
import fr.ted30600.bananaplugin.command.ArmeCommand;
import fr.ted30600.bananaplugin.listener.CombatListener;
import org.bukkit.plugin.java.JavaPlugin;
public final class BananaPlugin extends JavaPlugin {
 public void onEnable(){getServer().getPluginManager().registerEvents(new CombatListener(this),this);ArmeCommand c=new ArmeCommand(this);if(getCommand("arme")!=null){getCommand("arme").setExecutor(c);getCommand("arme").setTabCompleter(c);}getLogger().info("BananaPlugin 2.0.0 active - Paper 1.21.11");}
 public void onDisable(){getLogger().info("BananaPlugin desactive");}
}