package net.honormc.backstage.client.editor.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.joml.Matrix4fc;

public record OverlayFrame(RenderTarget target, Matrix4fc projection, Matrix4fc view, double cameraX, double cameraY,
                           double cameraZ, float time) {
}
