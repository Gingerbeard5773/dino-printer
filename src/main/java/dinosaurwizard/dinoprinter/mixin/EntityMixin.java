package dinosaurwizard.dinoprinter.mixin;

import dinosaurwizard.dinoprinter.utils.RotationManager;
import net.minecraft.world.entity.Entity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @WrapOperation(method = "moveRelative", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
    public float moveRelativeYaw(Entity entity, Operation<Float> original) {
        if ((Object) this == mc.player && RotationManager.shouldAlterMovementInputs()) {
            return RotationManager.serverYaw;
        }

        return original.call(entity);
    }
}
