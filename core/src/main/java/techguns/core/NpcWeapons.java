package techguns.core;

/** Generated from TGuns.setAIStats and GenericGun.getAIAttack. */
public final class NpcWeapons {
    public static NpcAttackSpec forWeapon(String id) {
        return switch (id) {
            case "handcannon" -> new NpcAttackSpec(12.0, 60, 0, 0, 0.0);
            case "sawedoff" -> new NpcAttackSpec(12.0, 60, 2, 20, 0.0);
            case "revolver" -> new NpcAttackSpec(18.0, 90, 6, 20, 0.0);
            case "goldenrevolver" -> new NpcAttackSpec(24.0, 60, 6, 15, 0.0);
            case "thompson" -> new NpcAttackSpec(18.0, 45, 3, 3, 0.0);
            case "boltaction" -> new NpcAttackSpec(36.0, 60, 0, 0, 0.0);
            case "combatshotgun" -> new NpcAttackSpec(12.0, 30, 0, 0, 0.0);
            case "pistol" -> new NpcAttackSpec(24.0, 30, 3, 10, 0.0);
            case "ak47" -> new NpcAttackSpec(24.0, 30, 3, 3, 0.0);
            case "m4" -> new NpcAttackSpec(24.0, 30, 3, 3, 0.0);
            case "m4_infiltrator" -> new NpcAttackSpec(24.0, 30, 3, 3, 0.0);
            case "lmg" -> new NpcAttackSpec(24.0, 40, 6, 3, 0.0);
            case "mac10" -> new NpcAttackSpec(18.0, 35, 3, 2, 0.0);
            case "as50" -> new NpcAttackSpec(36.0, 30, 0, 0, 0.0);
            case "aug" -> new NpcAttackSpec(24.0, 30, 3, 3, 0.0);
            case "scar" -> new NpcAttackSpec(24.0, 30, 5, 2, 0.0);
            case "vector" -> new NpcAttackSpec(18.0, 35, 3, 2, 0.0);
            case "lasergun" -> new NpcAttackSpec(24.0, 30, 0, 0, 0.0);
            case "laserpistol" -> new NpcAttackSpec(24.0, 30, 0, 0, 0.0);
            case "rocketlauncher" -> new NpcAttackSpec(24.0, 80, 0, 0, 0.35);
            case "netherblaster" -> new NpcAttackSpec(24.0, 40, 0, 0, 0.0);
            default -> throw new IllegalArgumentException("Unported NPC weapon: " + id);
        };
    }
    private NpcWeapons() {}
}
