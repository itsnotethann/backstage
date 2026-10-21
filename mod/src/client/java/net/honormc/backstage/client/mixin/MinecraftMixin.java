package net.honormc.backstage.client.mixin;

import net.honormc.backstage.client.editor.EditorMode;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V", shift = At.Shift.AFTER))
    private void backstage$frame(boolean advanceGameTime, CallbackInfo ci) {
        EditorMode mode = EditorMode.get();
        if (mode != null) {
            mode.frameRendered();
        }
    }
}
