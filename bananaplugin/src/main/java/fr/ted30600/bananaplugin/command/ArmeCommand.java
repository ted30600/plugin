package fr.ted30600.bananaplugin.command;
import fr.ted30600.bananaplugin.BananaPlugin;
import fr.ted30600.bananaplugin.item.BananaBladeItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.List;
public final class ArmeCommand implements CommandExecutor,TabCompleter{
 private final BananaPlugin plugin; public ArmeCommand(BananaPlugin p){plugin=p;}
 public boolean onCommand(CommandSender s,Command c,String l,String[] a){
  if(!s.hasPermission("bananaplugin.give")){s.sendMessage(Component.text("Tu n'as pas la permission.",NamedTextColor.RED));return true;}
  if(a.length<2||!a[0].equalsIgnoreCase("give")||!a[1].equalsIgnoreCase("banana")){s.sendMessage(Component.text("Usage : /arme give banana [joueur]",NamedTextColor.RED));return true;}
  Player t;if(a.length>=3){t=Bukkit.getPlayerExact(a[2]);if(t==null){s.sendMessage(Component.text("Joueur introuvable : "+a[2],NamedTextColor.RED));return true;}}else if(s instanceof Player p)t=p;else{s.sendMessage(Component.text("Depuis la console : /arme give banana <joueur>",NamedTextColor.RED));return true;}
  t.getInventory().addItem(BananaBladeItem.create(plugin));t.sendMessage(Component.text("Tu as reçu la Lame Banane !",NamedTextColor.GOLD));if(!s.equals(t))s.sendMessage(Component.text("Lame Banane donnée à "+t.getName(),NamedTextColor.GREEN));return true;
 }
 public List<String> onTabComplete(CommandSender s,Command c,String a,String[] x){if(x.length==1)return List.of("give");if(x.length==2)return List.of("banana");if(x.length==3)return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();return List.of();}
}