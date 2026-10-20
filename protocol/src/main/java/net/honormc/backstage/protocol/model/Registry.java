package net.honormc.backstage.protocol.model;

import java.util.List;

public record Registry(List<MarkerType> types, List<KindDef> kinds, List<ListDef> lists) {
    public Registry {
        types = List.copyOf(types);
        kinds = List.copyOf(kinds);
        lists = List.copyOf(lists);
    }
}
