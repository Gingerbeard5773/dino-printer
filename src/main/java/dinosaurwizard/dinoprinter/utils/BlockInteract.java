
/**
    BlockInteract

    This class deals with matching blockstates by interacting with blocks
**/

package dinosaurwizard.dinoprinter.utils;

import dinosaurwizard.dinoprinter.modules.DinoPrinter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
        if (block.getStateDefinition().getProperties().isEmpty()) return false;

        // Blacklisted blocks
        if (block == Blocks.IRON_DOOR || block == Blocks.IRON_TRAPDOOR) return false;

        // Note - Noteblocks
        if (required.hasProperty(BlockStateProperties.NOTE) && existing.hasProperty(BlockStateProperties.NOTE)) {
            return required.getValue(BlockStateProperties.NOTE) != existing.getValue(BlockStateProperties.NOTE);
        // Open - Doors, trapdoors, fencegates
        } else if (required.hasProperty(BlockStateProperties.OPEN) && existing.hasProperty(BlockStateProperties.OPEN)) {
            return required.getValue(BlockStateProperties.OPEN) != existing.getValue(BlockStateProperties.OPEN);
        // Inverted - Daylight detector
        } else if (required.hasProperty(BlockStateProperties.INVERTED) && existing.hasProperty(BlockStateProperties.INVERTED)) {
            return required.getValue(BlockStateProperties.INVERTED) != existing.getValue(BlockStateProperties.INVERTED);
        // Delay - Repeater
        } else if (required.hasProperty(BlockStateProperties.DELAY) && existing.hasProperty(BlockStateProperties.DELAY)) {
            return required.getValue(BlockStateProperties.DELAY) != existing.getValue(BlockStateProperties.DELAY);
        // Comparator Mode - Comparator
        } else if (required.hasProperty(BlockStateProperties.MODE_COMPARATOR) && existing.hasProperty(BlockStateProperties.MODE_COMPARATOR)) {
            return required.getValue(BlockStateProperties.MODE_COMPARATOR) != existing.getValue(BlockStateProperties.MODE_COMPARATOR);
        // Powered - levers
        } else if (block instanceof LeverBlock && required.hasProperty(BlockStateProperties.POWERED) && existing.hasProperty(BlockStateProperties.POWERED)) {
            return required.getValue(BlockStateProperties.POWERED) != existing.getValue(BlockStateProperties.POWERED);
        // Wire Connection - Redstone
        } else if (block instanceof RedStoneWireBlock) {
            return (RedStoneWireBlock.isCross(existing) && RedStoneWireBlock.isDot(required)) || 
                   (RedStoneWireBlock.isCross(required) && RedStoneWireBlock.isDot(existing));
        }

        return false;
    }

    public boolean canInteract() {
        return hit != null;
    }

    // Yaw when interacting
    public double getYaw() {
        return RotationManager.getYaw(hit.getLocation());
    }

    // Pitch when interacting
    public double getPitch() {
        return RotationManager.getPitch(hit.getLocation());
    }

    // Gives a valid hit
    private BlockHitResult calculateBestInteractHit() {
        for (Direction direction : Direction.values()) {
            Set<Vec3> points = PrinterUtils.getShapeFacePoints(blockPos, direction);
            for (Vec3 point : points) {
                if (!isPointValid(point, blockPos, direction)) continue;

                BlockHitResult interactHit = new BlockHitResult(point, direction, blockPos, false);
                return interactHit;
            }
        }

        // We failed to find any good spot to interact with the block
        return null;
    }

    public boolean isPointValid() {
        return isPointValid(hit.getLocation(), hit.getBlockPos(), hit.getDirection());
    }

    private boolean isPointValid(Vec3 point, BlockPos pos, Direction direction) {
        if (!PrinterUtils.isPointInRange(point, printer.placeRange.get())) return false;

        if (!printer.wallPlace.get() && !PrinterUtils.isPointVisible(point, blockPos, direction)) return false;

        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BlockInteract other)) return false;
        return blockPos.equals(other.blockPos);
    }
}
