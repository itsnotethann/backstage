package net.honormc.backstage.protocol.model;

import java.util.List;
import java.util.Objects;

public sealed interface Prop {
    record Str(String value) implements Prop {
        public Str {
            Objects.requireNonNull(value);
        }
    }

    record Num(double value) implements Prop {
    }

    record Bool(boolean value) implements Prop {
    }

    record BlockProp(String state) implements Prop {
        public BlockProp {
            Objects.requireNonNull(state);
        }
    }

    record ItemProp(String snbt) implements Prop {
        public ItemProp {
            Objects.requireNonNull(snbt);
        }
    }

    record ListProp(List<Prop> values) implements Prop {
        public ListProp {
            values = List.copyOf(values);
        }
    }
}
