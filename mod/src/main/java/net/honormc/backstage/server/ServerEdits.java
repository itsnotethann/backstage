package net.honormc.backstage.server;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.honormc.backstage.Backstage;
import net.honormc.backstage.net.EditChunkPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ServerEdits {
    private static final int MAX_HISTORY = 25;

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private static final Map<UUID, Deque<Step>> HISTORY = new HashMap<>();
    private static final Map<UUID, Step> OPEN = new HashMap<>();

    private ServerEdits() {}

    public static void apply(ServerPlayer player, EditChunkPayload chunk) {
        if (!mayBuild(player)) {
            player.sendSystemMessage(Component.literal("§cBackstage: you need creative mode and operator permissions."));
            return;
        }
        ServerLevel level = player.level();
        UUID id = player.getUUID();

        Step step = OPEN.get(id);
        if (step == null || step.op != chunk.op() || step.level != level) {
            step = new Step(chunk.op(), level);
            OPEN.put(id, step);
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < chunk.positions().length; i++) {
            pos.set(chunk.positions()[i]);
            if (!level.isInsideBuildHeight(pos.getY()) || !level.isLoaded(pos)) {
                step.skipped++;
                continue;
            }
            BlockState next = Block.stateById(chunk.states()[i]);
            BlockState prev = level.getBlockState(pos);
            if (prev == next) {
                continue;
            }
            step.record(pos.asLong(), prev);
            level.setBlock(pos, next, FLAGS);
        }

        if (chunk.last()) {
            OPEN.remove(id);
            if (step.size() > 0) {
                Deque<Step> history = HISTORY.computeIfAbsent(id, k -> new ArrayDeque<>());
                history.push(step);
                while (history.size() > MAX_HISTORY) {
                    history.removeLast();
                }
            }
            String skipped = step.skipped > 0 ? " §7(" + step.skipped + " outside loaded area)" : "";
            player.sendSystemMessage(Component.literal("§aBackstage: changed " + step.size() + " blocks." + skipped), true);
            Backstage.LOG.info("{} applied Backstage op {} ({} blocks)", player.getName().getString(), step.op, step.size());
        }
    }

    public static void undo(ServerPlayer player) {
        if (!mayBuild(player)) {
            player.sendSystemMessage(Component.literal("§cBackstage: you need creative mode and operator permissions."));
            return;
        }
        Deque<Step> history = HISTORY.get(player.getUUID());
        Step step = history == null ? null : history.poll();
        if (step == null) {
            player.sendSystemMessage(Component.literal("§eBackstage: nothing to undo."), true);
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = step.size() - 1; i >= 0; i--) {
            pos.set(step.positions[i]);
            if (step.level.isLoaded(pos)) {
                step.level.setBlock(pos, step.previous[i], FLAGS);
            }
        }
        player.sendSystemMessage(Component.literal("§aBackstage: undid " + step.size() + " blocks. "
                + history.size() + " more undo steps."), true);
    }

    public static void forget(ServerPlayer player) {
        OPEN.remove(player.getUUID());
    }

    public static void clear() {
        HISTORY.clear();
        OPEN.clear();
    }

    private static boolean mayBuild(ServerPlayer player) {
        if (!player.isCreative()) {
            return false;
        }
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                || player.level().getServer().isSingleplayerOwner(player.nameAndId());
    }

    private static final class Step {
        final int op;
        final ServerLevel level;
        long[] positions = new long[256];
        BlockState[] previous = new BlockState[256];
        int count;
        int skipped;

        Step(int op, ServerLevel level) {
            this.op = op;
            this.level = level;
        }

        void record(long pos, BlockState prev) {
            if (count == positions.length) {
                positions = java.util.Arrays.copyOf(positions, count * 2);
                previous = java.util.Arrays.copyOf(previous, count * 2);
            }
            positions[count] = pos;
            previous[count] = prev;
            count++;
        }

        int size() {
            return count;
        }
    }
}
