package techguns.core;

public enum BallisticVariant {
    DEFAULT("default"), INCENDIARY("incendiary");
    private final String id;
    BallisticVariant(String id) { this.id = id; }
    public String id() { return id; }
    public static BallisticVariant fromId(String id) {
        for (var value : values()) if (value.id.equals(id)) return value;
        throw new IllegalArgumentException("Unknown ballistic variant: " + id);
    }
}
