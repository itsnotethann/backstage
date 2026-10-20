package net.honormc.backstage.client.editor;

import com.mojang.math.Transformation;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class MarkerDisplays {
    private static final int GLOWING_FLAG = 6;
    private static final float VIEW_RANGE = 4.0f;

    private MarkerDisplays() {
    }

    static final class GlowingBlock extends Display.BlockDisplay {
        GlowingBlock(Level level, BlockState state, int colour) {
            super(EntityTypes.BLOCK_DISPLAY, level);
            setBlockState(state);
            setGlowColorOverride(colour);
            setBrightnessOverride(Brightness.FULL_BRIGHT);
            setViewRange(VIEW_RANGE);
            setSharedFlag(GLOWING_FLAG, true);
        }
    }

    static Entity block(Level level, BlockState state, int colour, double x, double y, double z, Quaternionf rotation,
                        Vector3f offset, Vector3f scale, float cull) {
        GlowingBlock display = new GlowingBlock(level, state, colour);
        Vector3f translation = rotation.transform(new Vector3f(offset));
        display.setTransformation(new Transformation(translation, new Quaternionf(rotation), scale, new Quaternionf()));
        display.setWidth(cull);
        display.setHeight(cull);
        display.snapTo(x, y, z, 0, 0);
        return display;
    }

    static Entity label(Level level, Component text, double x, double y, double z) {
        Display.TextDisplay display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
        display.setText(text);
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display.setBackgroundColor(0x80000000);
        display.setLineWidth(200);
        display.setBrightnessOverride(Brightness.FULL_BRIGHT);
        display.setViewRange(VIEW_RANGE);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6f), new Quaternionf()));
        display.setWidth(1);
        display.setHeight(1);
        display.snapTo(x, y, z, 0, 0);
        return display;
    }
}
