/**
    RotationManager

    Forked from base meteor but rewritten heavily.
    It is designed for compliance with Grim anti-cheat primarily.

    Most notably, "move fix" is implemented so rotations dont cause rubberbanding while the player moves.
**/

package dinosaurwizard.dinoprinter.utils;

import dinosaurwizard.dinoprinter.modules.DinoPrinter;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.input.Input;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.PlayerInput;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RotationManager {
    private static final List<Rotation> queue = new ArrayList<>();
    private static final List<Rotation> rotations = new ArrayList<>();
    private static float preYaw, prePitch;
    public static float serverYaw, serverPitch;

    private static Rotation lastRotation;
    private static int lastRotationTimer;

    public static boolean rotating = false;

    private static DinoPrinter printer;

    private RotationManager() {
    }

    @PreInit
    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(RotationManager.class);
    }

    @PostInit
    public static void initPost() {
        printer = Modules.get().get(DinoPrinter.class);
    }

    public static void rotate(double yaw, double pitch, int priority, Runnable callback) {
        Rotation rotation = new Rotation(yaw, pitch, priority, callback);

        int i = 0;
        for (; i < queue.size(); i++) {
            if (priority > queue.get(i).priority) break;
        }

        queue.add(i, rotation);
    }

    public static void rotate(double yaw, double pitch, Runnable callback) {
        rotate(yaw, pitch, 0, callback);
    }

    public static void rotate(double yaw, double pitch, int priority) {
        rotate(yaw, pitch, priority, null);
    }

    public static void rotate(double yaw, double pitch) {
        rotate(yaw, pitch, 0, null);
    }

    private static void setPrePlayerRotation() {
        preYaw = mc.player.getYaw();
        prePitch = mc.player.getPitch();
    }

    private static void setPlayerRotation(float yaw, float pitch) {
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private static void setServerRotation(float yaw, float pitch) {
        serverYaw = yaw;
        serverPitch = pitch;
    }

    @EventHandler(priority = EventPriority.HIGHEST + 100)
    private static void onTickRotations(TickEvent.Pre event) {
        if (rotations.isEmpty()) return;

        setPrePlayerRotation();

        // First rotation packet was already sent to server last tick naturally
        Rotation first = rotations.get(0);
        setPlayerRotation(first.yaw, first.pitch);
        first.runCallback();

        // All subsequent rotations require a packet sent
        for (int i = 1; i < rotations.size(); i++) {
            Rotation rotation = rotations.get(i);
            setServerRotation(rotation.yaw, rotation.pitch);
            setPlayerRotation(rotation.yaw, rotation.pitch);
            rotation.sendPacket();
            rotation.runCallback();
        }

        setPlayerRotation(preYaw, prePitch);

        Rotation lastIndexRotation = rotations.get(rotations.size() - 1);
        if (lastIndexRotation != lastRotation) {
            lastRotation = lastIndexRotation;
            lastRotation.callback = null;
            lastRotationTimer = 0;
            rotating = Config.get().rotationHoldTicks.get() > 0;
        }

        rotations.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST - 1000)
    private static void onTickQueue(TickEvent.Pre event) {
        if (printer != null && printer.rotateLegitMove.get()) {
            startRotations();
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    private static void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
        if (printer != null && !printer.rotateLegitMove.get()) {
            startRotations();
        }

        if (rotations.isEmpty()) return;

        // The first rotation is sent within our normal movement packets
        Rotation first = rotations.get(0);
        setPrePlayerRotation();
        setServerRotation(first.yaw, first.pitch);
        setPlayerRotation(first.yaw, first.pitch);
    }

    @EventHandler(priority = EventPriority.LOW)
    private static void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
        if (rotations.isEmpty()) return;

        if (Rotations.rotating) return; // Compatibility with base meteor

        setPlayerRotation(preYaw, prePitch);
    }

    private static void startRotations() {
        if (printer.isActive()) {
            printer.onTickPreRotate();
        }

        rotating = false;

        if (lastRotation != null) {
            if (lastRotationTimer++ >= Config.get().rotationHoldTicks.get()) {
                lastRotation = null;
                lastRotationTimer = 0;
            } else if (queue.isEmpty()) {
                queue.add(lastRotation);
            }
        }

        if (!queue.isEmpty()) {
            rotating = true;

            for (Rotation rotation : queue) {
                setServerRotation(rotation.yaw, rotation.pitch);
                rotations.add(rotation);
            }

            queue.clear();
        }
    }

    public static boolean shouldAlterMovementInputs() {
        return printer != null && rotating && printer.rotateLegitMove.get();
    }

    public static double getYaw(Vec3d pos) {
        return mc.player.getYaw() + MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(pos.getZ() - mc.player.getZ(), pos.getX() - mc.player.getX())) - 90f - mc.player.getYaw());
    }

    public static double getPitch(Vec3d pos) {
        double diffX = pos.getX() - mc.player.getX();
        double diffY = pos.getY() - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double diffZ = pos.getZ() - mc.player.getZ();
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        return mc.player.getPitch() + MathHelper.wrapDegrees((float) -Math.toDegrees(Math.atan2(diffY, diffXZ)) - mc.player.getPitch());
    }

    /**
    * setRotationPlayerInput & getInputFromRelativeYaw are derived from Lambda Client.
    * @see https://github.com/lambda-client/lambda/blob/1.21.11/src/main/kotlin/com/lambda/util/player/RotationUtils.kt
    *
    * @author dinosaurwizard
    * @date 2026-10-10
    **/

    public static void setRotationPlayerInput(Input input) {
        input.playerInput = getInputFromRelativeYaw(input.playerInput);

        int x = (input.playerInput.left() ? 1 : 0) - (input.playerInput.right() ? 1 : 0);
        int y = (input.playerInput.forward() ? 1 : 0) - (input.playerInput.backward() ? 1 : 0);
        input.movementSideways = x;
        input.movementForward = y;
    }

    private static PlayerInput getInputFromRelativeYaw(PlayerInput input) {
        int strafe = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
        int forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        if (strafe == 0 && forward == 0) return input;

        double deltaYawRad = Math.toRadians(mc.player.getYaw() - serverYaw);
        double cos = Math.cos(deltaYawRad);
        double sin = Math.sin(deltaYawRad);
        double newStrafe = strafe * cos - forward * sin;
        double newForward = strafe * sin + forward * cos;
        double angle = Math.atan2(newStrafe, newForward);
        double sector = Math.PI / 4.0;
        double boundary = Math.PI / 8.0;

        boolean pressForward = false;
        boolean pressBackward = false;
        boolean pressLeft = false;
        boolean pressRight = false;

        if (angle > -boundary && angle <= boundary) {
            pressForward = true;
        } else if (angle > boundary && angle <= boundary + sector) {
            pressForward = true;
            pressLeft = true;
        } else if (angle > boundary + sector && angle <= boundary + 2 * sector) {
            pressLeft = true;
        } else if (angle > boundary + 2 * sector && angle <= boundary + 3 * sector) {
            pressBackward = true;
            pressLeft = true;
        } else if (angle > boundary + 3 * sector || angle <= -(boundary + 3 * sector)) {
            pressBackward = true;
        } else if (angle > -(boundary + 3 * sector) && angle <= -(boundary + 2 * sector)) {
            pressBackward = true;
            pressRight = true;
        } else if (angle > -(boundary + 2 * sector) && angle <= -(boundary + sector)) {
            pressRight = true;
        } else if (angle > -(boundary + sector) && angle <= -boundary) {
            pressForward = true;
            pressRight = true;
        }

        return new PlayerInput(pressForward, pressBackward, pressLeft, pressRight, input.jump(), input.sneak(), input.sprint());
    }

    private static class Rotation {
        public float yaw, pitch;
        public int priority;
        public Runnable callback;

        public Rotation(double yaw, double pitch, int priority, Runnable callback) {
            this.yaw = (float) yaw;
            this.pitch = (float) pitch;
            this.priority = priority;
            this.callback = callback;
        }

        public void sendPacket() {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        }

        public void runCallback() {
            if (callback != null) callback.run();
        }
    }
}
