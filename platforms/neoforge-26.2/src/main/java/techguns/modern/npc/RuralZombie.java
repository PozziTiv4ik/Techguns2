package techguns.modern.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
import techguns.modern.armor.*;

/** Shared source behavior of the two danger-zero NPCs; custom Techguns spawner links remain pending. */
public abstract class RuralZombie extends BurningUndeadNpc {
    private final RuralZombieRules.Kind kind;
    protected RuralZombie(EntityType<? extends RuralZombie> type,Level level,RuralZombieRules.Kind kind) { super(type,level); this.kind=kind; }
    public RuralZombieRules.Kind kind() { return kind; }
    public static AttributeSupplier.Builder attributes(RuralZombieRules.Kind kind) {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,kind.health()).add(Attributes.MOVEMENT_SPEED,.2)
                .add(Attributes.ATTACK_DAMAGE,kind.attack()).add(Attributes.FOLLOW_RANGE,50).add(Attributes.ARMOR,0);
    }
    @Override public float armorAgainst(DamageKind kind) { return 0; }
    public void equipRoll(int weapon,int camo,double chest,double legs,double boots) {
        if(camo<0 || camo>=Armors.T1_MINER.getFirst().camos().size()) throw new IllegalArgumentException("Invalid miner camo");
        String id=kind.weapon(weapon);
        if(id.startsWith("techguns:")) equip(id.substring("techguns:".length()));
        else setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id))));
        for(var slot:ArmorSlot.values()) {
            double roll=switch(slot) { case HEAD -> 0; case CHEST -> chest; case LEGS -> legs; case FEET -> boots; };
            var stack=RuralZombieRules.armor(kind,slot,roll)?ArmorContent.T1_MINER.get(slot).toStack():ItemStack.EMPTY;
            if(!stack.isEmpty()) TGArmorItem.setCamo(stack,camo);
            setItemSlot(EquipmentSlot.valueOf(slot.name()),stack);
        }
    }
    public void equipForSpawn() {
        equipRoll(random.nextInt(kind.weaponCount()),random.nextInt(Armors.T1_MINER.getFirst().camos().size()),random.nextDouble(),random.nextDouble(),random.nextDouble());
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,EntitySpawnReason reason,SpawnGroupData data) {
        var result=super.finalizeSpawn(level,difficulty,reason,data); equipForSpawn(); setCanPickUpLoot(false); return result;
    }
    @Override public SoundEvent getAmbientSound() { return SoundEvents.ZOMBIE_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ZOMBIE_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ZOMBIE_DEATH; }
    @Override protected void playStepSound(BlockPos pos,BlockState state) { playSound(SoundEvents.ZOMBIE_STEP,.15f,1); }
}
