package net.honormc.backstage.client.mixin;

import net.honormc.backstage.client.editor.EditorMode;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void backstage$button(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
        EditorMode mode = EditorMode.get();
        if (mode != null && mode.mouseButton(info.button(), action, info.modifiers())) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void backstage$scroll(long handle, double x, double y, CallbackInfo ci) {
        EditorMode mode = EditorMode.get();
        if (mode != null && mode.scroll(x, y)) {
            ci.cancel();
        }
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void backstage$grab(CallbackInfo ci) {
        if (EditorMode.blocksGrab()) {
            ci.cancel();
        }
    }
}
