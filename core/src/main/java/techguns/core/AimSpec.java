package techguns.core;

/** Original GenericGun.setZoom parameters, shared by camera and server accuracy rules. */
public record AimSpec(float fovMultiplier, boolean toggle, float accuracyMultiplier, boolean centered) {
    public AimSpec {
        if (!Float.isFinite(fovMultiplier) || fovMultiplier <= 0 || fovMultiplier > 1
                || !Float.isFinite(accuracyMultiplier) || accuracyMultiplier <= 0 || accuracyMultiplier > 1)
            throw new IllegalArgumentException("Invalid aim parameters");
    }
    public boolean supported() { return fovMultiplier < 1; }
    public float applyFov(float normalFov) { return normalFov * fovMultiplier; }
}
