
/**
    PrinterUtils

    Generic printer related utilities to power our robust printer.
**/

package dinosaurwizard.dinoprinter.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.LinkedHashSet;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class PrinterUtils {

    // AIR SHAPE is the shape that air-place utilizes. It is slightly smaller than a normal minecraft block.
    private static final VoxelShape AIR_SHAPE = Shapes.box(0.01, 0.01, 0.01, 1.0 - 0.01, 1.0 - 0.01, 1.0 - 0.01);

    private static final double[] samples = {0.1666666667, 0.5, 0.8333333333};

    private PrinterUtils() {
    }

    // Determines if a player is close enough to a point on a block's face
    public static boolean isPointInRange(Vec3 point, double range) {
        return mc.player.getEyePosition().distanceToSqr(point) < range * range;
    }

    // Determines if a player can see a point on a block's face
    public static boolean isPointVisible(Vec3 point, BlockPos blockPos, Direction direction) {
        ClipContext context = new ClipContext(mc.player.getEyePosition(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult ray = mc.level.clip(context);

        return ray.getType() == HitResult.Type.MISS || (ray.getBlockPos().equals(blockPos) && ray.getDirection() == direction);
    }

    /**
         +---------------------+
        /                     /|
       /                     / |   Heres a diagram of the points getShapeFacePoints may give for a direction. (points marked as X)
      /                     /  |
     /                     /   |   For dino printer, these points are tested to determine if the player can place/interact against said point.
    +---------------------+    |   Tests may include raycasting, range checks, and testing simulated blockstates at each point.
    |                     |    |
    | X        X        X |    |   For generic placement, points are checked on every adjacent block for a position.
    |                     |    |   For air-place, points are checked on its own position.
    |                     |    |
    | X        X        X |    +
    |                     |   /
    |                     |  /
    | X        X        X | /
    |                     |/
    +---------------------+

    **/

    // Gets a set of points across a block shape's face as shown in the diagram
    // This is dynamic, so blocks like stairs or hoppers have more faces/points
    public static Set<Vec3> getShapeFacePoints(BlockPos blockPos, Direction face) {
        Set<Vec3> points = new LinkedHashSet<>();

        BlockState state = mc.level.getBlockState(blockPos);
        VoxelShape shape = state.canBeReplaced() ? AIR_SHAPE : state.getShape(mc.level, blockPos);

        for (AABB box : shape.toAabbs()) {
            double minX = box.minX + blockPos.getX(), minY = box.minY + blockPos.getY(), minZ = box.minZ + blockPos.getZ();
            double maxX = box.maxX + blockPos.getX(), maxY = box.maxY + blockPos.getY(), maxZ = box.maxZ + blockPos.getZ();

            // Samples are 'multiplied' against eachother, for a total of 9 points per block face (as shown on the diagram)
            for (double u : samples) {
                for (double v : samples) {
                    switch (face) {
                        case DOWN ->  points.add(new Vec3(Mth.lerp(u, minX, maxX), minY, Mth.lerp(v, minZ, maxZ)));
                        case UP ->    points.add(new Vec3(Mth.lerp(u, minX, maxX), maxY, Mth.lerp(v, minZ, maxZ)));
                        case NORTH -> points.add(new Vec3(Mth.lerp(u, minX, maxX), Mth.lerp(v, minY, maxY), minZ));
                        case SOUTH -> points.add(new Vec3(Mth.lerp(u, minX, maxX), Mth.lerp(v, minY, maxY), maxZ));
                        case WEST ->  points.add(new Vec3(minX, Mth.lerp(u, minY, maxY), Mth.lerp(v, minZ, maxZ)));
                        case EAST ->  points.add(new Vec3(maxX, Mth.lerp(u, minY, maxY), Mth.lerp(v, minZ, maxZ)));
                    }
                }
            }
        }

        return points;
    }
}
