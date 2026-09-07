package techguns.core;

/** Pure inventory arithmetic. The platform must apply consumption and the new state atomically. */
public final class Magazine {
    public record Reload(int rounds, int consumedItems) {}
    public record Plan(int rounds, int consumedItems, int emptyMagazines, int looseBundles) {}

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

    public static Plan plan(WeaponDefinition gun, int rounds, int availableItems, boolean creative) {
        int valid = gun.stats().clampRounds(rounds);
        int capacity = gun.stats().capacity();
        if (valid == capacity || (!creative && availableItems <= 0)) return new Plan(valid, 0, 0, 0);
        if (creative) return new Plan(capacity, 0, 0, 0);
        if (gun.ammo().individual()) {
            int count = Math.min(capacity - valid, availableItems);
            return new Plan(valid + count, count, 0, 0);
        }
        int loose = (int) ((long) valid * gun.ammo().bundlesPerMagazine() / capacity);
        return new Plan(capacity, 1, gun.ammo().magazine() ? 1 : 0, loose);
    }
}
