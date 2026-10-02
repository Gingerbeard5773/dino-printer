
/**
    BlockPrint

    This class deals with matching blockstates when placing blocks.

    This is where most of the advanced functionality of dino printer is located.
**/

package dinosaurwizard.dinoprinter.utils;

import dinosaurwizard.dinoprinter.modules.DinoPrinter;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.block.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BlockPrint {
    private final DinoPrinter printer;
    public final BlockPos blockPos;
    public final BlockState required;
    public final BlockState existing;
    public BlockState simulated;
    public final BlockHitResult hit;
    public final boolean fluid;
    private final boolean rotatable;
    private float placeYaw;
    private float placePitch;
    private boolean useRotation = false;

    public BlockPrint(BlockPos blockPos, BlockState required, BlockState existing, DinoPrinter printer) {
        this.printer = printer;
        this.blockPos = blockPos;
        this.required = required;
        this.existing = existing;

        this.fluid = required.getBlock() instanceof FluidBlock;
        this.rotatable = isRotatable();
        this.hit = calculateBestPlaceHit();
    }

    // Determine if a location can be printed at
    public static boolean isValid(BlockPos blockPos, BlockState required, BlockState existing, DinoPrinter printer) {
        if (required.isAir()) return false;

        boolean fluid = required.getBlock() instanceof FluidBlock;
        if (fluid && (!required.getFluidState().isStill() || !printer.fluids.get())) return false;

        if (!printer.incrementalStates.get() || !BlockPrint.isIncremental(required, existing)) {
            // Spot must be air or some other replaceable block
            if (!existing.isReplaceable()) return false;

            // Spot is not already the required blockstate
            if (required.getBlock() == existing.getBlock() && (!fluid || existing.getFluidState().isStill())) return false;

            // Don't place in a position we already attempted
            if (printer.cachedPositions.contains(blockPos)) return false;
        }

        // Check if legally placeable. For example, if its a torch, it can only be placed onto another block.
        if (!required.canPlaceAt(mc.world, blockPos)) return false;

        // No intersecting entities at our position
        if (!mc.world.canPlace(required, blockPos, ShapeContext.absent())) return false;

        return true;
    }

    public boolean canPlace() {
        return hit != null;
    }

    // Should we do special rotation when placing this block?
    public boolean shouldRotatePlace() {
        return (printer.rotationPlace.get() && rotatable) || fluid;
    }

    // Determine if our required blockstate has directionality.
    private boolean isRotatable() {
        return required.contains(Properties.FACING) || 
               required.contains(Properties.HORIZONTAL_FACING) ||
               required.contains(Properties.HORIZONTAL_AXIS) ||
               required.contains(Properties.AXIS) ||
               required.contains(Properties.HOPPER_FACING) ||
               required.contains(Properties.ORIENTATION) ||
               required.contains(Properties.VERTICAL_DIRECTION) || 
               required.contains(Properties.ROTATION);
    }

    // Yaw when placing the block
    public double getYaw() {
        if (useRotation) return placeYaw;

        return Rotations.getYaw(hit.getPos());
    }

    // Pitch when placing the block
    public double getPitch() {
        if (useRotation) return placePitch;

        return Rotations.getPitch(hit.getPos());
    }

    // Gets a simulated place state using customizable inputs.
    // The simulated place state is effectively the blockState when it is actually placed, allowing us to determine what it will look like before it is placed.
    // This can be used to compare certain attributes such as the facing direction.
    private BlockState getSimulatedPlaceState(float yaw, float pitch, BlockHitResult blockHit) {
        ItemStack stack = asItem().getDefaultStack();
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;

        boolean sneaking = printer.sneakPlace.get() || mc.player.isSneaking();
        PrinterPlaceContext context = new PrinterPlaceContext(mc.player, yaw, pitch, sneaking, Hand.MAIN_HAND, stack, blockHit);
        if (!existing.canReplace(context)) return null;

        return blockItem.getPlacementState(context);
    }

    // Gives the best possible hit result using relevant requirements
    private BlockHitResult calculateBestPlaceHit() {
        boolean incremental = printer.incrementalStates.get() && required.getBlock() == existing.getBlock();

        if (!incremental || fluid) {
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = blockPos.offset(direction);
                Direction opposite = direction.getOpposite();

                // Check spots on other blocks to place onto
                if (!mc.world.getBlockState(adjacent).isReplaceable()) {
                    Set<Vec3d> points = PrinterUtils.getShapeFacePoints(adjacent, opposite);
                    for (Vec3d point : points) {
                        if (!isPointValid(point, adjacent, opposite)) continue;

                        BlockHitResult placeHit = new BlockHitResult(point, opposite, adjacent, false);
                        if (!isMatchingRequirements(placeHit)) continue;

                        return placeHit;
                    }
                }
            }
        }

        // If we couldn't find an adjacent block to place onto, check points on our own block position
        // Only for air placement or if we have an incremental block
        if ((printer.airPlace.get() || incremental) && !fluid) {
            for (Direction direction : Direction.values()) {
                Set<Vec3d> points = PrinterUtils.getShapeFacePoints(blockPos, direction);
                for (Vec3d point : points) {
                    if (!isPointValid(point, blockPos, direction)) continue;

                    BlockHitResult placeHit = new BlockHitResult(point, direction, blockPos, false);
                    if (!isMatchingRequirements(placeHit)) continue;

                    return placeHit;
                }
            }
        }

        // We failed to find any good spot to place the block
        return null;
    }

    private boolean isMatchingRequirements(BlockHitResult placeHit) {
        if (required.getBlock().getStateManager().getProperties().isEmpty() || fluid) return true;

        if (!isMatchingProperties(placeHit)) return false;
        if (!isMatchingRotation(placeHit)) return false;
        return true;
    }

    // Check if the BlockHitResult has the same properties
    private boolean isMatchingProperties(BlockHitResult blockHit) {
        Block block = required.getBlock();

        // Ignore property matching for blocks:
        if (block instanceof TripwireBlock || block instanceof TripwireHookBlock) return true;

        float yaw = (float) Rotations.getYaw(blockHit.getPos());
        float pitch = (float) Rotations.getPitch(blockHit.getPos());
        simulated = getSimulatedPlaceState(yaw, pitch, blockHit);
        if (simulated == null) return false;

        // Unequal blocks get booted. e.g standing signs vs wall signs
        if (block != simulated.getBlock()) return false;

        if (printer.halfBlocks.get()) {
            // Slab Type - half slabs & also handling of double slabs
            if (required.contains(Properties.SLAB_TYPE) && simulated.contains(Properties.SLAB_TYPE)) {
                SlabType requiredType = required.get(Properties.SLAB_TYPE);
                SlabType simulatedType = simulated.get(Properties.SLAB_TYPE);
                if (requiredType == SlabType.DOUBLE) {
                    if (existing.contains(Properties.SLAB_TYPE)) {
                        if (simulatedType != SlabType.DOUBLE) return false;
                    }
                } else {
                    if (requiredType != simulatedType) return false;
                }
            // Block Half - stairs, trapdoors
            } else if (required.contains(Properties.BLOCK_HALF) && simulated.contains(Properties.BLOCK_HALF)) {
                if (required.get(Properties.BLOCK_HALF) != simulated.get(Properties.BLOCK_HALF)) return false;
            }
        }

        if (printer.miscStates.get()) {
            // Door Hinge - doors
            if (required.contains(Properties.DOOR_HINGE) && simulated.contains(Properties.DOOR_HINGE)) {
                if (required.get(Properties.DOOR_HINGE) != simulated.get(Properties.DOOR_HINGE)) return false;
            // Block Face - wall mounted blocks like torches or levers
            } else if (required.contains(Properties.BLOCK_FACE) && simulated.contains(Properties.BLOCK_FACE)) {
                if (required.get(Properties.BLOCK_FACE) != simulated.get(Properties.BLOCK_FACE)) return false;
            // Attachment - bells
            } else if (required.contains(Properties.ATTACHMENT) && simulated.contains(Properties.ATTACHMENT)) {
                if (required.get(Properties.ATTACHMENT) != simulated.get(Properties.ATTACHMENT)) return false;
            // Attached - hanging signs
            } else if (required.contains(Properties.ATTACHED) && simulated.contains(Properties.ATTACHED)) {
                if (required.get(Properties.ATTACHED) != simulated.get(Properties.ATTACHED)) return false;
            // Hanging - lanterns
            } else if (required.contains(Properties.HANGING) && simulated.contains(Properties.HANGING)) {
                if (required.get(Properties.HANGING) != simulated.get(Properties.HANGING)) return false;
            // Bed Part - beds
            } else if (required.contains(Properties.BED_PART) && simulated.contains(Properties.BED_PART)) {
                if (required.get(Properties.BED_PART) != simulated.get(Properties.BED_PART)) return false;
            // Chest Type - chests 
            } else if (required.contains(Properties.CHEST_TYPE) && simulated.contains(Properties.CHEST_TYPE)) {
                ChestType requiredType = required.get(Properties.CHEST_TYPE);
                ChestType simulatedType = simulated.get(Properties.CHEST_TYPE);
                if (requiredType != ChestType.SINGLE) {
                    BlockPos neighborPos = blockPos.offset(ChestBlock.getFacing(required));
                    BlockState neighborState = mc.world.getBlockState(neighborPos);
                    if (neighborState.contains(Properties.CHEST_TYPE) && requiredType != simulatedType) return false;
                } else {
                    if (requiredType != simulatedType) return false;
                }
            }
        }

        if (printer.incrementalStates.get()) {
            // Incremental states - snow layers, turtle eggs, candles, pickles, wildflowers, leaflitter
            if (simulated.contains(Properties.LAYERS) && existing.contains(Properties.LAYERS)) {
                if (simulated.get(Properties.LAYERS) <= existing.get(Properties.LAYERS)) return false;
            } else if (simulated.contains(Properties.EGGS) && existing.contains(Properties.EGGS)) {
                if (simulated.get(Properties.EGGS) <= existing.get(Properties.EGGS)) return false;
            } else if (simulated.contains(Properties.CANDLES) && existing.contains(Properties.CANDLES)) {
                if (simulated.get(Properties.CANDLES) <= existing.get(Properties.CANDLES)) return false;
            } else if (simulated.contains(Properties.PICKLES) && existing.contains(Properties.PICKLES)) {
                if (simulated.get(Properties.PICKLES) <= existing.get(Properties.PICKLES)) return false;
            } else if (simulated.contains(Properties.FLOWER_AMOUNT) && existing.contains(Properties.FLOWER_AMOUNT)) {
                if (simulated.get(Properties.FLOWER_AMOUNT) <= existing.get(Properties.FLOWER_AMOUNT)) return false;
            } else if (simulated.contains(Properties.SEGMENT_AMOUNT) && existing.contains(Properties.SEGMENT_AMOUNT)) {
                if (simulated.get(Properties.SEGMENT_AMOUNT) <= existing.get(Properties.SEGMENT_AMOUNT)) return false;
            }

            // Multi face blocks - vines, glow lichen, sculk veins
            if (block instanceof MultifaceGrowthBlock || block instanceof VineBlock) {
                if (isMatchingPropertyMultiFace(simulated, Properties.UP)) return true;
                if (isMatchingPropertyMultiFace(simulated, Properties.DOWN)) return true;
                if (isMatchingPropertyMultiFace(simulated, Properties.EAST)) return true;
                if (isMatchingPropertyMultiFace(simulated, Properties.NORTH)) return true;
                if (isMatchingPropertyMultiFace(simulated, Properties.SOUTH)) return true;
                if (isMatchingPropertyMultiFace(simulated, Properties.WEST)) return true;
                return false;
            }
        }
        return true;
    }

    private boolean isMatchingPropertyMultiFace(BlockState simulated, Property<Boolean> property) {
        return simulated.contains(property) && required.contains(property) &&
               simulated.get(property) && required.get(property) &&
               (!existing.contains(property) || !existing.get(property));
    }

    // Check if the BlockHitResult has the correct rotation
    private boolean isMatchingDirection(float yaw, float pitch, BlockHitResult blockHit) {
        simulated = getSimulatedPlaceState(yaw, pitch, blockHit);
        if (simulated == null) return false;

        // Check if our facing direction is the same
        if (required.contains(Properties.FACING) && simulated.contains(Properties.FACING)) {
            if (required.get(Properties.FACING) != simulated.get(Properties.FACING)) return false;
        } else if (required.contains(Properties.HORIZONTAL_FACING) && simulated.contains(Properties.HORIZONTAL_FACING)) {
            if (required.get(Properties.HORIZONTAL_FACING) != simulated.get(Properties.HORIZONTAL_FACING)) return false;
        } else if (required.contains(Properties.HORIZONTAL_AXIS) && simulated.contains(Properties.HORIZONTAL_AXIS)) {
            if (required.get(Properties.HORIZONTAL_AXIS) != simulated.get(Properties.HORIZONTAL_AXIS)) return false;
        } else if (required.contains(Properties.AXIS) && simulated.contains(Properties.AXIS)) {
            if (required.get(Properties.AXIS) != simulated.get(Properties.AXIS)) return false;
        } else if (required.contains(Properties.HOPPER_FACING) && simulated.contains(Properties.HOPPER_FACING)) {
            if (required.get(Properties.HOPPER_FACING) != simulated.get(Properties.HOPPER_FACING)) return false;
        } else if (required.contains(Properties.ORIENTATION) && simulated.contains(Properties.ORIENTATION)) {
            if (required.get(Properties.ORIENTATION) != simulated.get(Properties.ORIENTATION)) return false;
        } else if (required.contains(Properties.VERTICAL_DIRECTION) && simulated.contains(Properties.VERTICAL_DIRECTION)) {
            if (required.get(Properties.VERTICAL_DIRECTION) != simulated.get(Properties.VERTICAL_DIRECTION)) return false;
        } else if (required.contains(Properties.ROTATION) && simulated.contains(Properties.ROTATION)) {
            if (required.get(Properties.ROTATION) != simulated.get(Properties.ROTATION)) return false;
        }

        return true;
    }

    // Calculate rotation from a hit. Also sets Yaw and Pitch the player should use when placing the block.
    private boolean isMatchingRotation(BlockHitResult blockHit) {
        if (!printer.rotationPlace.get() || !rotatable) return true;

        float legitYaw = (float) Rotations.getYaw(blockHit.getPos());
        float legitPitch = (float) Rotations.getPitch(blockHit.getPos());

        // Hack rotation calculation
        if (!printer.strictRotation.get()) {
            List<Float> yaws = getSimulationYaws(legitYaw);
            List<Float> pitches = getSimulationPitches(legitPitch);
            for (float rotateYaw : yaws) {
                for (float rotatePitch : pitches) {
                    if (!isMatchingDirection(rotateYaw, rotatePitch, blockHit)) continue;
                    placeYaw = rotateYaw;
                    placePitch = rotatePitch;
                    useRotation = true;
                    return true;
                }
            }
        }

        // Strict rotation calculation
        if (isMatchingDirection(legitYaw, legitPitch, blockHit)) {
            placeYaw = legitYaw;
            placePitch = legitPitch;
            useRotation = true;
            return true;
        }

        return false;
    }

    // Determines if we can place again to increment the block state.
    public static boolean isIncremental(BlockState required, BlockState existing) {
        Block block = required.getBlock();
        if (block.getStateManager().getProperties().isEmpty()) return false;

        if (block != existing.getBlock()) return false;

        if (required.contains(Properties.SLAB_TYPE) && existing.contains(Properties.SLAB_TYPE)) {
            return required.get(Properties.SLAB_TYPE) == SlabType.DOUBLE && existing.get(Properties.SLAB_TYPE) != SlabType.DOUBLE;
        } else if (required.contains(Properties.LAYERS) && existing.contains(Properties.LAYERS)) {
            return required.get(Properties.LAYERS) > existing.get(Properties.LAYERS);
        } else if (required.contains(Properties.EGGS) && existing.contains(Properties.EGGS)) {
            return required.get(Properties.EGGS) > existing.get(Properties.EGGS);
        } else if (required.contains(Properties.CANDLES) && existing.contains(Properties.CANDLES)) {
            return required.get(Properties.CANDLES) > existing.get(Properties.CANDLES);
        } else if (required.contains(Properties.PICKLES) && existing.contains(Properties.PICKLES)) {
            return required.get(Properties.PICKLES) > existing.get(Properties.PICKLES);
        } else if (required.contains(Properties.FLOWER_AMOUNT) && existing.contains(Properties.FLOWER_AMOUNT)) {
            return required.get(Properties.FLOWER_AMOUNT) > existing.get(Properties.FLOWER_AMOUNT);
        } else if (required.contains(Properties.SEGMENT_AMOUNT) && existing.contains(Properties.SEGMENT_AMOUNT)) {
            return required.get(Properties.SEGMENT_AMOUNT) > existing.get(Properties.SEGMENT_AMOUNT);
        }

        if (block instanceof MultifaceGrowthBlock || block instanceof VineBlock) {
            if (required.contains(Properties.UP) && required.get(Properties.UP) && !existing.get(Properties.UP)) return true;
            if (required.contains(Properties.DOWN) && required.get(Properties.DOWN) && !existing.get(Properties.DOWN)) return true;
            if (required.contains(Properties.EAST) && required.get(Properties.EAST) && !existing.get(Properties.EAST)) return true;
            if (required.contains(Properties.NORTH) && required.get(Properties.NORTH) && !existing.get(Properties.NORTH)) return true;
            if (required.contains(Properties.SOUTH) && required.get(Properties.SOUTH) && !existing.get(Properties.SOUTH)) return true;
            if (required.contains(Properties.WEST) && required.get(Properties.WEST) && !existing.get(Properties.WEST)) return true;
        }
        return false;
    }

    private boolean isPointValid(Vec3d point, BlockPos pos, Direction direction) {
        if (!PrinterUtils.isPointInRange(point, printer.placeRange.get())) return false;

        if ((!printer.wallPlace.get() || fluid) && !PrinterUtils.isPointVisible(point, pos, direction)) return false;

        return true;
    }

    // Yaws to simulate when placing blocks with the ROTATION property. e.g signs, banners
    private static final List<Float> ROTATION_YAWS = List.of(
        0.0f, 22.5f, 45.0f, 67.5f,
        90.0f, 112.5f, 135.0f, 157.5f,
        180.0f, 202.5f, 225.0f, 247.5f,
        270.0f, 292.5f, 315.0f, 337.5f
    );

    // Yaws to simulate when placing rotatable blocks
    private static final List<Float> CARDINAL_YAWS = List.of(
        0.0f, 90.0f, 180.0f, 270.0f
    );

    // Pitches to simulate when placing rotatable blocks
    private static final List<Float> VERTICAL_PITCHES = List.of(
        0.0f, 90.0f, -90.0f
    );

    // Gets a series of yaws to simulate when placing
    private List<Float> getSimulationYaws(float legitYaw) {
        if (required.contains(Properties.ROTATION)) {
            return ROTATION_YAWS;
        } else if (required.contains(Properties.HORIZONTAL_FACING) ||
            required.contains(Properties.FACING) ||
            required.contains(Properties.BLOCK_FACE) ||
            required.contains(Properties.ORIENTATION)) {
            return CARDINAL_YAWS;
        }
        return List.of(legitYaw);
    }

    // Gets a series of pitches to simulate when placing
    private List<Float> getSimulationPitches(float legitPitch) {
        if (required.contains(Properties.FACING) ||
            required.contains(Properties.VERTICAL_DIRECTION) ||
            required.contains(Properties.ORIENTATION) ||
            required.contains(Properties.BLOCK_FACE) ||
            required.contains(Properties.ROTATION)) {
            return VERTICAL_PITCHES;
        }
        return List.of(legitPitch);
    }

    // Account for edge case blocks that dont map to their respective placer item properly
    public Item asItem() {
        Block block = required.getBlock();
        if (block instanceof FlowerPotBlock) return Items.FLOWER_POT;

        if (fluid) {
            if (block == Blocks.WATER) return Items.WATER_BUCKET;
            if (block == Blocks.LAVA)  return Items.LAVA_BUCKET;
        }

        return block.asItem();
    }
}
