
/**
    BlockPrint

    This class deals with matching blockstates when placing blocks.

    This is where most of the advanced functionality of dino printer is located.
**/

package dinosaurwizard.dinoprinter.utils;

import dinosaurwizard.dinoprinter.modules.DinoPrinter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.List;
import java.util.Set;
import net.minecraft.screen.NamedScreenHandlerFactory;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BlockPrint {
    private final DinoPrinter printer;
    public final BlockPos blockPos;
    public final BlockState required;
    public final BlockState existing;
    public final BlockHitResult hit;
    public final boolean fluid;
    private final boolean rotatable;
    private float placeYaw;
    private float placePitch;
    private boolean useRotation = false;
    public boolean sneaking;

    public BlockPrint(BlockPos blockPos, BlockState required, BlockState existing, DinoPrinter printer) {
        this.printer = printer;
        this.blockPos = blockPos;
        this.required = required;
        this.existing = existing;

        this.fluid = required.getBlock() instanceof LiquidBlock;
        this.rotatable = isRotatable();
        this.hit = calculateBestPlaceHit();
    }

    // Determine if a location can be printed at
    public static boolean isValid(BlockPos blockPos, BlockState required, BlockState existing, DinoPrinter printer) {
        if (required.isAir()) return false;

        boolean fluid = required.getBlock() instanceof LiquidBlock;
        if (fluid && (!required.getFluidState().isSource() || !printer.fluids.get())) return false;

        if (!printer.incrementalStates.get() || !isIncremental(required, existing)) {
            // Spot must be air or some other replaceable block
            if (!existing.canBeReplaced()) return false;

            // Spot is not already the required blockstate
            if (required.getBlock() == existing.getBlock() && (!fluid || existing.getFluidState().isSource())) return false;

            // Don't place in a position we already attempted
            if (printer.cachedPositions.contains(blockPos)) return false;
        }

        // Check if legally placeable. For example, if its a torch, it can only be placed onto another block.
        if (!required.canSurvive(mc.level, blockPos)) return false;

        // No intersecting entities at our position
        if (!mc.level.isUnobstructed(required, blockPos, CollisionContext.empty())) return false;

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
        return required.hasProperty(BlockStateProperties.FACING) || 
               required.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ||
               required.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) ||
               required.hasProperty(BlockStateProperties.AXIS) ||
               required.hasProperty(BlockStateProperties.FACING_HOPPER) ||
               required.hasProperty(BlockStateProperties.ORIENTATION) ||
               required.hasProperty(BlockStateProperties.VERTICAL_DIRECTION) || 
               required.hasProperty(BlockStateProperties.ROTATION_16);
    }

    // Yaw when placing the block
    public double getYaw() {
        if (useRotation) return placeYaw;

        return RotationManager.getYaw(hit.getLocation());
    }

    // Pitch when placing the block
    public double getPitch() {
        if (useRotation) return placePitch;

        return RotationManager.getPitch(hit.getLocation());
    }

    // Gets a simulated place state using customizable inputs.
    // The simulated place state is effectively the blockState when it is actually placed, allowing us to determine what it will look like before it is placed.
    // This can be used to compare certain attributes such as the facing direction.
    private BlockState getSimulatedPlaceState(float yaw, float pitch, BlockHitResult blockHit) {
        ItemStack stack = asItem().getDefaultInstance();
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;

        PrinterPlaceContext context = new PrinterPlaceContext(mc.player, yaw, pitch, sneaking, InteractionHand.MAIN_HAND, stack, blockHit);
        if (!existing.canBeReplaced(context)) return null;

        return blockItem.getPlacementState(context);
    }

    // Gives the best possible hit result using relevant requirements
    private BlockHitResult calculateBestPlaceHit() {
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = blockPos.offset(direction);
            if (mc.world.getBlockState(adjacent).isReplaceable()) continue;

            Direction opposite = direction.getOpposite();
            Set<Vec3> points = PrinterUtils.getShapeFacePoints(adjacent, opposite);
            for (Vec3 point : points) {
                if (!isPointValid(point, adjacent, opposite)) continue;

                BlockHitResult placeHit = new BlockHitResult(point, opposite, adjacent, false);
                if (!isMatchingRequirements(placeHit)) continue;

                return placeHit;
            }
        }

        // If we couldn't find an adjacent block to place onto, check points on our own block position
        // Only for air placement or if we have an incremental block
        boolean incremental = printer.incrementalStates.get() && required.getBlock() == existing.getBlock();
        if ((printer.airPlace.get() || incremental) && !fluid) {
            for (Direction direction : Direction.values()) {
                Set<Vec3> points = PrinterUtils.getShapeFacePoints(blockPos, direction);
                for (Vec3 point : points) {
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
        sneaking = printer.sneakPlace.get();

        if (printer.sneakPlace.get() || mc.player.isShiftKeyDown()) {
            sneaking = true;
            if (isMatchingProperties(placeHit) && isMatchingRotation(placeHit)) return true;
        }

        if (!mc.player.isShiftKeyDown()) {
            sneaking = false;
            if (isMatchingProperties(placeHit) && isMatchingRotation(placeHit)) return true;
        }

        return false;
    }

    // Check if the BlockHitResult has the same properties
    private boolean isMatchingProperties(BlockHitResult blockHit) {
        Block block = required.getBlock();

        // Ignore property matching for blocks:
        if (block instanceof TripwireBlock || block instanceof TripwireHookBlock || fluid) return true;

        // Never allow interactions to go through
        if (!sneaking && isInteractable(blockHit.getBlockPos())) return false;

        if (required.getBlock().getStateManager().getProperties().isEmpty()) return true;

        float yaw = (float) RotationManager.getYaw(blockHit.getLocation());
        float pitch = (float) RotationManager.getPitch(blockHit.getLocation());
        BlockState simulated = getSimulatedPlaceState(yaw, pitch, blockHit);
        if (simulated == null) return false;

        // Unequal blocks get booted. e.g standing signs vs wall signs
        if (block != simulated.getBlock()) return false;

        if (printer.halfBlocks.get()) {
            // Slab Type - half slabs & also handling of double slabs
            if (required.hasProperty(BlockStateProperties.SLAB_TYPE) && simulated.hasProperty(BlockStateProperties.SLAB_TYPE)) {
                SlabType requiredType = required.getValue(BlockStateProperties.SLAB_TYPE);
                SlabType simulatedType = simulated.getValue(BlockStateProperties.SLAB_TYPE);
                if (requiredType == SlabType.DOUBLE) {
                    if (existing.hasProperty(BlockStateProperties.SLAB_TYPE)) {
                        if (simulatedType != SlabType.DOUBLE) return false;
                    }
                } else {
                    if (requiredType != simulatedType) return false;
                }
            // Block Half - stairs, trapdoors
            } else if (required.hasProperty(BlockStateProperties.HALF) && simulated.hasProperty(BlockStateProperties.HALF)) {
                if (required.getValue(BlockStateProperties.HALF) != simulated.getValue(BlockStateProperties.HALF)) return false;
            }
        }

        if (printer.miscStates.get()) {
            // Door Hinge - doors
            if (required.hasProperty(BlockStateProperties.DOOR_HINGE) && simulated.hasProperty(BlockStateProperties.DOOR_HINGE)) {
                if (required.getValue(BlockStateProperties.DOOR_HINGE) != simulated.getValue(BlockStateProperties.DOOR_HINGE)) return false;
            // Block Face - wall mounted blocks like torches or levers
            } else if (required.hasProperty(BlockStateProperties.ATTACH_FACE) && simulated.hasProperty(BlockStateProperties.ATTACH_FACE)) {
                if (required.getValue(BlockStateProperties.ATTACH_FACE) != simulated.getValue(BlockStateProperties.ATTACH_FACE)) return false;
            // Attachment - bells
            } else if (required.hasProperty(BlockStateProperties.BELL_ATTACHMENT) && simulated.hasProperty(BlockStateProperties.BELL_ATTACHMENT)) {
                if (required.getValue(BlockStateProperties.BELL_ATTACHMENT) != simulated.getValue(BlockStateProperties.BELL_ATTACHMENT)) return false;
            // Attached - hanging signs
            } else if (required.hasProperty(BlockStateProperties.ATTACHED) && simulated.hasProperty(BlockStateProperties.ATTACHED)) {
                if (required.getValue(BlockStateProperties.ATTACHED) != simulated.getValue(BlockStateProperties.ATTACHED)) return false;
            // Hanging - lanterns
            } else if (required.hasProperty(BlockStateProperties.HANGING) && simulated.hasProperty(BlockStateProperties.HANGING)) {
                if (required.getValue(BlockStateProperties.HANGING) != simulated.getValue(BlockStateProperties.HANGING)) return false;
            // Bed Part - beds
            } else if (required.hasProperty(BlockStateProperties.BED_PART) && simulated.hasProperty(BlockStateProperties.BED_PART)) {
                if (required.getValue(BlockStateProperties.BED_PART) != simulated.getValue(BlockStateProperties.BED_PART)) return false;
            // Chest Type - chests 
            } else if (required.hasProperty(BlockStateProperties.CHEST_TYPE) && simulated.hasProperty(BlockStateProperties.CHEST_TYPE)) {
                ChestType requiredType = required.getValue(BlockStateProperties.CHEST_TYPE);
                ChestType simulatedType = simulated.getValue(BlockStateProperties.CHEST_TYPE);
                if (requiredType != ChestType.SINGLE) {
                    BlockPos neighborPos = blockPos.relative(ChestBlock.getConnectedDirection(required));
                    BlockState neighborState = mc.level.getBlockState(neighborPos);
                    if (neighborState.hasProperty(BlockStateProperties.CHEST_TYPE) && requiredType != simulatedType) return false;
                } else {
                    if (requiredType != simulatedType) return false;
                }
            }
        }

        if (printer.incrementalStates.get()) {
            // Incremental states - snow layers, turtle eggs, candles, pickles, wildflowers, leaflitter
            if (simulated.hasProperty(BlockStateProperties.LAYERS) && existing.hasProperty(BlockStateProperties.LAYERS)) {
                if (simulated.getValue(BlockStateProperties.LAYERS) <= existing.getValue(BlockStateProperties.LAYERS)) return false;
            } else if (simulated.hasProperty(BlockStateProperties.EGGS) && existing.hasProperty(BlockStateProperties.EGGS)) {
                if (simulated.getValue(BlockStateProperties.EGGS) <= existing.getValue(BlockStateProperties.EGGS)) return false;
            } else if (simulated.hasProperty(BlockStateProperties.CANDLES) && existing.hasProperty(BlockStateProperties.CANDLES)) {
                if (simulated.getValue(BlockStateProperties.CANDLES) <= existing.getValue(BlockStateProperties.CANDLES)) return false;
            } else if (simulated.hasProperty(BlockStateProperties.PICKLES) && existing.hasProperty(BlockStateProperties.PICKLES)) {
                if (simulated.getValue(BlockStateProperties.PICKLES) <= existing.getValue(BlockStateProperties.PICKLES)) return false;
            } else if (simulated.hasProperty(BlockStateProperties.FLOWER_AMOUNT) && existing.hasProperty(BlockStateProperties.FLOWER_AMOUNT)) {
                if (simulated.getValue(BlockStateProperties.FLOWER_AMOUNT) <= existing.getValue(BlockStateProperties.FLOWER_AMOUNT)) return false;
            } else if (simulated.hasProperty(BlockStateProperties.SEGMENT_AMOUNT) && existing.hasProperty(BlockStateProperties.SEGMENT_AMOUNT)) {
                if (simulated.getValue(BlockStateProperties.SEGMENT_AMOUNT) <= existing.getValue(BlockStateProperties.SEGMENT_AMOUNT)) return false;
            }

            // Multi face blocks - vines, glow lichen, sculk veins
            if (block instanceof MultifaceSpreadeableBlock || block instanceof VineBlock) {
                if (isMatchingPropertyMultiFace(simulated, BlockStateProperties.UP)) return true;
                if (isMatchingPropertyMultiFace(simulated, BlockStateProperties.DOWN)) return true;
                if (isMatchingPropertyMultiFace(simulated, BlockStateProperties.EAST)) return true;
                if (isMatchingPropertyMultiFace(simulated, BlockStateProperties.NORTH)) return true;
                if (isMatchingPropertyMultiFace(simulated, BlockStateProperties.SOUTH)) return true;
                if (isMatchingPropertyMultiFace(simulated, BlockStateProperties.WEST)) return true;
                return false;
            }
        }
        return true;
    }

    private boolean isMatchingPropertyMultiFace(BlockState simulated, Property<Boolean> property) {
        return simulated.hasProperty(property) && required.hasProperty(property) &&
               simulated.getValue(property) && required.getValue(property) &&
               (!existing.hasProperty(property) || !existing.getValue(property));
    }

    // Check if the BlockHitResult has the correct rotation
    private boolean isMatchingDirection(float yaw, float pitch, BlockHitResult blockHit) {
        BlockState simulated = getSimulatedPlaceState(yaw, pitch, blockHit);
        if (simulated == null) return false;

        // Check if our facing direction is the same
        if (required.hasProperty(BlockStateProperties.FACING) && simulated.hasProperty(BlockStateProperties.FACING)) {
            if (required.getValue(BlockStateProperties.FACING) != simulated.getValue(BlockStateProperties.FACING)) return false;
        } else if (required.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && simulated.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            if (required.getValue(BlockStateProperties.HORIZONTAL_FACING) != simulated.getValue(BlockStateProperties.HORIZONTAL_FACING)) return false;
        } else if (required.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) && simulated.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            if (required.getValue(BlockStateProperties.HORIZONTAL_AXIS) != simulated.getValue(BlockStateProperties.HORIZONTAL_AXIS)) return false;
        } else if (required.hasProperty(BlockStateProperties.AXIS) && simulated.hasProperty(BlockStateProperties.AXIS)) {
            if (required.getValue(BlockStateProperties.AXIS) != simulated.getValue(BlockStateProperties.AXIS)) return false;
        } else if (required.hasProperty(BlockStateProperties.FACING_HOPPER) && simulated.hasProperty(BlockStateProperties.FACING_HOPPER)) {
            if (required.getValue(BlockStateProperties.FACING_HOPPER) != simulated.getValue(BlockStateProperties.FACING_HOPPER)) return false;
        } else if (required.hasProperty(BlockStateProperties.ORIENTATION) && simulated.hasProperty(BlockStateProperties.ORIENTATION)) {
            if (required.getValue(BlockStateProperties.ORIENTATION) != simulated.getValue(BlockStateProperties.ORIENTATION)) return false;
        } else if (required.hasProperty(BlockStateProperties.VERTICAL_DIRECTION) && simulated.hasProperty(BlockStateProperties.VERTICAL_DIRECTION)) {
            if (required.getValue(BlockStateProperties.VERTICAL_DIRECTION) != simulated.getValue(BlockStateProperties.VERTICAL_DIRECTION)) return false;
        } else if (required.hasProperty(BlockStateProperties.ROTATION_16) && simulated.hasProperty(BlockStateProperties.ROTATION_16)) {
            if (required.getValue(BlockStateProperties.ROTATION_16) != simulated.getValue(BlockStateProperties.ROTATION_16)) return false;
        }

        return true;
    }

    // Calculate rotation from a hit. Also sets Yaw and Pitch the player should use when placing the block.
    private boolean isMatchingRotation(BlockHitResult blockHit) {
        if (!printer.rotationPlace.get() || !rotatable) return true;

        float legitYaw = (float) RotationManager.getYaw(blockHit.getLocation());
        float legitPitch = (float) RotationManager.getPitch(blockHit.getLocation());

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
        if (block.getStateDefinition().getProperties().isEmpty()) return false;

        if (block != existing.getBlock()) return false;

        if (required.hasProperty(BlockStateProperties.SLAB_TYPE) && existing.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            return required.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE && existing.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE;
        } else if (required.hasProperty(BlockStateProperties.LAYERS) && existing.hasProperty(BlockStateProperties.LAYERS)) {
            return required.getValue(BlockStateProperties.LAYERS) > existing.getValue(BlockStateProperties.LAYERS);
        } else if (required.hasProperty(BlockStateProperties.EGGS) && existing.hasProperty(BlockStateProperties.EGGS)) {
            return required.getValue(BlockStateProperties.EGGS) > existing.getValue(BlockStateProperties.EGGS);
        } else if (required.hasProperty(BlockStateProperties.CANDLES) && existing.hasProperty(BlockStateProperties.CANDLES)) {
            return required.getValue(BlockStateProperties.CANDLES) > existing.getValue(BlockStateProperties.CANDLES);
        } else if (required.hasProperty(BlockStateProperties.PICKLES) && existing.hasProperty(BlockStateProperties.PICKLES)) {
            return required.getValue(BlockStateProperties.PICKLES) > existing.getValue(BlockStateProperties.PICKLES);
        } else if (required.hasProperty(BlockStateProperties.FLOWER_AMOUNT) && existing.hasProperty(BlockStateProperties.FLOWER_AMOUNT)) {
            return required.getValue(BlockStateProperties.FLOWER_AMOUNT) > existing.getValue(BlockStateProperties.FLOWER_AMOUNT);
        } else if (required.hasProperty(BlockStateProperties.SEGMENT_AMOUNT) && existing.hasProperty(BlockStateProperties.SEGMENT_AMOUNT)) {
            return required.getValue(BlockStateProperties.SEGMENT_AMOUNT) > existing.getValue(BlockStateProperties.SEGMENT_AMOUNT);
        }

        if (block instanceof MultifaceSpreadeableBlock || block instanceof VineBlock) {
            if (required.hasProperty(BlockStateProperties.UP) && required.getValue(BlockStateProperties.UP) && !existing.getValue(BlockStateProperties.UP)) return true;
            if (required.hasProperty(BlockStateProperties.DOWN) && required.getValue(BlockStateProperties.DOWN) && !existing.getValue(BlockStateProperties.DOWN)) return true;
            if (required.hasProperty(BlockStateProperties.EAST) && required.getValue(BlockStateProperties.EAST) && !existing.getValue(BlockStateProperties.EAST)) return true;
            if (required.hasProperty(BlockStateProperties.NORTH) && required.getValue(BlockStateProperties.NORTH) && !existing.getValue(BlockStateProperties.NORTH)) return true;
            if (required.hasProperty(BlockStateProperties.SOUTH) && required.getValue(BlockStateProperties.SOUTH) && !existing.getValue(BlockStateProperties.SOUTH)) return true;
            if (required.hasProperty(BlockStateProperties.WEST) && required.getValue(BlockStateProperties.WEST) && !existing.getValue(BlockStateProperties.WEST)) return true;
        }
        return false;
    }

    public boolean isPointValid() {
        return isPointValid(hit.getLocation(), hit.getBlockPos(), hit.getDirection());
    }

    private boolean isPointValid(Vec3 point, BlockPos pos, Direction direction) {
        if (!PrinterUtils.isPointInRange(point, printer.placeRange.get())) return false;

        if ((!printer.wallPlace.get() || fluid) && !PrinterUtils.isPointVisible(point, pos, direction)) return false;

        return true;
    }

    // Determines if the block consumes an interaction. Not perfect
    private static boolean isInteractable(BlockPos blockPos) {
        // Blocks that have screens
        if (mc.world.getBlockEntity(blockPos) instanceof NamedScreenHandlerFactory) return true;
        
        BlockState state = mc.world.getBlockState(blockPos);
        // Blocks with screen-handler factories (e.g. crafting tables)
        if (state.createScreenHandlerFactory(mc.world, blockPos) != null) return true;

        Block block = state.getBlock();
        return block instanceof DoorBlock
            || block instanceof TrapdoorBlock
            || block instanceof FenceGateBlock
            || block instanceof ButtonBlock
            || block instanceof LeverBlock
            || block instanceof RepeaterBlock
            || block instanceof ComparatorBlock
            || block instanceof DaylightDetectorBlock
            || block instanceof NoteBlock
            || block instanceof BellBlock
            || block instanceof BedBlock
            || block instanceof DragonEggBlock 
            || block instanceof LecternBlock;
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
        if (required.hasProperty(BlockStateProperties.ROTATION_16)) {
            return ROTATION_YAWS;
        } else if (required.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ||
            required.hasProperty(BlockStateProperties.FACING) ||
            required.hasProperty(BlockStateProperties.ATTACH_FACE) ||
            required.hasProperty(BlockStateProperties.ORIENTATION)) {
            return CARDINAL_YAWS;
        }
        return List.of(legitYaw);
    }

    // Gets a series of pitches to simulate when placing
    private List<Float> getSimulationPitches(float legitPitch) {
        if (required.hasProperty(BlockStateProperties.FACING) ||
            required.hasProperty(BlockStateProperties.VERTICAL_DIRECTION) ||
            required.hasProperty(BlockStateProperties.ORIENTATION) ||
            required.hasProperty(BlockStateProperties.ATTACH_FACE) ||
            required.hasProperty(BlockStateProperties.ROTATION_16)) {
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
