package techguns.modern.npc;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.*;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import techguns.core.*;
import techguns.modern.armor.ArmorContent;

/** Living source GenericNPC: independent 50% armor rolls, guaranteed headgear, three equal weapon choices. */
public final class ArmySoldier extends ArmedNpc {
    public static final List<String> WEAPONS = List.of("m4", "combatshotgun", "boltaction");
    public ArmySoldier(EntityType<? extends ArmySoldier> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,25).add(Attributes.MOVEMENT_SPEED,.3)
                .add(Attributes.ATTACK_DAMAGE,4).add(Attributes.FOLLOW_RANGE,75).add(Attributes.ARMOR,8).add(Attributes.ARMOR_TOUGHNESS,0);
    }
    @Override public float armorAgainst(DamageKind kind) { return ArmorMath.defaultArmor(kind,8,false); }
    public void equipLoadout(int weapon, boolean helmet, boolean chest, boolean legs, boolean boots) {
        equip(WEAPONS.get(weapon));
        setItemSlot(EquipmentSlot.HEAD,helmet?ArmorContent.ITEMS.get(ArmorSlot.HEAD).toStack():ArmorContent.BERET.toStack());
        setItemSlot(EquipmentSlot.CHEST,chest?ArmorContent.ITEMS.get(ArmorSlot.CHEST).toStack():ItemStack.EMPTY);
        setItemSlot(EquipmentSlot.LEGS,legs?ArmorContent.ITEMS.get(ArmorSlot.LEGS).toStack():ItemStack.EMPTY);
        setItemSlot(EquipmentSlot.FEET,boots?ArmorContent.ITEMS.get(ArmorSlot.FEET).toStack():ItemStack.EMPTY);
    }
    public void equipForSpawn() {
        boolean head=random.nextDouble()<=.5, chest=random.nextDouble()<=.5, legs=random.nextDouble()<=.5, boots=random.nextDouble()<=.5;
        equipLoadout(random.nextInt(3),head,chest,legs,boots);
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
        var result=super.finalizeSpawn(level,difficulty,reason,data); equipForSpawn(); setCanPickUpLoot(false); return result;
    }
    @Override public SoundEvent getAmbientSound() { return SoundEvents.VILLAGER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.VILLAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.ZOMBIE_VILLAGER_STEP,.15f,1); }
}
