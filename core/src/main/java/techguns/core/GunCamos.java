package techguns.core;

import java.util.List;
import java.util.Map;

/** Generated from opt-in weapon-ports camos and GenericGun / ClientProxy. */
public final class GunCamos {
    private static final Map<String, List<String>> NAMES = Map.ofEntries(
        Map.entry("pdw", List.of("techguns.item.defaultcamo", "item.techguns.pdw.camoname.1", "item.techguns.pdw.camoname.2")));
    public static final int MAX_INDEX = 2;
    public static int count(String id) { return NAMES.getOrDefault(id, List.of()).size(); }
    public static String nameKey(String id, int index) { return NAMES.get(id).get(index); }
    private GunCamos() {}
}
