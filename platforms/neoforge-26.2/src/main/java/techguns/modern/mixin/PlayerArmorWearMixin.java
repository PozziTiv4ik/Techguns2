package techguns.modern.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import techguns.modern.armor.TGArmorSystem;

/** Replace only ordinary body-armor wear; helmet impacts and explicit slot damage retain their native path. */
@Mixin(Player.class)
public abstract class PlayerArmorWearMixin {
    @Inject(method="hurtArmor",at=@At("HEAD"),cancellable=true)
    private void techguns$deferSpecialArmorWear(DamageSource source,float amount,CallbackInfo callback) {
        var player=(Player)(Object)this;
        TGArmorSystem.refresh(player);
        if(TGArmorSystem.hasArmor(player)) callback.cancel();
    }
}
