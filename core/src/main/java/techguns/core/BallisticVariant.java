package techguns.core;

public enum BallisticVariant {
    DEFAULT("default"), INCENDIARY("incendiary"), EXPLOSIVE("explosive");
    private final String id;
    BallisticVariant(String id) { this.id = id; }
    public String id() { return id; }
    public boolean supported(WeaponDefinition gun) {
        return switch (this) {
            case DEFAULT -> true;
            case INCENDIARY -> IncendiaryAmmo.supported(gun);
            case EXPLOSIVE -> ExplosiveAmmo.supported(gun);
        };
    }
    public AmmoSpec ammo(WeaponDefinition gun) {
        if (!supported(gun)) throw new IllegalArgumentException("Unsupported ammunition for " + gun.id());
        return switch (this) {
            case DEFAULT -> gun.ammo();
            case INCENDIARY -> IncendiaryAmmo.ammo(gun, this);
            case EXPLOSIVE -> ExplosiveAmmo.ammo(gun);
        };
    }
    public static BallisticVariant fromId(String id) {
        for (var value : values()) if (value.id.equals(id)) return value;
        throw new IllegalArgumentException("Unknown ballistic variant: " + id);
    }
}
