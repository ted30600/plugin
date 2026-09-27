package fr.ted30600.bananaplugin.listener;
import fr.ted30600.bananaplugin.item.BananaBladeItem;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import java.util.*;
public final class CombatListener implements Listener{
 private static final double FALL=1.5,MULT=1.8,DIST=4.0,DAMAGE=6.0;private static final long COOLDOWN=20L;
 private final Plugin plugin;private final Map<UUID,Long> cooldowns=new HashMap<>();public CombatListener(Plugin p){plugin=p;}
 @EventHandler public void onAttack(EntityDamageByEntityEvent e){if(!(e.getDamager() instanceof Player p)||!(e.getEntity() instanceof LivingEntity t)||!BananaBladeItem.isBananaBlade(plugin,p.getInventory().getItemInMainHand()))return;double f=p.getFallDistance();if(f<FALL)return;e.setDamage(e.getDamage()+f*MULT);for(Entity x:t.getWorld().getNearbyEntities(t.getLocation(),2.5,2.5,2.5))if(x instanceof LivingEntity v&&!x.equals(p)){Vector push=v.getLocation().toVector().subtract(t.getLocation().toVector());if(push.lengthSquared()>0)push.normalize();v.setVelocity(v.getVelocity().add(push.multiply(.9).setY(.45)));}t.getWorld().playSound(t.getLocation(),Sound.ITEM_MACE_SMASH_AIR,1,1);p.setFallDistance(0);}
 @EventHandler public void onRightClick(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND||!e.getAction().isRightClick())return;Player p=e.getPlayer();if(!BananaBladeItem.isBananaBlade(plugin,p.getInventory().getItemInMainHand()))return;long now=p.getWorld().getFullTime(),last=cooldowns.getOrDefault(p.getUniqueId(),Long.MIN_VALUE);if(now-last<COOLDOWN)return;cooldowns.put(p.getUniqueId(),now);Vector look=p.getLocation().getDirection().normalize();Location eye=p.getEyeLocation();for(Entity x:p.getWorld().getNearbyEntities(eye,DIST,2,DIST))if(x instanceof LivingEntity v&&!x.equals(p)){Vector to=v.getLocation().toVector().subtract(eye.toVector());double along=to.dot(look);if(along>0&&along<=DIST){double lateral=to.subtract(look.clone().multiply(along)).length();if(lateral<1.2){v.damage(DAMAGE,p);v.setVelocity(v.getVelocity().add(look.clone().multiply(.6).setY(.15)));}}}p.setVelocity(p.getVelocity().add(look.clone().multiply(1.4).setY(.1)));p.getWorld().playSound(p.getLocation(),Sound.ITEM_TRIDENT_THROW,1,.8f);p.playEffect(EntityEffect.HURT);}
}