package fr.ted30600.tntdupe;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * TNT duplication support for piston-driven flying machines such as
 * World Eaters and Trench Miners.
 *
 * The duplication is deliberately limited to TNT moved in the same piston
 * event as slime, honey, or dead coral. This avoids turning every ordinary
 * piston-pushed TNT block into a duplicator.
 */
public final class Main extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("TNTDupe enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("TNTDupe disabled.");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        List<Block> movedBlocks = event.getBlocks();
        if (movedBlocks.isEmpty()) {
            return;
        }

        // The piston event exposes the blocks before they are moved.
        // A valid flying-machine setup must move TNT together with at least
        // one slime/honey block or a dead coral block.
        if (!containsTriggerBlock(movedBlocks)) {
            return;
        }

        for (Block block : movedBlocks) {
            if (block.getType() != Material.TNT) {
                continue;
            }

            Location spawnLocation = block.getLocation()
                    .add(event.getDirection().getDirection().multiply(0.5));

            TNTPrimed primed = block.getWorld().spawn(spawnLocation, TNTPrimed.class);
            // Match vanilla TNT's normal fuse (4 seconds / 80 ticks).
            primed.setFuse(80);
        }
    }

    private boolean containsTriggerBlock(List<Block> movedBlocks) {
        for (Block block : movedBlocks) {
            Material type = block.getType();
            if (type == Material.SLIME_BLOCK
                    || type == Material.HONEY_BLOCK
                    || isDeadCoral(type)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDeadCoral(Material material) {
        return material == Material.DEAD_BRAIN_CORAL
                || material == Material.DEAD_BUBBLE_CORAL
                || material == Material.DEAD_FIRE_CORAL
                || material == Material.DEAD_HORN_CORAL
                || material == Material.DEAD_TUBE_CORAL
                || material == Material.DEAD_CORAL_BLOCK
                || material == Material.DEAD_BRAIN_CORAL_FAN
                || material == Material.DEAD_BUBBLE_CORAL_FAN
                || material == Material.DEAD_FIRE_CORAL_FAN
                || material == Material.DEAD_HORN_CORAL_FAN
                || material == Material.DEAD_TUBE_CORAL_FAN
                || material == Material.DEAD_BRAIN_CORAL_WALL_FAN
                || material == Material.DEAD_BUBBLE_CORAL_WALL_FAN
                || material == Material.DEAD_FIRE_CORAL_WALL_FAN
                || material == Material.DEAD_HORN_CORAL_WALL_FAN
                || material == Material.DEAD_TUBE_CORAL_WALL_FAN;
    }
}
