package techguns.modern.npc;

/** Original INPCTechgunsShooter muzzle offsets, independent of ground/flying AI. */
public interface NpcMuzzle {
    default double bulletSideOffset() { return 0; }
    default double bulletHeightOffset() { return 0; }
}
