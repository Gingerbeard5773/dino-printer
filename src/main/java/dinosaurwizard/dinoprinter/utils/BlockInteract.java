
/**
    BlockInteract

    This class deals with matching blockstates by interacting with blocks
**/

package dinosaurwizard.dinoprinter.utils;

import dinosaurwizard.dinoprinter.modules.DinoPrinter;
import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BlockInteract {
    private final DinoPrinter printer;
    public final BlockPos blockPos;
    public final BlockHitResult hit;
    public int timer;

    public BlockInteract(BlockPos blockPos, DinoPrinter printer) {
        this.printer = printer;
        this.blockPos = blockPos;
        this.hit = calculateBestInteractHit();
        this.timer = 0;
    }

    // Determine if there is an available interaction
    public static boolean isValid(BlockState required, BlockState existing) {
        if (required.isAir()) return false;

        Block block = required.getBlock();
        if (block != existing.getBlock()) return false;

        // Ignore blocks with no properties
        if (block.getStateManager().getProperties().isEmpty()) return false;

        // Blacklisted blocks
        if (block == Blocks.IRON_DOOR || block == Blocks.IRON_TRAPDOOR) return false;

        // Note - Noteblocks
        if (required.contains(Properties.NOTE) && existing.contains(Properties.NOTE)) {
            return required.get(Properties.NOTE) != existing.get(Properties.NOTE);
        // Open - Doors, trapdoors, fencegates
        } else if (required.contains(Properties.OPEN) && existing.contains(Properties.OPEN)) {
            return required.get(Properties.OPEN) != existing.get(Properties.OPEN);
        // Inverted - Daylight detector
        } else if (required.contains(Properties.INVERTED) && existing.contains(Properties.INVERTED)) {
            return required.get(Properties.INVERTED) != existing.get(Properties.INVERTED);
        // Delay - Repeater
        } else if (required.contains(Properties.DELAY) && existing.contains(Properties.DELAY)) {
            return required.get(Properties.DELAY) != existing.get(Properties.DELAY);
        // Comparator Mode - Comparator
        } else if (required.contains(Properties.COMPARATOR_MODE) && existing.contains(Properties.COMPARATOR_MODE)) {
            return required.get(Properties.COMPARATOR_MODE) != existing.get(Properties.COMPARATOR_MODE);
        // Powered - levers
        } else if (block instanceof LeverBlock && required.contains(Properties.POWERED) && existing.contains(Properties.POWERED)) {
            return required.get(Properties.POWERED) != existing.get(Properties.POWERED);
        // Wire Connection - Redstone
        } else if (block instanceof RedstoneWireBlock) {
            return (RedstoneWireBlock.isFullyConnected(existing) && RedstoneWireBlock.isNotConnected(required)) || 
                   (RedstoneWireBlock.isFullyConnected(required) && RedstoneWireBlock.isNotConnected(existing));
        }

        return false;
    }

    public boolean canInteract() {
        return hit != null;
    }

    // Gives a valid hit
    private BlockHitResult calculateBestInteractHit() {
        for (Direction direction : Direction.values()) {
            Set<Vec3d> points = PrinterUtils.getShapeFacePoints(blockPos, direction);
            for (Vec3d point : points) {
                if (!PrinterUtils.isPointInRange(point, printer.placeRange.get())) continue;

                if (!printer.wallPlace.get() && !PrinterUtils.isPointVisible(point, blockPos, direction)) continue;

                BlockHitResult interactHit = new BlockHitResult(point, direction, blockPos, false);
                return interactHit;
            }
        }

        // We failed to find any good spot to interact with the block
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BlockInteract other)) return false;
        return blockPos.equals(other.blockPos);
    }
}
