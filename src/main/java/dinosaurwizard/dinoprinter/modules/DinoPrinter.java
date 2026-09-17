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
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
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
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
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
        .sliderMax(20)
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
        .description("Respect miscellaneous states. Blocks like doors, beds, and lanterns are affected.")
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

    private final Setting<Integer> slotLockTicks = sgInventory.add(new IntSetting.Builder()
        .name("slot-lock-ticks")
        .description("The time in ticks to lock a slot that was used for swapping items.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .visible(() -> autoSwitch.get() && allowInventory.get())
        .build()
    );

    private final Setting<Integer> antiOverrideTicks = sgInventory.add(new IntSetting.Builder()
        .name("anti-override-ticks")
        .description("The time in ticks to stop S2C sync packets overriding your inventory slots, for the purpose of stopping misplaces.")
        .defaultValue(8)
        .min(0)
        .sliderMax(20)
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

    private int placeTimer = 0;
    private int cacheTimer = 0;
    private int pendingInventorySlot = -1;
    private int pendingHotbarSlot = -1;
    private long lastSignPlaceTime = 0;
    public final Set<BlockPos> cachedPositions = new ObjectOpenHashSet<>();
    private final List<BlockPrint> blockPrints = new ArrayList<>();
    private final List<PlacedFade> placedFades = new ArrayList<>();
    private final List<ItemStack> mainInventory = new ArrayList<>(SlotUtils.MAIN_END + 1);
    private final List<PrintSlot> printSlots = createPrintSlots();

    private List<PrintSlot> createPrintSlots() {
        List<PrintSlot> slots = new ArrayList<>(SlotUtils.MAIN_END + 1);
        for (int i = 0; i <= SlotUtils.MAIN_END; i++) {
            slots.add(new PrintSlot(i));
        }
        return slots;
    }

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
        pendingInventorySlot = -1;
        pendingHotbarSlot = -1;
        lastSignPlaceTime = 0;
        cachedPositions.clear();
        blockPrints.clear();
        placedFades.clear();
        mainInventory.clear();

        for (PrintSlot slot : printSlots) {
            slot.expire();
        }
    }

    // onSendMovementPacketsPre is used instead of onTick for max synchronization with player rotations
    @EventHandler(priority = EventPriority.HIGH)
    private void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
        if (worldSchematic == null) {
            toggle();
            return;
        }

        // Tick slot timers
        for (PrintSlot slot : printSlots) {
            slot.lockTimer = Math.min(slotLockTicks.get(), slot.lockTimer + 1);
            slot.syncTimer = Math.min(antiOverrideTicks.get(), slot.syncTimer + 1);
        }

        if (shouldPause()) return;

        // Clear cached positions every so often
        if (!cachedPositions.isEmpty() && cacheTimer++ >= placeRetryDelay.get()) {
            cachedPositions.clear();
            cacheTimer = 0;
        }

        // Delay
        if (placeTimer++ < placeDelay.get()) return;

        // Find print locations
        int px = mc.player.getBlockX(), py = mc.player.getBlockY(), pz = mc.player.getBlockZ();
        int radius = (int) Math.ceil(placeRange.get());
        int minY = Math.max(mc.world.getBottomY(), py - radius);
        int maxY = Math.min(mc.world.getTopYInclusive(), py + radius);

        BlockPos.Mutable blockPos = new BlockPos.Mutable();
        for (int x = px - radius; x <= px + radius; x++) {
            for (int z = pz - radius; z <= pz + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    int dx = x - px, dy = y - py, dz = z - pz;
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;

                    blockPos.set(x, y, z);
                    BlockState required = worldSchematic.getBlockState(blockPos);
                    BlockState existing = mc.world.getBlockState(blockPos);

                    BlockPrint blockPrint = new BlockPrint(blockPos, required, existing, this);
                    if (!blockPrint.canPlace()) continue;

                    blockPrints.add(blockPrint);
                }
            }
        }

        if (blockPrints.isEmpty()) return;

        // Sort blocks
        sortBlockPrints();

        // Setup inventory copy
        // We simulate an entire inventory due to desynchronization with rotations
        mainInventory.clear();
        for (int i = 0; i <= SlotUtils.MAIN_END; i++) {
            mainInventory.add(mc.player.getInventory().getStack(i).copy());
        }

        // Place blocks!
        int placedCount = 0;
        for (BlockPrint blockPrint : blockPrints) {
            if (placedCount >= blocksPerTick.get()) break;

            Item item = blockPrint.required.getBlock().asItem();
            int inventorySlot = getItemSlot(item);
            if (inventorySlot == -1) continue;

            int hotbarSlot = inventorySlot;

            // Move items into hotbar if allowed
            if (inventorySlot > SlotUtils.HOTBAR_END) {
                if (!canInventoryMove()) continue;

                hotbarSlot = getSwapSlot();
                if (hotbarSlot == -1) continue;

                printSlots.get(hotbarSlot).reset();
                printSlots.get(inventorySlot).reset();

                // Simulate swap for our simulated inventory
                ItemStack hotbarStack = mainInventory.get(hotbarSlot);
                ItemStack inventoryStack = mainInventory.get(inventorySlot);
                mainInventory.set(hotbarSlot, inventoryStack);
                mainInventory.set(inventorySlot, hotbarStack);

                // If locked on reset, exit out early.
                if (printSlots.get(hotbarSlot).isLocked()) {
                    pendingInventorySlot = inventorySlot;
                    pendingHotbarSlot = hotbarSlot;
                    blockPrints.clear();
                    return;
                }

                // Simulate count decrement for our simulated inventory
                inventoryStack.decrementUnlessCreative(1, mc.player);
            }

            if (!autoSwitch.get() && mc.player.getInventory().selectedSlot != hotbarSlot) continue;

            if (exitSigns.get() && blockPrint.required.getBlock() instanceof AbstractSignBlock) {
                lastSignPlaceTime = System.currentTimeMillis();
            }

            printSlots.get(hotbarSlot).syncTimer = 0;

            place(blockPrint, inventorySlot, hotbarSlot);

            cachedPositions.add(blockPrint.blockPos);
            placedCount++;
        }

        blockPrints.clear();
        placeTimer = 0;
    }

    // Post rotations
    @EventHandler(priority = EventPriority.LOW)
    private void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
        if (pendingInventorySlot != -1 && pendingHotbarSlot != -1) {
            InvUtils.quickSwap().fromId(pendingHotbarSlot).to(pendingInventorySlot);
            pendingInventorySlot = -1;
            pendingHotbarSlot = -1;
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        // Cancel sign screens when applicable
        if (!exitSigns.get() || !(event.screen instanceof AbstractSignEditScreen)) return;
        if (System.currentTimeMillis() - lastSignPlaceTime > 500) return;

        event.setCancelled(true);
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


    /// Inventory

    private class PrintSlot {
        public final int slot;
        public int lockTimer;
        public int syncTimer;

        PrintSlot(int slot) {
            this.slot = slot;
        }

        public void reset() {
            lockTimer = 0;
            syncTimer = 0;
        }

        public void expire() {
            lockTimer = slotLockTicks.get();
            syncTimer = antiOverrideTicks.get();
        }

        public boolean isLocked() {
            return lockTimer < slotLockTicks.get();
        }

        public boolean isCancelSync() {
            return syncTimer < antiOverrideTicks.get();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event)  {
        // Cancel incoming packets that cause misplaces
        // Misplaces are caused when the server resyncs the player but the player already changed their slots again
        if (event.packet instanceof ScreenHandlerSlotUpdateS2CPacket packet) {
            ScreenHandler handler = mc.player.currentScreenHandler;
            if (packet.getSyncId() != handler.syncId) return;

            int packetSlot = packet.getSlot();
            if (packetSlot < 0 || packetSlot >= handler.slots.size()) return;

            Slot screenSlot = handler.getSlot(packetSlot);
            if (screenSlot.inventory != mc.player.getInventory()) return;

            int slot = screenSlot.getIndex();
            if (slot < 0 || slot >= printSlots.size()) return;

            if (printSlots.get(slot).isCancelSync()) {
                event.cancel();
            }
        } else if (event.packet instanceof InventoryS2CPacket packet) {
            ScreenHandler handler = mc.player.currentScreenHandler;
            if (packet.getSyncId() != handler.syncId) return;

            List<ItemStack> contents = packet.getContents();
            for (PrintSlot printSlot : printSlots) {
                if (!printSlot.isCancelSync()) continue;

                int slot = handler.getSlotIndex(mc.player.getInventory(), printSlot.slot).orElse(-1);
                if (slot >= 0 && slot < contents.size()) {
                    contents.set(slot, mc.player.getInventory().getStack(printSlot.slot).copy());
                }
            }
        }
    }

    // Find the slot of an item we can print with
    private int getItemSlot(Item item) {
        for (int i = 0; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mainInventory.get(i);
            if (item != stack.getItem()) continue;

            // If we are already in the process of moving this item into the hotbar, dont bother with the same type
            if (isSlotLocked(i)) return -1;

            return i;
        }

        return -1;
    }

    // Find a good slot to swap items into
    private int getSwapSlot() {
        // First look for an empty slot in our inventory to use
        for (int i = 0; i < 9; i++) {
            // Slots we are moving items into are locked and cannot be used temporarily
            if (isSlotLocked(i)) continue;

            if (mainInventory.get(i).isEmpty()) {
                return i;
            }
        }

        // Otherwise just use our selected slot
        int selected = mc.player.getInventory().selectedSlot;
        if (!isSlotLocked(selected)) {
            return selected;
        }
        return -1;
    }

    private boolean isSlotLocked(int slot) {
        if (slot < 0 || slot >= printSlots.size()) return false;

        return printSlots.get(slot).isLocked();
    }

    private boolean canInventoryMove() {
        if (!autoSwitch.get() || !allowInventory.get()) return false;

        if (stationaryMove.get() && mc.player.getVelocity().multiply(1, 0, 1).length() > 0.00001) return false;

        return true;
    }


    /// Placement

    // Custom placement that is far better than meteor's standard
    private void place(BlockPrint blockPrint, int inventorySlot, int hotbarSlot) {
        if (rotate.get() || blockPrint.shouldRotatePlace()) {
            Rotations.rotate(blockPrint.getYaw(), blockPrint.getPitch(), () -> {
                interactPlace(blockPrint, inventorySlot, hotbarSlot);
            });
        } else {
            interactPlace(blockPrint, inventorySlot, hotbarSlot);
        }
    }

    private void interactPlace(BlockPrint blockPrint, int inventorySlot, int hotbarSlot) {
        // Send our inputs with sneaking injected
        boolean isSneaking = mc.player.isSneaking();
        PlayerInput old = mc.player.input.playerInput;
        if (sneakPlace.get() && !isSneaking) {
            mc.player.input.playerInput = new PlayerInput(old.forward(), old.backward(), old.left(), old.right(), old.jump(), true, old.sprint());
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
            mc.player.setSneaking(true);
        }

        if (autoSwitch.get()) {
            if (inventorySlot > SlotUtils.HOTBAR_END) {
                InvUtils.quickSwap().fromId(hotbarSlot).to(inventorySlot);
            }

            InvUtils.swap(hotbarSlot, swapBack.get());
        }

        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockPrint.hit).isAccepted()) {
            if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
            else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        if (autoSwitch.get() && swapBack.get()) InvUtils.swapBack();

        if (sneakPlace.get() && !isSneaking) {
            mc.player.input.playerInput = old;
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
            mc.player.setSneaking(isSneaking);
        }

        if (render.get()) {
            placedFades.add(new PlacedFade((float) fadeTime.get(), blockPrint.blockPos));
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

        return MeteorClient.mc.player.getPos().squaredDistanceTo(pos.toCenterPos());
    }

    private static final Comparator<BlockPrint> hotbarAlgorithm = (a, b) -> {
        Item itemA = a.required.getBlock().asItem();
        Item itemB = b.required.getBlock().asItem();
        boolean aInHotbar = InvUtils.findInHotbar(itemStack -> itemA == itemStack.getItem()).found();
        boolean bInHotbar = InvUtils.findInHotbar(itemStack -> itemB == itemStack.getItem()).found();

        return Boolean.compare(bInHotbar, aInHotbar);
    };
}
