package techguns.core;

import java.util.List;
import java.util.HashSet;

/** Item identifiers in original metadata order, including the reversed banner dye order. */
public record CamoPalette(String id, List<String> items) {
    public CamoPalette {
        items = List.copyOf(items);
        if (id == null || id.isBlank() || items.size() < 2 || new HashSet<>(items).size() != items.size()) throw new IllegalArgumentException("Invalid camouflage palette");
    }
    public int index(String item) { return items.indexOf(item); }
    public String next(String item, boolean back) {
        int index = index(item);
        if (index < 0) throw new IllegalArgumentException("Item is not part of palette " + id);
        return items.get(cycle(index, items.size(), back));
    }
    public static int cycle(int index, int count, boolean back) {
        if (count < 1 || index < 0 || index >= count) throw new IllegalArgumentException("Invalid camouflage index");
        return Math.floorMod(index + (back ? -1 : 1), count);
    }
}
