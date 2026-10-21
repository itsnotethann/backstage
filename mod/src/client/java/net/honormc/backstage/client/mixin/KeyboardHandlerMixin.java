package net.honormc.backstage.client.mixin;

import net.honormc.backstage.client.editor.EditorMode;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void backstage$key(long handle, int action, KeyEvent event, CallbackInfo ci) {
        EditorMode mode = EditorMode.get();
        if (mode != null && mode.key(event.key(), event.scancode(), action, event.modifiers())) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void backstage$character(long handle, CharacterEvent event, CallbackInfo ci) {
        EditorMode mode = EditorMode.get();
        if (mode != null && mode.character(event.codepoint())) {
            ci.cancel();
        }
    }
}
