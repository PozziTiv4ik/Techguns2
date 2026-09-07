package techguns.core;

/** Pure inventory arithmetic. The platform must apply consumption and the new state atomically. */
public final class Magazine {
    public record Reload(int rounds, int consumedItems) {}

    private Magazine() {}

    public static boolean canFire(WeaponSpec spec, int rounds, int cooldown, boolean reloading) {
        return spec.clampRounds(rounds) > 0 && cooldown <= 0 && !reloading;
    }

    public static int afterShot(WeaponSpec spec, int rounds) {
        int valid = spec.clampRounds(rounds);
        if (valid == 0) throw new IllegalStateException("Cannot fire an empty magazine");
        return valid - 1;
    }

    /** Legacy bundle reload: replaces the remaining cylinder contents using one ammo item. */
    public static Reload reloadBundle(WeaponSpec spec, int rounds, int availableItems, boolean creative) {
        int valid = spec.clampRounds(rounds);
        if (valid == spec.capacity() || (!creative && availableItems <= 0)) return new Reload(valid, 0);
        return new Reload(spec.capacity(), creative ? 0 : 1);
    }
}
