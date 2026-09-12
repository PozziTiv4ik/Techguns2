package techguns.modern.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.*;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import techguns.core.*;
import techguns.modern.armor.ArmorContent;

/** GenericNPCUndead with ZombieSoldier's fixed stats, equipment and no daylight burning. */
public final class ZombieSoldier extends ArmedNpc {
    public ZombieSoldier(EntityType<? extends ZombieSoldier> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,25).add(Attributes.MOVEMENT_SPEED,.25)
                .add(Attributes.ATTACK_DAMAGE,4).add(Attributes.FOLLOW_RANGE,50).add(Attributes.ARMOR,5);
    }
    @Override public float armorAgainst(DamageKind kind) { return ArmorMath.defaultArmor(kind,5,false); }
    public void equipRoll(int weapon, double helmet, double chest, double legs, double boots) {
        String id = ZombieSoldierRules.weapon(weapon);
        if (id.startsWith("techguns:")) equip(id.substring("techguns:".length()));
        else setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(weapon == 2 ? Items.IRON_SHOVEL : Items.STONE_SHOVEL));
        for (ArmorSlot slot : ArmorSlot.values()) {
            double roll = switch(slot) { case HEAD -> helmet; case CHEST -> chest; case LEGS -> legs; case FEET -> boots; };
            setItemSlot(EquipmentSlot.valueOf(slot.name()), ZombieSoldierRules.armor(roll) ? ArmorContent.T1_COMBAT.get(slot).toStack() : ItemStack.EMPTY);
        }
    }
    public void equipForSpawn() { equipRoll(random.nextInt(4),random.nextDouble(),random.nextDouble(),random.nextDouble(),random.nextDouble()); }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
        var result = super.finalizeSpawn(level,difficulty,reason,data);
        equipForSpawn(); setCanPickUpLoot(false); return result;
    }
    @Override public SoundEvent getAmbientSound() { return SoundEvents.ZOMBIE_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ZOMBIE_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ZOMBIE_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.ZOMBIE_STEP,.15f,1); }
}
