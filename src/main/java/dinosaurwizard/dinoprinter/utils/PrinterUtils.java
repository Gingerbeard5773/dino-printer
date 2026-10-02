
/**
    PrinterUtils

    Generic printer related utilities to power our robust printer.
**/

package dinosaurwizard.dinoprinter.utils;

import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;

import java.util.LinkedHashSet;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class PrinterUtils {

    // AIR SHAPE is the shape that air-place utilizes. It is slightly smaller than a normal minecraft block.
    private static final VoxelShape AIR_SHAPE = VoxelShapes.cuboid(0.01, 0.01, 0.01, 1.0 - 0.01, 1.0 - 0.01, 1.0 - 0.01);

    private static final double[] samples = {0.1666666667, 0.5, 0.8333333333};

    private PrinterUtils() {
    }

    // Determines if a player is close enough to a point on a block's face
    public static boolean isPointInRange(Vec3d point, double range) {
        return mc.player.getEyePos().squaredDistanceTo(point) < range * range;
    }

    // Determines if a player can see a point on a block's face
    public static boolean isPointVisible(Vec3d point, BlockPos blockPos, Direction direction) {
        RaycastContext context = new RaycastContext(mc.player.getEyePos(), point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        BlockHitResult ray = mc.world.raycast(context);

        return ray.getType() == HitResult.Type.MISS || (ray.getBlockPos().equals(blockPos) && ray.getSide() == direction);
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
    public static Set<Vec3d> getShapeFacePoints(BlockPos blockPos, Direction face) {
        Set<Vec3d> points = new LinkedHashSet<>();

        BlockState state = mc.world.getBlockState(blockPos);
        VoxelShape shape = state.isReplaceable() ? AIR_SHAPE : state.getOutlineShape(mc.world, blockPos);

        for (Box box : shape.getBoundingBoxes()) {
            double minX = box.minX + blockPos.getX(), minY = box.minY + blockPos.getY(), minZ = box.minZ + blockPos.getZ();
            double maxX = box.maxX + blockPos.getX(), maxY = box.maxY + blockPos.getY(), maxZ = box.maxZ + blockPos.getZ();

            // Samples are 'multiplied' against eachother, for a total of 9 points per block face (as shown on the diagram)
            for (double u : samples) {
                for (double v : samples) {
                    switch (face) {
                        case DOWN ->  points.add(new Vec3d(MathHelper.lerp(u, minX, maxX), minY, MathHelper.lerp(v, minZ, maxZ)));
                        case UP ->    points.add(new Vec3d(MathHelper.lerp(u, minX, maxX), maxY, MathHelper.lerp(v, minZ, maxZ)));
                        case NORTH -> points.add(new Vec3d(MathHelper.lerp(u, minX, maxX), MathHelper.lerp(v, minY, maxY), minZ));
                        case SOUTH -> points.add(new Vec3d(MathHelper.lerp(u, minX, maxX), MathHelper.lerp(v, minY, maxY), maxZ));
                        case WEST ->  points.add(new Vec3d(minX, MathHelper.lerp(u, minY, maxY), MathHelper.lerp(v, minZ, maxZ)));
                        case EAST ->  points.add(new Vec3d(maxX, MathHelper.lerp(u, minY, maxY), MathHelper.lerp(v, minZ, maxZ)));
                    }
                }
            }
        }

        return points;
    }
}
