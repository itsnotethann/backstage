package net.honormc.backstage.protocol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.honormc.backstage.protocol.model.FieldDef;
import net.honormc.backstage.protocol.model.FieldType;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.KindDef;
import net.honormc.backstage.protocol.model.ListDef;
import net.honormc.backstage.protocol.model.ListEntry;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Prop;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Registry;
import net.honormc.backstage.protocol.model.Shape;

final class Samples {
    private Samples() {
    }

    static Map<String, Prop> fullProps() {
        Map<String, Prop> props = new LinkedHashMap<>();
        props.put("name", new Prop.Str("Gojo's well é世"));
        props.put("hp", new Prop.Num(-1250.75));
        props.put("boss", new Prop.Bool(true));
        props.put("off", new Prop.Bool(false));
        props.put("block", new Prop.BlockProp("minecraft:oak_stairs[facing=north]"));
        props.put("item", new Prop.ItemProp("{id:\"minecraft:diamond_sword\",count:1}"));
        props.put("list", new Prop.ListProp(List.of(
                new Prop.Str("a"),
                new Prop.Num(2),
                new Prop.ListProp(List.of(new Prop.Bool(false), new Prop.ListProp(List.of()))))));
        props.put("", new Prop.Str(""));
        return props;
    }

    static Point fullPoint() {
        return new Point("boss_spawn", 1.5, -64.25, 29_999_999.875, 90f, -12.5f, List.of("boss", "spawn"),
                fullProps());
    }

    static Point emptyPoint() {
        return new Point("a", 0, 0, 0, 0, 0, List.of(), Map.of());
    }

    static Group fullGroup() {
        return new Group("flag.top-1", new long[]{
                BlockPosPacking.pack(0, 0, 0),
                BlockPosPacking.pack(-30_000_000, -64, 29_999_999),
                BlockPosPacking.pack(123, 319, -678)},
                List.of("flag"), fullProps());
    }

    static Group emptyGroup() {
        return new Group("g", new long[0], List.of(), Map.of());
    }

    static Region fullBox() {
        return new Region("arena", new Shape.Box(-100, -64, Integer.MIN_VALUE, 100, 320, Integer.MAX_VALUE),
                List.of("arena", "pvp"), fullProps());
    }

    static Region fullPolygon() {
        return new Region("pit", new Shape.Polygon(new int[]{0, 0, 10, -5, -7, 20, 3, 3}, 40, 80), List.of("pit"),
                fullProps());
    }

    static Region emptyRegion() {
        return new Region("r", new Shape.Box(0, 0, 0, 0, 0, 0), List.of(), Map.of());
    }

    static List<Marker> allMarkers() {
        return List.of(fullPoint(), emptyPoint(), fullGroup(), emptyGroup(), fullBox(), fullPolygon(), emptyRegion());
    }

    static Registry fullRegistry() {
        return new Registry(
                List.of(MarkerType.POINT, MarkerType.GROUP, MarkerType.REGION),
                List.of(new KindDef("world_boss", "World boss", MarkerType.POINT, List.of(
                        new FieldDef("spawn", "Spawn", FieldType.POINT_REF, true, ""),
                        new FieldDef("flag", "Flag", FieldType.GROUP_REF, true, ""),
                        new FieldDef("arena", "Arena", FieldType.REGION_REF, false, ""),
                        new FieldDef("hp", "HP", FieldType.NUMBER, false, ""),
                        new FieldDef("title", "Title", FieldType.STRING, false, ""),
                        new FieldDef("enraged", "Enraged", FieldType.BOOL, false, ""),
                        new FieldDef("floor", "Floor", FieldType.BLOCK, false, ""),
                        new FieldDef("drop", "Drop", FieldType.ITEM, false, ""),
                        new FieldDef("effect", "Effect", FieldType.CHOICE, true, "effects"))),
                        new KindDef("empty", "", MarkerType.REGION, List.of())),
                List.of(new ListDef("effects", List.of(new ListEntry("flame", "Flame"), new ListEntry("", ""))),
                        new ListDef("none", List.of())));
    }

    static Registry emptyRegistry() {
        return new Registry(List.of(), List.of(), List.of());
    }

    static String repeat(char c, int count) {
        return String.valueOf(c).repeat(count);
    }
}
