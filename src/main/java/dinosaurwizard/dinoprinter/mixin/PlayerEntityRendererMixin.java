package dinosaurwizard.dinoprinter.mixin;

import dinosaurwizard.dinoprinter.utils.RotationManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("RETURN"))
    private void updateRenderState$rotations(AbstractClientPlayerEntity player, PlayerEntityRenderState state, float f, CallbackInfo info) {
        if (RotationManager.rotating && player == mc.player) {
            state.relativeHeadYaw = 0;
            state.bodyYaw = RotationManager.serverYaw;
            state.pitch = RotationManager.serverPitch;
        }
    }
}
