package net.honormc.backstage.client.editor;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.honormc.backstage.protocol.EditorProtocol;

public final class GroupDraft {
    public enum Outcome {
        ADDED,
        PRESENT,
        FULL
    }

    private final LongLinkedOpenHashSet blocks = new LongLinkedOpenHashSet();
    private String id;

    public GroupDraft(String id, long[] blocks) {
        this.id = id;
        for (long block : blocks) {
            if (this.blocks.size() >= EditorProtocol.MAX_GROUP_BLOCKS) {
                break;
            }
            this.blocks.add(block);
        }
    }

    public String id() {
        return id;
    }

    public void id(String id) {
        this.id = id;
    }

    public Outcome add(long block) {
        if (blocks.contains(block)) {
            return Outcome.PRESENT;
        }
        if (blocks.size() >= EditorProtocol.MAX_GROUP_BLOCKS) {
            return Outcome.FULL;
        }
        blocks.add(block);
        return Outcome.ADDED;
    }

    public int addAll(long[] positions) {
        int added = 0;
        for (long block : positions) {
            Outcome outcome = add(block);
            if (outcome == Outcome.FULL) {
                break;
            }
            if (outcome == Outcome.ADDED) {
                added++;
            }
        }
        return added;
    }

    public boolean remove(long block) {
        return blocks.remove(block);
    }

    public boolean contains(long block) {
        return blocks.contains(block);
    }

    public boolean full() {
        return blocks.size() >= EditorProtocol.MAX_GROUP_BLOCKS;
    }

    public int size() {
        return blocks.size();
    }

    public long[] blocks() {
        return blocks.toLongArray();
    }

    public void reset(long[] positions) {
        blocks.clear();
        addAll(positions);
    }
}
