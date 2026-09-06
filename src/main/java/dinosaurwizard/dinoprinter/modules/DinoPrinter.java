/**

  Dino Printer by dinosaurwizard

**/

package dinosaurwizard.dinoprinter.modules;

import dinosaurwizard.dinoprinter.utils.BlockPrint;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
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
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.PlayerInput;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class DinoPrinter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");
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

    public final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
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

    public final Setting<Boolean> wallPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("wall-place")
        .description("Allow placement through walls. Turn off to use raytracing.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Allow placement in the air.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> sneakPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("sneak-place")
        .description("Sends sneaking packets when placing blocks.")
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

    // Advanced

    public final Setting<Boolean> rotationPlace = sgAdvanced.add(new BoolSetting.Builder()
        .name("rotation-place")
        .description("Respect block rotation.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> strictRotation = sgAdvanced.add(new BoolSetting.Builder()
        .name("strict-rotation")
        .description("Determines if rotatable blocks can only be placed legitimently.")
        .defaultValue(false)
        .visible(rotationPlace::get)
        .build()
    );

    public final Setting<Boolean> halfBlocks = sgAdvanced.add(new BoolSetting.Builder()
        .name("half-blocks")
        .description("Respect block half. Necessary for properly placing slabs, stairs and trapdoors.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> incrementalStates = sgAdvanced.add(new BoolSetting.Builder()
        .name("incremental-states")
        .description("Respect states that need multiple placements. Necessary for double-slabs, candles, vines, and snow layers.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> miscStates = sgAdvanced.add(new BoolSetting.Builder()
        .name("misc-states")
        .description("Respect miscellaneous states. Blocks like doors, beds, and lanterns are affected. ")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> exitSigns = sgAdvanced.add(new BoolSetting.Builder()
        .name("exit-signs")
        .description("Auto exit sign screens.")
        .defaultValue(true)
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

    private final Setting<Integer> inventoryMoveDelay = sgInventory.add(new IntSetting.Builder()
        .name("inventory-move-delay")
        .description("The tick delay that your swap slot will be locked when moving items into your hotbar.")
        .defaultValue(4)
        .range(0, 10)
        .visible(() -> autoSwitch.get() && allowInventory.get())
        .build()
    );

    private final Setting<Boolean> stationaryMove = sgInventory.add(new BoolSetting.Builder()
        .name("stationary-move")
        .description("Only allows blocks to be moved from your inventory if you are standing still. Required for certain anti-cheats.")
        .defaultValue(false)
        .visible(() -> autoSwitch.get() && allowInventory.get())
        .build()
    );

    private final Setting<Boolean> hotbarPriority = sgInventory.add(new BoolSetting.Builder()
        .name("hotbar-priority")
        .description("Blocks in your hotbar will have placement priority over blocks inside your inventory.")
        .defaultValue(true)
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
        .defaultValue(8)
        .min(0)
        .sliderMax(20)
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
    private int inventoryTimer;
    private Item pendingItem = null;
    private int pendingSlot = -1;
    public final Set<BlockPos> cachedPositions = new ObjectOpenHashSet<>();
    public final Set<BlockPos> rotatePositions = new ObjectOpenHashSet<>();
    private final List<BlockPrint> blockPrints = new ArrayList<>();
    private final List<PlacedFade> placedFades = new ArrayList<>();
    private long lastSignPlaceTime = 0;

    public DinoPrinter() {
        super(Categories.World, "dino-printer", "Prints rendered litematica schematics.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        placeTimer = 0;
        cacheTimer = 0;
        inventoryTimer = 0;
        pendingItem = null;
        pendingSlot = -1;
        cachedPositions.clear();
        rotatePositions.clear();
        blockPrints.clear();
        placedFades.clear();
        lastSignPlaceTime = 0;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
        if (worldSchematic == null) {
            toggle();
            return;
        }

        if (shouldPause()) return;

        // Unlock the pending slot when the time comes
        if (pendingSlot != -1 && pendingItem == null && inventoryTimer++ >= inventoryMoveDelay.get()) {
            pendingSlot = -1;
            inventoryTimer = 0;
        }

        // Clear cached positions every so often
        if (!cachedPositions.isEmpty() && cacheTimer++ >= placeRetryDelay.get()) {
            cachedPositions.clear();
            cacheTimer = 0;
        }

        // Delay
        if (placeTimer++ < placeDelay.get()) return;

        // Find print locations
        BlockIterator.register((int) Math.ceil(placeRange.get()), (int) Math.ceil(placeRange.get()), (blockPos, existing) -> {
            BlockState required = worldSchematic.getBlockState(blockPos);

            BlockPrint blockPrint = new BlockPrint(blockPos, required, existing, this);
            if (!blockPrint.canPlace()) return;

            blockPrints.add(blockPrint);
        });
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (blockPrints.isEmpty()) return;

        // Sort blocks
        sortBlockPrints();

        // Place blocks!
        int placedCount = 0;
        for (BlockPrint blockPrint : blockPrints) {
            if (placedCount >= blocksPerTick.get()) break;

            Item item = blockPrint.required.getBlock().asItem();
            FindItemResult result = findPrintableItem(item);
            if (!result.found()) continue;

            // Move items into hotbar if allowed
            if (!result.isHotbar() && canInventoryMove()) {
                pendingItem = item;
                pendingSlot = getSwapSlotToUse();
                blockPrints.clear();
                return;
            }

            if (!result.isHotbar()) continue;

            if (!autoSwitch.get() && mc.player.getInventory().getSelectedSlot() != result.slot()) continue;

            if (exitSigns.get() && blockPrint.required.getBlock() instanceof AbstractSignBlock) {
                lastSignPlaceTime = System.currentTimeMillis();
            }

            place(blockPrint, result);

            if (render.get()) {
                placedFades.add(new PlacedFade((float) fadeTime.get(), blockPrint.blockPos));
            }

            cachedPositions.add(blockPrint.blockPos);
            placedCount++;
        }

        blockPrints.clear();
        placeTimer = 0;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
        // Move items into our hotbar AFTER rotations occur
        if (pendingItem == null) return;

        if (canInventoryMove()) {
            FindItemResult result = InvUtils.find(itemStack -> pendingItem == itemStack.getItem());
            if (result.found() && !result.isHotbar()) {
                InvUtils.quickSwap().fromId(pendingSlot).to(result.slot());
            }
        }
        pendingItem = null;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        // Cancel sign screens when applicable
        if (!exitSigns.get() || !(event.screen instanceof AbstractSignEditScreen)) return;
        if (System.currentTimeMillis() - lastSignPlaceTime > 500) return;

        event.setCancelled(true);
    }

    private boolean canInventoryMove() {
        if (!autoSwitch.get() || !allowInventory.get()) return false;

        if (stationaryMove.get() && mc.player.getVelocity().multiply(1, 0, 1).length() > 0.00001) return false;

        return true;
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

    // Find the slot of an item we can print with
    private FindItemResult findPrintableItem(Item item) {
        // Dont allow inventory if we are pending
        int end = pendingSlot != -1 ? 8 : mc.player.getInventory().size();
        for (int i = 0; i <= end; i++) {
            // Slots we are moving items into are locked
            if (i == pendingSlot) continue; 

            ItemStack stack = mc.player.getInventory().getStack(i);
            if (item != stack.getItem()) continue;

            return new FindItemResult(i, 0);
        }

        return new FindItemResult(-1, 0);
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
            rotatePositions.add(blockPrint.blockPos);
            Rotations.rotate(blockPrint.getYaw(), blockPrint.getPitch(), () -> {
                rotatePositions.clear();
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


    /// Rendering

    private static class PlacedFade {
        public float ticks;
        public final BlockPos pos;

        PlacedFade(float ticks, BlockPos pos) {
            this.ticks = ticks;
            this.pos = pos;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        placedFades.removeIf(fade -> {
            fade.ticks -= event.tickDelta;
            if (fade.ticks <= 0) return true;

            Color fadedColor = new Color(color.get());
            int alpha = (int) ((fade.ticks / fadeTime.get()) * color.get().a);
            fadedColor.a(alpha);
            event.renderer.box(fade.pos, fadedColor, null, ShapeMode.Sides, 0);
            return false;
        });
    }


    /// Sorting

    private void sortBlockPrints() {
        Comparator<BlockPrint> comparator = firstAlgorithm.get().algorithm;

        if (firstAlgorithm.get().applySecondSorting && secondAlgorithm.get() != SortingSecond.None) {
            comparator = secondAlgorithm.get().algorithm.thenComparing(comparator);
        }

        if (autoSwitch.get() && allowInventory.get() && hotbarPriority.get()) {
            comparator = hotbarAlgorithm.thenComparing(comparator);
        }

        blockPrints.sort(comparator);
    }

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

        return MeteorClient.mc.player.getEntityPos().squaredDistanceTo(pos.toCenterPos());
    }

    private static final Comparator<BlockPrint> hotbarAlgorithm = (a, b) -> {
        Item itemA = a.required.getBlock().asItem();
        Item itemB = b.required.getBlock().asItem();

        boolean aInHotbar = InvUtils.findInHotbar(itemStack -> itemA == itemStack.getItem()).found();
        boolean bInHotbar = InvUtils.findInHotbar(itemStack -> itemB == itemStack.getItem()).found();

        return Boolean.compare(bInHotbar, aInHotbar);
    };
}
