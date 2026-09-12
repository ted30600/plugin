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
 * Duplication only occurs when TNT is moved in the same piston event as
 * slime, honey, or dead coral. The TNT block itself is not removed.
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
        if (movedBlocks.isEmpty() || !containsTriggerBlock(movedBlocks)) {
            return;
        }

        for (Block block : movedBlocks) {
            if (block.getType() != Material.TNT) {
                continue;
            }

            // The event fires before the piston moves the blocks, so the
            // destination is the current TNT block shifted by piston direction.
            // Spawn at the center of that destination block.
            Block destination = block.getRelative(event.getDirection());
            Location spawnLocation = destination.getLocation().add(0.5, 0.5, 0.5);

            TNTPrimed primed = block.getWorld().spawn(spawnLocation, TNTPrimed.class);
            // Vanilla TNT's standard fuse is 80 ticks (4 seconds).
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
        return switch (material) {
            case DEAD_BRAIN_CORAL,
                 DEAD_BUBBLE_CORAL,
                 DEAD_FIRE_CORAL,
                 DEAD_HORN_CORAL,
                 DEAD_TUBE_CORAL,
                 DEAD_CORAL_BLOCK,
                 DEAD_BRAIN_CORAL_FAN,
                 DEAD_BUBBLE_CORAL_FAN,
                 DEAD_FIRE_CORAL_FAN,
                 DEAD_HORN_CORAL_FAN,
                 DEAD_TUBE_CORAL_FAN,
                 DEAD_BRAIN_CORAL_WALL_FAN,
                 DEAD_BUBBLE_CORAL_WALL_FAN,
                 DEAD_FIRE_CORAL_WALL_FAN,
                 DEAD_HORN_CORAL_WALL_FAN,
                 DEAD_TUBE_CORAL_WALL_FAN -> true;
            default -> false;
        };
    }
}
