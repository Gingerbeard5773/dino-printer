/**

  Dino Printer by dinosaurwizard
  ------------------------------
  It is designed so that it can work on every relevant anti-cheat, whilst still offering most 'advanced' features.
  Made from scratch, it is fundamentally different from all other printers. Enjoy!

**/

package dinosaurwizard.dinoprinter.modules;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public class DinoPrinter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgPause = settings.createGroup("Pause");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("The tick delay between placing blocks.")
        .defaultValue(1)
        .range(0, 10)
        .build()
    );

    private final Setting<Integer> placeRetryDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-retry-delay")
        .description("Delay in ticks to retry placing a block if it failed the first time.")
        .defaultValue(20)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("How far away from the player you can place a block.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place in one tick.")
        .defaultValue(1)
        .min(1)
        .build()
    );

    private final Setting<Boolean> wallPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("wall-place")
        .description("Allow placement through walls. Turn off to use raytracing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Allow placement in the air.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sneakPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("sneak-place")
        .description("Sends sneaking packets when placing blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotationPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("rotation-place")
        .description("Respect block rotation.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> strictRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("strict-rotation")
        .description("Determines if rotatable blocks can only be placed legitimently.")
        .defaultValue(false)
        .visible(rotationPlace::get)
        .build()
    );

    private final Setting<Boolean> halfBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("half-blocks")
        .description("Respect block half. Necessary for properly placing slabs and stairs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> miscStates = sgGeneral.add(new BoolSetting.Builder()
        .name("misc-states")
        .description("Respect miscellaneous states. Blocks like doors, hanging signs, and levers are affected. ")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the blocks being placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SortAlgorithm> firstAlgorithm = sgGeneral.add(new EnumSetting.Builder<SortAlgorithm>()
        .name("first-sorting-mode")
        .description("The blocks you want to place first.")
        .defaultValue(SortAlgorithm.None)
        .build()
    );

    private final Setting<SortingSecond> secondAlgorithm = sgGeneral.add(new EnumSetting.Builder<SortingSecond>()
        .name("second-sorting-mode")
        .description("Second pass of sorting eg. place first blocks higher and closest to you.")
        .defaultValue(SortingSecond.None)
        .visible(()-> firstAlgorithm.get().applySecondSorting)
        .build()
    );

    // Inventory

    private final Setting<Boolean> autoSwitch = sgInventory.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Switches to placeable blocks in your hotbar automatically.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swapBack = sgInventory.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Switches to your previous slot after placing blocks.")
        .defaultValue(true)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Boolean> allowInventory = sgInventory.add(new BoolSetting.Builder()
        .name("allow-inventory")
        .description("Allows blocks to be moved into your hotbar from your inventory.")
        .defaultValue(true)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Boolean> stationaryMove = sgInventory.add(new BoolSetting.Builder()
        .name("stationary-move")
        .description("Only allows blocks to be moved from your inventory if you are standing still. Required for certain anti-cheats.")
        .defaultValue(false)
        .visible(() -> autoSwitch.get() && allowInventory.get())
        .build()
    );

    // Pause

    private final Setting<Boolean> pauseOnUse = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Pauses while using an item.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnMine = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-mine")
        .description("Pauses while mining blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnKillAura = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-kill-aura")
        .description("Pauses while using kill aura.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pauses when the server stops responding.")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<Boolean> swing = sgRender.add(new BoolSetting.Builder()
        .name("swing")
        .description("Render your hand swinging when placing blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders block placements.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> fadeTime = sgRender.add(new IntSetting.Builder()
        .name("fade-time")
        .description("Time for the rendering to fade, in ticks.")
        .defaultValue(4)
        .range(0, 10)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("The color to render the placement as.")
        .defaultValue(new SettingColor(177, 236, 248, 100))
        .visible(render::get)
        .build()
    );

    private int placeTimer;
    private int cacheTimer;
    private final Set<BlockPos> cachedPositions = new ObjectOpenHashSet<>();
    private final List<BlockPrint> blockPrints = new ArrayList<>();
    private final List<PlacedFade> placedFades = new ArrayList<>();

    // Air shape is the air place shape. It is slightly smaller than a normal minecraft block.
    private static final VoxelShape airShape = VoxelShapes.cuboid(0.01, 0.01, 0.01, 1.0 - 0.01, 1.0 - 0.01, 1.0 - 0.01);

    public DinoPrinter() {
        super(Categories.World, "dino-printer", "Prints rendered litematica schematics.");
    }

    @Override
    public void onActivate() {
        placeTimer = 0;
        cacheTimer = 0;
        cachedPositions.clear();
        blockPrints.clear();
        placedFades.clear();
    }

    @Override
    public void onDeactivate() {
        placeTimer = 0;
        cacheTimer = 0;
        cachedPositions.clear();
        blockPrints.clear();
        placedFades.clear();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        // Tick or remove each fade
        placedFades.forEach(fade -> fade.ticks--);
        placedFades.removeIf(fade -> fade.ticks <= 0);

        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
        if (worldSchematic == null) {
            toggle();
            return;
        }

        if (shouldPause()) return;

        // Clear cached positions every so often
        if (!cachedPositions.isEmpty() && cacheTimer++ > placeRetryDelay.get()) {
            cachedPositions.clear();
            cacheTimer = 0;
        }

        // Delay
        if (placeTimer++ < placeDelay.get()) return;

        // Find print locations
        BlockIterator.register((int) Math.ceil(placeRange.get()), (int) Math.ceil(placeRange.get()), (blockPos, existing) -> {
            BlockState required = worldSchematic.getBlockState(blockPos);
            // Spot must be air or some other replaceable block
            if (!existing.isReplaceable()) return;

            // Spot cannot be required blockstate
            if (existing.getBlock() == required.getBlock()) return;

            // Blacklisted states
            if (!required.getFluidState().isEmpty() || required.isAir()) return;

            // Dont place in a position we already attempted
            if (cachedPositions.contains(blockPos)) return;

            // Only rendered schematic blocks can be placed
            if (!DataManager.getRenderLayerRange().isPositionWithinRange(blockPos)) return;

            // Spot must be have no entities overlapping and is below world height
            if (!BlockUtils.canPlace(blockPos)) return;

            // Check if legally placeable. For example, if its a torch, it can only be placed onto another block.
            if (!required.canPlaceAt(mc.world, blockPos)) return;

            BlockPrint blockPrint = new BlockPrint(new BlockPos(blockPos), required);

            // Block specific requirements must be met from our printer.
            if (!blockPrint.canPlace()) return;

            blockPrints.add(blockPrint);
        });
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (blockPrints.isEmpty()) return;

        // Sort blocks
        if (firstAlgorithm.get() != SortAlgorithm.None) {
            if (firstAlgorithm.get().applySecondSorting && secondAlgorithm.get() != SortingSecond.None) {
                blockPrints.sort(secondAlgorithm.get().algorithm);
            }
            blockPrints.sort(firstAlgorithm.get().algorithm);
        }

        // Place blocks!
        int placedCount = 0;
        for (BlockPrint blockPrint : blockPrints) {
            if (placedCount >= blocksPerTick.get()) break;

            Item item = blockPrint.required.getBlock().asItem();
            FindItemResult result = InvUtils.find(itemStack -> item == itemStack.getItem());
            if (!result.found()) continue;

            // Move items into hotbar if allowed
            if (!result.isHotbar() && autoSwitch.get() && allowInventory.get()) {
                if (!stationaryMove.get() || mc.player.getVelocity().multiply(1, 0, 1).length() < 0.00001) {
                    int slotToUse = getSwapSlotToUse();
                    InvUtils.quickSwap().fromId(slotToUse).to(result.slot());

                    // It takes a tick for the server to register a swap, so exit out now.
                    blockPrints.clear();
                    return;
                }
            }

            if (!result.isHotbar()) continue;

            if (!autoSwitch.get() && mc.player.getInventory().getSelectedSlot() != result.slot()) continue;

            place(blockPrint, result);

            if (render.get()) {
                placedFades.add(new PlacedFade(fadeTime.get(), blockPrint.blockPos));
            }

            cachedPositions.add(blockPrint.blockPos);
            placedCount++;
        }

        blockPrints.clear();
        placeTimer = 0;
    }

    private boolean shouldPause() {
        if (pauseOnUse.get()) {
            if (Modules.get().get(AutoEat.class).eating || Modules.get().get(AutoGap.class).isEating()) return true;

            if (mc.player.isUsingItem()) return true;
        }

        if (pauseOnMine.get() && mc.interactionManager.isBreakingBlock()) return true;

        if (pauseOnKillAura.get() && Modules.get().get(KillAura.class).attacking) return true;

        if (pauseOnLag.get() && TickRate.INSTANCE.getTimeSinceLastTick() >= 1.5f) return true;

        return false;
    }

    // Find a good slot to swap items into
    private int getSwapSlotToUse() {
        FindItemResult empty = InvUtils.findEmpty();
        if (empty.found() && empty.isHotbar()) return empty.slot();

        return mc.player.getInventory().getSelectedSlot();
    }

    // Custom placement that is far better than meteor's standard
    private void place(BlockPrint blockPrint, FindItemResult result) {
        if (rotate.get() || blockPrint.shouldRotatePlace()) {
            Rotations.rotate(blockPrint.getYaw(), blockPrint.getPitch(), () -> {
                interactPlace(blockPrint.hit, result);
            });
        } else {
            interactPlace(blockPrint.hit, result);
        }
    }

    private void interactPlace(BlockHitResult hit, FindItemResult result) {
        // Send our inputs with sneaking injected
        boolean isSneaking = mc.player.isSneaking();
        PlayerInput old = mc.player.input.playerInput;
        if (sneakPlace.get() && !isSneaking) {
            PlayerInput sneak = new PlayerInput(old.forward(), old.backward(), old.left(), old.right(), old.jump(), true, old.sprint());
            mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(sneak));
            mc.player.setSneaking(true);
        }

        InvUtils.swap(result.slot(), swapBack.get());

        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted()) {
            if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
            else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        if (swapBack.get()) InvUtils.swapBack();

        // Go back to our old inputs
        if (sneakPlace.get() && !isSneaking) {
            mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(old));
            mc.player.setSneaking(isSneaking);
        }
    }

    /**
        Dino Printer code is complex. Lets help you visualize what happens in the BlockPrint class below.
        This is where the fundamentals of Dino Printer are.

             +---------------------+
            /                     /|
           /                     / |   Here we have a diagram of a block's face.
          /                     /  |   Lets say dino printer wants to see if this is a good block to place onto-
         /                     /   |   First it gets the shape of the adjacent block, then it creates a set of points that correspond with the shape's vertices.
        +---------------------+    |   Each spot is tested to see if it passes all requirements.
        |                     |    |   What are those requirements?
        | X        X        X |    |   Raycasting: can we see the point from our player's eyes?
        |                     |    |   Half block: if the block we want to place is a slab, should we place on the top or bottom of the adjacent block's face?
        |                     |    |   Rotation: if we place at this point, will our block's orientation be correct?
        | X        X        X |    +   Other states: other miscellaneous states may be tested to see if we are placing correctly.
        |                     |   /
        |                     |  /     When the point is passed as valid, this is the spot where the player's "placement" happens.
        | X        X        X | /
        |                     |/   X = an example of a point that the printer will check
        +---------------------+

        Dino Printer also supports airplace. When it does as such,
           it does the same process explained above, but disregards adjacent blocks and insteads runs the point testing at its own block position using a custom box shape.

    **/
    private class BlockPrint {
        public final BlockPos blockPos;
        public final BlockState required;
        public final BlockHitResult hit;
        private float placeYaw;
        private float placePitch;
        private boolean useHackRotation = false;

        BlockPrint(BlockPos blockPos, BlockState required) {
            this.blockPos = blockPos;
            this.required = required;
            this.hit = calculateBestPlaceHit();
        }

        public boolean canPlace() {
            return hit != null;
        }

        // Should we do special rotation when placing this block?
        public boolean shouldRotatePlace() {
            return rotationPlace.get() && isRotatable();
        }

        // Determine if our required blockstate is a rotatable block with directionality.
        private boolean isRotatable() {
            return required.contains(Properties.FACING) || 
                   required.contains(Properties.HORIZONTAL_FACING) ||
                   required.contains(Properties.HORIZONTAL_AXIS) ||
                   required.contains(Properties.AXIS) ||
                   required.contains(Properties.HOPPER_FACING) ||
                   required.contains(Properties.ORIENTATION) ||
                   required.contains(Properties.VERTICAL_DIRECTION);
        }

        // Yaw when placing the block
        public double getYaw() {
            if (useHackRotation) return placeYaw;

            return Rotations.getYaw(hit.getPos());
        }

        // Pitch when placing the block
        public double getPitch() {
            if (useHackRotation) return placePitch;

            return Rotations.getPitch(hit.getPos());
        }

        // Gets a simulated place state using customizable inputs.
        // The simulated place state is effectively the blockState when it is actually placed, allowing us to determine what it will look like before it is placed.
        // This can be used to compare certain attributes such as the facing direction.
        private BlockState getSimulatedPlaceState(float yaw, float pitch, BlockHitResult blockHit) {
            float oldYaw = mc.player.getYaw();
            float oldPitch = mc.player.getPitch();

            // Set rotation for proper calculation
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);

            Block block = required.getBlock();
            ItemStack stack = block.asItem().getDefaultStack();
            ItemPlacementContext context = new ItemPlacementContext(mc.player, Hand.MAIN_HAND, stack, blockHit);
            BlockState placeState = block.getPlacementState(context);

            // Return rotation back to normal
            mc.player.setYaw(oldYaw);
            mc.player.setPitch(oldPitch);

            return placeState;
        }

        // Gives the best possible hit result using relevant requirements
        private BlockHitResult calculateBestPlaceHit() {
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = blockPos.offset(direction);
                BlockState adjacentState = mc.world.getBlockState(adjacent);

                // Check spots on other blocks to place onto
                if (!adjacentState.isReplaceable()) {
                    Set<Vec3d> points = getShapeFacePoints(adjacent, direction.getOpposite());
                    for (Vec3d point : points) {
                        if (!isPointValid(point, blockPos, direction)) continue;

                        BlockHitResult placeHit = new BlockHitResult(point, direction.getOpposite(), adjacent, false);

                        if (!isMatchingPropertiesFromHit(placeHit)) continue;

                        if (!isValidRotationHit(placeHit)) continue;

                        // placeHit passed all requirements
                        return placeHit;
                    }
                }

                // If we couldn't find a block to place onto for this direction,
                // Check points on our own block position for an air place
                if (airPlace.get()) {
                    Set<Vec3d> airPoints = getShapeFacePoints(blockPos, direction);
                    for (Vec3d point : airPoints) {
                        if (!isPointValid(point, blockPos, direction)) continue;

                        BlockHitResult airPlaceHit = new BlockHitResult(point, direction, blockPos, false);

                        if (!isMatchingPropertiesFromHit(airPlaceHit)) continue;

                        if (!isValidRotationHit(airPlaceHit)) continue;

                        // airPlaceHit passed all requirements
                        return airPlaceHit;
                    }
                }
            }

            // We failed to find any good spot to place the block
            return null;
        }

        // Check if the BlockHitResult has the same properties
        private boolean isMatchingPropertiesFromHit(BlockHitResult blockHit) {
            float yaw = (float) Rotations.getYaw(blockHit.getPos());
            float pitch = (float) Rotations.getPitch(blockHit.getPos());
            BlockState simulated = getSimulatedPlaceState(yaw, pitch, blockHit);
            if (simulated == null) return false;

            // Block Half - slabs and stairs etc.
            if (halfBlocks.get()) {
                if (required.contains(Properties.SLAB_TYPE) && simulated.contains(Properties.SLAB_TYPE)) {
                    if (required.get(Properties.SLAB_TYPE) != simulated.get(Properties.SLAB_TYPE)) return false;
                } else if (required.contains(Properties.BLOCK_HALF) && simulated.contains(Properties.BLOCK_HALF)) {
                    if (required.get(Properties.BLOCK_HALF) != simulated.get(Properties.BLOCK_HALF)) return false;
                }
            }

            if (miscStates.get()) {
                // Door Hinge - doors
                if (required.contains(Properties.DOOR_HINGE) && simulated.contains(Properties.DOOR_HINGE)) {
                    if (required.get(Properties.DOOR_HINGE) != simulated.get(Properties.DOOR_HINGE)) return false;
                }

                // Block Face - levers
                if (required.contains(Properties.BLOCK_FACE) && simulated.contains(Properties.BLOCK_FACE)) {
                    if (required.get(Properties.BLOCK_FACE) != simulated.get(Properties.BLOCK_FACE)) return false;
                }

                // Attachment - hanging signs
                if (required.contains(Properties.ATTACHMENT) && simulated.contains(Properties.ATTACHMENT)) {
                    if (required.get(Properties.ATTACHMENT) != simulated.get(Properties.ATTACHMENT)) return false;
                }
            }

            return true;
        }

        // Check if the BlockHitResult has the correct rotation
        private boolean isMatchingFacingFromHit(float yaw, float pitch, BlockHitResult blockHit) {
            BlockState simulated = getSimulatedPlaceState(yaw, pitch, blockHit);
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
            }

            return true;
        }

        // Calculate rotation from a hit. Also calculates necessary Yaw and Pitch the player should use when placing the block.
        private boolean isValidRotationHit(BlockHitResult blockHit) {
            if (!rotationPlace.get()) return true;

            // Strict rotation calculation
            float legitYaw = (float) Rotations.getYaw(blockHit.getPos());
            float legitPitch = (float) Rotations.getPitch(blockHit.getPos());
            if (isMatchingFacingFromHit(legitYaw, legitPitch, blockHit)) return true;

            // Hack rotation calculation
            if (!strictRotation.get()) {
                for (Direction rotateDirection : Direction.values()) {
                    float rotateYaw = getHackYaw(legitYaw, rotateDirection);
                    float rotatePitch = getHackPitch(legitPitch, rotateDirection);
                    if (isMatchingFacingFromHit(rotateYaw, rotatePitch, blockHit)) {
                        placeYaw = rotateYaw;
                        placePitch = rotatePitch;
                        useHackRotation = true;
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isPointValid(Vec3d point, BlockPos adjacent, Direction direction) {
            // Place point must be within range
            if (!PlayerUtils.isWithin(point, placeRange.get())) return false;

            // Must be visible if applicable
            if (!wallPlace.get() && !isPointVisible(point, adjacent, direction)) return false;

            return true;
        }

        // Determines if a player can see a point on a block's face
        private boolean isPointVisible(Vec3d point, BlockPos pos, Direction direction) {
            RaycastContext context = new RaycastContext(mc.player.getEyePos(), point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
            BlockHitResult hit = mc.world.raycast(context);

            return hit.getType() == HitResult.Type.MISS || (hit.getBlockPos().equals(pos) && hit.getSide() == direction);
        }

        // Gets a list of relevant vertices on a block shape's specific face
        private Set<Vec3d> getShapeFacePoints(BlockPos pos, Direction face) {
            Set<Vec3d> points = new LinkedHashSet<>();

            BlockState blockState = mc.world.getBlockState(pos);
            VoxelShape shape = blockState.isReplaceable() ? airShape : blockState.getOutlineShape(mc.world, pos);

            final double[] samples = {1.0 / 6.0, 0.5, 5.0 / 6.0};

            for (Box box : shape.getBoundingBoxes()) {
                double minX = box.minX + pos.getX();
                double minY = box.minY + pos.getY();
                double minZ = box.minZ + pos.getZ();
                double maxX = box.maxX + pos.getX();
                double maxY = box.maxY + pos.getY();
                double maxZ = box.maxZ + pos.getZ();

                for (double u : samples) {
                    for (double v : samples) {
                        double x  = MathHelper.lerp(u, minX, maxX);
                        double y  = MathHelper.lerp(u, minY, maxY);
                        double z  = MathHelper.lerp(u, minZ, maxZ);
                        double x2 = MathHelper.lerp(v, minX, maxX);
                        double y2 = MathHelper.lerp(v, minY, maxY);
                        double z2 = MathHelper.lerp(v, minZ, maxZ);

                        switch (face) {
                            case DOWN ->  points.add(new Vec3d(x, minY, z2));
                            case UP ->    points.add(new Vec3d(x, maxY, z2));
                            case NORTH -> points.add(new Vec3d(x, y2, minZ));
                            case SOUTH -> points.add(new Vec3d(x, y2, maxZ));
                            case WEST ->  points.add(new Vec3d(minX, y, z2));
                            case EAST ->  points.add(new Vec3d(maxX, y, z2));
                        }
                    }
                }
            }

            return points;
        }

        // Yaw to use when doing 'hack' rotation
        private float getHackYaw(float legitYaw, Direction direction) {
            if (direction == Direction.UP || direction == Direction.DOWN) {
                return legitYaw;
            }

            return direction.getPositiveHorizontalDegrees();
        }

        // Pitch to use when doing 'hack' rotation
        private float getHackPitch(float legitPitch, Direction direction) {
            float pitch = switch (direction) {
                case UP -> -90.0f;
                case DOWN -> 90.0f;
                default -> legitPitch;
            };
            return pitch;
        }
    }


    /// Rendering

    private static class PlacedFade {
        public int ticks;
        public final BlockPos pos;

        PlacedFade(int ticks, BlockPos pos) {
            this.ticks = ticks;
            this.pos = pos;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        placedFades.forEach(fade -> {
            Color fadedColor = new Color(color.get());
            int alpha = (int)(((float) fade.ticks / (float) fadeTime.get()) * color.get().a);
            fadedColor.a(alpha);
            event.renderer.box(fade.pos, fadedColor, null, ShapeMode.Sides, 0);
        });
    }


    /// Sorting

    @SuppressWarnings("unused")
    public enum SortAlgorithm {
        None(false, (a, b) -> 0),
        TopDown(true, Comparator.comparingInt(p -> -p.blockPos.getY())),
        DownTop(true, Comparator.comparingInt(p -> p.blockPos.getY())),
        Nearest(false, Comparator.comparingDouble(p -> distanceToPlayer(p.blockPos))),
        Furthest(false, Comparator.comparingDouble(p -> -distanceToPlayer(p.blockPos)));

        final boolean applySecondSorting;
        final Comparator<BlockPrint> algorithm;

        SortAlgorithm(boolean applySecondSorting, Comparator<BlockPrint> algorithm) {
            this.applySecondSorting = applySecondSorting;
            this.algorithm = algorithm;
        }
    }

    @SuppressWarnings("unused")
    public enum SortingSecond {
        None(SortAlgorithm.None.algorithm),
        Nearest(SortAlgorithm.Nearest.algorithm),
        Furthest(SortAlgorithm.Furthest.algorithm);

        final Comparator<BlockPrint> algorithm;

        SortingSecond(Comparator<BlockPrint> algorithm) {
            this.algorithm = algorithm;
        }
    }

    private static double distanceToPlayer(BlockPos pos) {
        if (MeteorClient.mc.player == null) return 0;

        return Utils.squaredDistance(
            MeteorClient.mc.player.getX(),
            MeteorClient.mc.player.getY(),
            MeteorClient.mc.player.getZ(),
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5
        );
    }
}
