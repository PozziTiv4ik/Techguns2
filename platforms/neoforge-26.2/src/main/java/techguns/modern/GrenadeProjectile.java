package techguns.modern;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;
import techguns.core.HandGrenade;

/** Legacy grenade flight; a bounce keeps the entity, owner and remaining TTL. */
public class GrenadeProjectile extends Projectile {
    public static final ResourceKey<DamageType> IMPACT = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("grenade_impact"));
    public static final ResourceKey<DamageType> BLAST = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("grenade"));
    private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(GrenadeProjectile.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> GRAVITY = SynchedEntityData.defineId(GrenadeProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SPIN_START = SynchedEntityData.defineId(GrenadeProjectile.class, EntityDataSerializers.INT);
    private Vec3 origin;
    private int age, bounces=HandGrenade.STIELGRANATE.bounces;
    private ShotDamage damage=ShotDamage.PLAYER;
    public GrenadeProjectile(EntityType<? extends GrenadeProjectile> type, Level level) { super(type, level); }
    public HandGrenade grenade() { return HandGrenade.fromId(entityData.get(KIND)); }
    public ShotDamage shotDamage() { return damage; }
    public void npcDamage(float scale) { damage=new ShotDamage(true,scale); }
    protected int lifetime() { return grenade().lifetime; }
    protected int initialBounces() { return grenade().bounces; }
    protected float minimumGravity() { return grenade().gravity(30); }
    protected float maximumGravity() { return grenade().gravity(1); }
    public double outerRadius() { return grenade().outerRadius; }
    public float directDamage(double distance) { return grenade().directDamage(distance); }
    public float blastDamage(double distance) { return grenade().blastDamage(distance); }
    protected void flight(float gravity,int bounces) { entityData.set(GRAVITY,gravity);this.bounces=bounces; }
    public float gravity() { return entityData.get(GRAVITY); }
    public int age() { return age; }
    public int bounces() { return bounces; }
    public float spinAge(float partial) { return tickCount-entityData.get(SPIN_START)+partial; }
    public void configure(HandGrenade grenade, int heldTicks) {
        entityData.set(KIND, grenade.id()); flight(grenade.gravity(heldTicks),grenade.bounces);
    }
    public void shootLegacy(LivingEntity source, int muzzleSide) {
        shootLegacy(source,grenade().spread,muzzleSide,grenade().speed);
    }
    protected void shootLegacy(LivingEntity source,double spread,int muzzleSide,double speed) {
        LegacyShot.shoot(this, source, random, spread, muzzleSide, speed);
        orient(1); yRotO=getYRot(); xRotO=getXRot();
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, HandGrenade.STIELGRANATE.id()); builder.define(GRAVITY, .015f); builder.define(SPIN_START, 0);
    }
    private boolean target(Entity entity) { return entity!=getOwner() && !entity.isSpectator() && entity.isAlive() && entity.isPickable(); }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            setPos(position().add(getDeltaMovement())); orient(.2f);
            setDeltaMovement(getDeltaMovement().scale(isInWater()?(double).85f:(double).99f).add(0,-gravity(),0));
            return;
        }
        if (isRemoved() || age>=lifetime()) { discard(); return; }
        if (origin==null) origin=position();
        ++age;
        Vec3 start=position(), motion=getDeltaMovement(), end=start.add(motion);
        HitResult hit=level().clipIncludingBorder(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        var entityHit=ProjectileUtil.getEntityHitResult(level(),this,start,hit.getType()==HitResult.Type.MISS?end:hit.getLocation(),
                getBoundingBox().expandTowards(motion).inflate(1),this::target,.3f);
        if (entityHit!=null) hit=entityHit;
        boolean friendly=hit instanceof EntityHitResult result && getOwner() instanceof Player owner
                && result.getEntity() instanceof Player player && !owner.canHarmPlayer(player);
        if (!friendly && hit.getType()!=HitResult.Type.MISS && !EventHooks.onProjectileImpact(this,hit)) {
            if (hit instanceof EntityHitResult result) {
                var target=result.getEntity();
                // Inherited GenericProjectile has a tiny ordinary-cooldown physical impulse before its bullet hit.
                float raw=directDamage(origin.distanceTo(start));
                float amount=target instanceof LivingEntity?damage.againstEntity(raw):raw*damage.scale();
                if(amount<=0) { discard();return; }
                if (target instanceof LivingEntity) target.hurtServer(server,damage.source(server,IncendiaryBullet.KNOCKBACK_TYPE,this),.01f);
                boolean accepted=target.hurtServer(server,damage.source(server,IMPACT,this),amount);
                if (accepted && target instanceof LivingEntity) explode(); else discard();
            } else if (bounces>0) bounce((BlockHitResult)hit, motion); else explode();
        } else {
            setPos(end); orient(.2f);
            setDeltaMovement(motion.scale(isInWater()?(double).85f:(double).99f).add(0,-gravity(),0));
        }
        if (age>=lifetime()) discard();
    }
    private void bounce(BlockHitResult hit, Vec3 motion) {
        Vec3 reflected=switch(hit.getDirection().getAxis()) {
            case X -> new Vec3(-motion.x,motion.y,motion.z);
            case Y -> new Vec3(motion.x,-motion.y,motion.z);
            case Z -> new Vec3(motion.x,motion.y,-motion.z);
        };
        reflected=reflected.scale(.5);
        --bounces;
        // The source copy constructor subtracts .1 Y, then advances one reflected half-speed step.
        // Clip that displacement so its spawn offset cannot put a slow bounce inside the floor or through another wall.
        Vec3 outside=hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()).scale(.0001));
        Vec3 next=hit.getLocation().add(reflected).add(0,-.1,0);
        var clip=level().clip(new ClipContext(outside,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        setPos(clip.getType()==HitResult.Type.MISS ? next : outside);
        setDeltaMovement(reflected); needsSync=true;
        origin=null; entityData.set(SPIN_START,tickCount); orient(1);
        level().playSound(null,getX(),getY(),getZ(),SoundEvents.IRON_GOLEM_HURT,SoundSource.PLAYERS,1,1);
    }
    private void orient(float fraction) {
        var v=getDeltaMovement();
        setYRot(Mth.rotLerp(fraction,getYRot(),(float)Math.toDegrees(Math.atan2(v.x,v.z))));
        setXRot(Mth.rotLerp(fraction,getXRot(),(float)Math.toDegrees(Math.atan2(v.y,v.horizontalDistance()))));
    }
    public boolean explode() {
        if (isRemoved() || !(level() instanceof ServerLevel server)) return false;
        boolean result=GrenadeExplosion.detonate(server,this); discard(); return result;
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        saveProfile(output); damage.save(output); output.putFloat("gravity",gravity());
        output.putInt("age",age); output.putInt("bounces",bounces);
        if(origin!=null) output.store("origin",Vec3.CODEC,origin);
    }
    protected void saveProfile(ValueOutput output) { output.putString("grenade",grenade().id()); }
    protected void loadProfile(ValueInput input) { configure(HandGrenade.fromId(input.getStringOr("grenade","stielgranate")),30); }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            loadProfile(input); damage=ShotDamage.load(input);
            float gravity=input.getFloatOr("gravity",minimumGravity());
            age=input.getIntOr("age",0); bounces=input.getIntOr("bounces",initialBounces());
            if(!Float.isFinite(gravity) || gravity<minimumGravity() || gravity>maximumGravity()
                    || age<0 || age>=lifetime() || bounces<0 || bounces>initialBounces()) throw new IllegalArgumentException("Invalid flight state");
            entityData.set(GRAVITY,gravity); origin=input.read("origin",Vec3.CODEC).orElse(null);
            if(origin!=null && (!Double.isFinite(origin.x)||!Double.isFinite(origin.y)||!Double.isFinite(origin.z))) throw new IllegalArgumentException("Invalid origin");
        } catch(IllegalArgumentException invalid) { discard(); }
    }
}
