package techguns.core;

public record AmmoSpec(String item, String emptyItem, String looseItem,
                       int bundlesPerMagazine, boolean individual) {
    public AmmoSpec {
        if (item == null || item.isEmpty() || emptyItem == null || looseItem == null || bundlesPerMagazine < 0)
            throw new IllegalArgumentException("Invalid ammo definition");
        if (individual && !emptyItem.isEmpty()) throw new IllegalArgumentException("Individual ammo cannot be a magazine");
    }
    public boolean magazine() { return !emptyItem.isEmpty(); }
}
