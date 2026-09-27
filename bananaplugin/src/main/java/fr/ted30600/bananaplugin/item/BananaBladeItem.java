package fr.ted30600.bananaplugin.item;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.util.List;
public final class BananaBladeItem{
 public static final String ITEM_ID="banana_blade";
 private BananaBladeItem(){}
 public static NamespacedKey key(Plugin p){return new NamespacedKey(p,"banana_weapon");}
 public static ItemStack create(Plugin p){ItemStack i=new ItemStack(Material.MACE);ItemMeta m=i.getItemMeta();m.displayName(Component.text("Lame Banane",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false));m.lore(List.of(Component.text("Masse + Epee + Lance",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),Component.text("Clic droit : ruée façon lance",NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC,false)));m.getPersistentDataContainer().set(key(p),PersistentDataType.STRING,ITEM_ID);m.setUnbreakable(true);i.setItemMeta(m);return i;}
 public static boolean isBananaBlade(Plugin p,ItemStack s){if(s==null||s.getType().isAir()||!s.hasItemMeta())return false;return ITEM_ID.equals(s.getItemMeta().getPersistentDataContainer().get(key(p),PersistentDataType.STRING));}
}