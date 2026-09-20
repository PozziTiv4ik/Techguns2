package techguns.modern.npc;

import techguns.core.DamageKind;

/** Source INpcTGDamageSystem contract; not every implementation belongs to a GenericNPC faction. */
public interface NpcTypedArmor {
    float armorAgainst(DamageKind kind);
}
