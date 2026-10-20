package net.honormc.backstage.client.lua;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.luaj.vm2.LuaError;

public final class BlockParser {
    private final HolderLookup<Block> lookup;
    private final ConcurrentHashMap<String, BlockState> cache = new ConcurrentHashMap<>();

    public BlockParser(HolderLookup<Block> lookup) {
        this.lookup = lookup;
    }

    public BlockState parse(String text) {
        BlockState cached = cache.get(text);
        if (cached != null) {
            return cached;
        }
        BlockState state;
        try {
            state = BlockStateParser.parseForBlock(lookup, text.trim(), false).blockState();
        } catch (Exception e) {
            throw new LuaError("bad block '" + text + "': " + e.getMessage());
        }
        cache.put(text, state);
        return state;
    }

    public static String serialize(BlockState state) {
        return BlockStateParser.serialize(state);
    }
}
