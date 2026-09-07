package techguns.core;

/** Values displayed by the client. Keeping clamping here prevents stale network state drawing outside the HUD. */
public record WeaponHud(int rounds, int capacity, int reloadRemaining, float reloadProgress) {
    public static WeaponHud of(WeaponDefinition gun, int rounds, int reloadRemaining) {
        int remaining = Math.clamp(reloadRemaining, 0, gun.stats().reloadTicks());
        return new WeaponHud(gun.stats().clampRounds(rounds), gun.stats().capacity(), remaining,
                remaining == 0 ? 0 : 1 - (float) remaining / gun.stats().reloadTicks());
    }
    public boolean reloading() { return reloadRemaining > 0; }
}
