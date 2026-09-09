package techguns.modern.radiation;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import techguns.core.ExplosionMath;

/** Original nuclear fallout: 60 seconds, radii 15/25, amplifiers 9/2, decay in the last 30 seconds. */
public final class RadiationZone extends Entity {
    public static final int DURATION = 1200;
    private int age;
    public RadiationZone(EntityType<? extends RadiationZone> type, Level level) { super(type, level); noPhysics = true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        if (++age > DURATION) { discard(); return; }
        if (age % 20 == 0) RadiationSystem.applyZone(server, position(), 25, 22,
                ExplosionMath.falloutStrength(9, age, DURATION), 15, ExplosionMath.falloutStrength(2, age, DURATION));
    }
    public int age() { return age; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }
    @Override protected void addAdditionalSaveData(ValueOutput output) { output.putInt("age", age); }
    @Override protected void readAdditionalSaveData(ValueInput input) { age = Math.clamp(input.getIntOr("age", 0), 0, DURATION); }
}
