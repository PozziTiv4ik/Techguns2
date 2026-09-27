package techguns.modern.npc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** GenericFlyingMob's three independent controls, including the old nearest-player acquisition band. */
public final class HelicopterFlight {
    public static final class Move extends MoveControl {
        private final AttackHelicopter heli;
        private int cooldown;
        public Move(AttackHelicopter heli) { super(heli); this.heli=heli; }
        @Override public void tick() {
            if(operation!=Operation.MOVE_TO || !heli.isAlive()) return;
            var delta=new Vec3(wantedX-heli.getX(),wantedY-heli.getY(),wantedZ-heli.getZ()); double distance=delta.length();
            if(distance<1.0e-6) { operation=Operation.WAIT; return; }
            if(cooldown--<=0) {
                cooldown+=heli.getRandom().nextInt(5)+2; var step=delta.scale(1/distance); var box=heli.getBoundingBox();
                for(int i=1;i<distance;i++) {
                    box=box.move(step);
                    if(!heli.level().hasChunksAt(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)) || !heli.level().noCollision(heli,box)) { operation=Operation.WAIT; return; }
                }
                heli.setDeltaMovement(heli.getDeltaMovement().add(step.scale(.1)));
            }
        }
        public void save(ValueOutput out) { out.putInt("flight_cooldown",cooldown); if(hasWanted()) out.store("flight_target",Vec3.CODEC,new Vec3(wantedX,wantedY,wantedZ)); }
        public void load(ValueInput in) {
            cooldown=Math.clamp(in.getIntOr("flight_cooldown",0),0,6);
            in.read("flight_target",Vec3.CODEC).filter(v->Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z) && v.distanceToSqr(heli.position())<=3600)
                    .ifPresent(v->setWantedPosition(v.x,v.y,v.z,1));
        }
    }
    public static final class RandomFly extends Goal {
        private final AttackHelicopter heli;
        public RandomFly(AttackHelicopter heli) { this.heli=heli; setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            var move=heli.getMoveControl(); if(!move.hasWanted()) return true;
            double d=heli.position().distanceToSqr(new Vec3(move.getWantedX(),move.getWantedY(),move.getWantedZ())); return d<1 || d>3600;
        }
        @Override public boolean canContinueToUse() { return false; }
        @Override public void start() {
            var r=heli.getRandom(); double x=heli.getX()+(r.nextFloat()*2-1)*16,z=heli.getZ()+(r.nextFloat()*2-1)*16;
            // The original casts the current coordinates to int, not the destination coordinates.
            int y=heli.level().getHeight(Heightmap.Types.MOTION_BLOCKING,(int)heli.getX(),(int)heli.getZ())+24;
            heli.getMoveControl().setWantedPosition(x,y,z,1);
        }
    }
    public static final class Look extends Goal {
        private final AttackHelicopter heli;
        public Look(AttackHelicopter heli) { this.heli=heli; setFlags(EnumSet.of(Flag.LOOK)); }
        @Override public boolean canUse() { return true; }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            var target=heli.getTarget();
            if(target==null) {
                heli.setYRot((float)-Math.toDegrees(Math.atan2(heli.getDeltaMovement().x,heli.getDeltaMovement().z))); heli.yBodyRot=heli.getYRot(); heli.setXRot(0);
            } else if(heli.distanceToSqr(target)<96*96) {
                heli.setYRot((float)-Math.toDegrees(Math.atan2(target.getX()-heli.getX(),target.getZ()-heli.getZ()))); heli.yBodyRot=heli.getYRot();
                if(heli.getY()>target.getY()) {
                    var toTarget=target.position().subtract(heli.position()).normalize();
                    float angle=(float)-Math.clamp(90-Math.toDegrees(Math.acos(Math.clamp(-toTarget.y,-1d,1d))),0,90);
                    heli.getLookControl().setLookAt(target,45,angle);
                }
            }
        }
    }
    /** 1.12 EntityLookHelper accepts a negative pitch limit; modern clamp would not preserve it. */
    public static final class LegacyLook extends LookControl {
        public LegacyLook(AttackHelicopter heli) { super(heli); }
        @Override public void setLookAt(double x,double y,double z,float yaw,float pitch) { super.setLookAt(x,y,z,yaw,pitch); lookAtCooldown=1; }
        @Override public float rotateTowards(float current,float target,float maximum) {
            float d=net.minecraft.util.Mth.wrapDegrees(target-current); if(d>maximum) d=maximum; if(d < -maximum) d=-maximum; return current+d;
        }
    }
    public static final class Target extends Goal {
        private final AttackHelicopter heli;
        private Player selected;
        public Target(AttackHelicopter heli) { this.heli=heli; setFlags(EnumSet.of(Flag.TARGET)); }
        public boolean acceptable(Player player) {
            if(!player.isAlive() || player.isSpectator() || player.getAbilities().invulnerable || heli.isAlliedTo(player)) return false;
            double range=heli.getAttributeValue(Attributes.FOLLOW_RANGE);
            if(player.isShiftKeyDown()) range*=.800000011920929;
            if(player.isInvisible()) range*=.7f*Math.max(.1f,player.getArmorCoverPercentage());
            return heli.distanceToSqr(player)<=range*range && heli.hasLineOfSight(player);
        }
        @Override public boolean canUse() {
            double range=heli.getAttributeValue(Attributes.FOLLOW_RANGE);
            selected=heli.level().getEntitiesOfClass(Player.class,heli.getBoundingBox().inflate(range,4,range),this::acceptable).stream().min(Comparator.comparingDouble(heli::distanceToSqr)).orElse(null);
            return selected!=null;
        }
        @Override public boolean canContinueToUse() {
            var target=heli.getTarget(); double range=heli.getAttributeValue(Attributes.FOLLOW_RANGE);
            return target!=null && target.isAlive() && !target.isSpectator() && !(target instanceof Player p && p.getAbilities().invulnerable)
                    && !heli.isAlliedTo(target) && heli.distanceToSqr(target)<=range*range;
        }
        @Override public void start() { heli.setTarget(selected); }
        @Override public void stop() { selected=null; heli.setTarget(null); }
    }
    private HelicopterFlight() {}
}
