package techguns.modern.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Vanilla GameTestServer disables structures. Enable its native pipeline only in the opt-in worldgen run. */
@Mixin(GameTestServer.class)
public abstract class WorldgenTestOptionsMixin {
    @ModifyArg(method="<clinit>",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/WorldOptions;<init>(JZZ)V"),index=1)
    private static boolean techguns$enableNativeStructureTests(boolean original) {
        return original || Boolean.getBoolean("techguns.worldgenTest");
    }
}
