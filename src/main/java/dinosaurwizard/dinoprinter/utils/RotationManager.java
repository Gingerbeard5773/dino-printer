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
import net.minecraft.client.player.ClientInput;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
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
        preYaw = mc.player.getYRot();
        prePitch = mc.player.getXRot();
    }

    private static void setPlayerRotation(float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
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

    public static double getYaw(Vec3 pos) {
        return mc.player.getYRot() + Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(pos.z() - mc.player.getZ(), pos.x() - mc.player.getX())) - 90f - mc.player.getYRot());
    }

    public static double getPitch(Vec3 pos) {
        double diffX = pos.x() - mc.player.getX();
        double diffY = pos.y() - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double diffZ = pos.z() - mc.player.getZ();
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        return mc.player.getXRot() + Mth.wrapDegrees((float) -Math.toDegrees(Math.atan2(diffY, diffXZ)) - mc.player.getXRot());
    }

    /**
    * setRotationPlayerInput & getInputFromRelativeYaw are derived from Lambda Client.
    * @see https://github.com/lambda-client/lambda/blob/1.21.11/src/main/kotlin/com/lambda/util/player/RotationUtils.kt
    *
    * @author dinosaurwizard
    * @date 2026-10-10
    **/

    public static void setRotationPlayerInput(ClientInput input) {
        input.keyPresses = getInputFromRelativeYaw(input.keyPresses);

        int x = (input.keyPresses.left() ? 1 : 0) - (input.keyPresses.right() ? 1 : 0);
        int y = (input.keyPresses.forward() ? 1 : 0) - (input.keyPresses.backward() ? 1 : 0);
        input.moveVector = new Vec2(x, y).normalized();
    }

    private static Input getInputFromRelativeYaw(Input input) {
        int strafe = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
        int forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        if (strafe == 0 && forward == 0) return input;

        double deltaYawRad = Math.toRadians(mc.player.getYRot() - serverYaw);
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

        return new Input(pressForward, pressBackward, pressLeft, pressRight, input.jump(), input.shift(), input.sprint());
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
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
        }

        public void runCallback() {
            if (callback != null) callback.run();
        }
    }
}
