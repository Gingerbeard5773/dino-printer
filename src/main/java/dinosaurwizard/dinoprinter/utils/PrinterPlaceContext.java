
/** PrinterPlaceContext 

  This file is technically unnecessary. But it has been added for one major reason: Client Compatibility!
  Some clients (lambda...) like to mixin and modify existing base methods which screw up our calculations.
  So we simply do all the code ourselves to bypass whatever code they injected.

  This file mainly just consists of copied base MC functionality.
**/

package dinosaurwizard.dinoprinter.utils;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;

public class PrinterPlaceContext extends BlockPlaceContext {

    private final float yaw;
    private final float pitch;
    private final boolean sneaking;

    public PrinterPlaceContext(Player player, float yaw, float pitch, boolean sneaking, InteractionHand hand, ItemStack stack, BlockHitResult hitResult) {
        super(player.level(), player, hand, stack, hitResult);
        this.yaw = yaw;
        this.pitch = pitch;
        this.sneaking = sneaking;
    }

    @Override
    public Direction getNearestLookingDirection() {
        return getEntityFacingOrder()[0];
    }

    @Override
    public Direction getNearestLookingVerticalDirection() {
        return pitch < 0.0F ? Direction.UP : Direction.DOWN;
    }

    @Override
    public Direction[] getNearestLookingDirections() {
        Direction[] directions = getEntityFacingOrder();
        if (this.replaceClicked) {
            return directions;
        } else {
            Direction direction = this.getClickedFace();
            int i = 0;

            while (i < directions.length && directions[i] != direction.getOpposite()) {
                i++;
            }

            if (i > 0) {
                System.arraycopy(directions, 0, directions, 1, i);
                directions[0] = direction.getOpposite();
            }

            return directions;
        }
    }

    @Override
    public Direction getHorizontalDirection() {
        return Direction.fromYRot(yaw);
    }

    @Override
    public boolean isSecondaryUseActive() {
        return sneaking;
    }

    @Override
    public float getRotation() {
        return yaw;
    }

    public Direction[] getEntityFacingOrder() {
        float f = pitch * (float) (Math.PI / 180.0);
        float g = -yaw * (float) (Math.PI / 180.0);
        float h = Mth.sin(f);
        float i = Mth.cos(f);
        float j = Mth.sin(g);
        float k = Mth.cos(g);
        boolean bl = j > 0.0F;
        boolean bl2 = h < 0.0F;
        boolean bl3 = k > 0.0F;
        float l = bl ? j : -j;
        float m = bl2 ? -h : h;
        float n = bl3 ? k : -k;
        float o = l * i;
        float p = n * i;
        Direction direction = bl ? Direction.EAST : Direction.WEST;
        Direction direction2 = bl2 ? Direction.UP : Direction.DOWN;
        Direction direction3 = bl3 ? Direction.SOUTH : Direction.NORTH;
        if (l > n) {
            if (m > o) {
                return listClosest(direction2, direction, direction3);
            } else {
                return p > m ? listClosest(direction, direction3, direction2) : listClosest(direction, direction2, direction3);
            }
        } else if (m > p) {
            return listClosest(direction2, direction3, direction);
        } else {
            return o > m ? listClosest(direction3, direction, direction2) : listClosest(direction3, direction2, direction);
        }
    }

    /**
     * Helper function that returns the 3 directions given, followed by the 3 opposite given in opposite order.
     */
    private static Direction[] listClosest(Direction first, Direction second, Direction third) {
        return new Direction[]{first, second, third, third.getOpposite(), second.getOpposite(), first.getOpposite()};
    }
}
