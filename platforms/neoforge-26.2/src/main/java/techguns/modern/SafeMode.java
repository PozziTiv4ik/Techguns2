package techguns.modern;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.*;

/** Legacy B toggle, persistent per player; permission and OP policy remain server authoritative. */
public final class SafeMode {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue OP_ONLY = BUILDER.comment("Original RestrictUnsafeModeToOP. False uses the techguns.allowunsafemode permission (allowed by default).")
            .define("RestrictUnsafeModeToOP", false);
    private static final ModConfigSpec SPEC = BUILDER.build();
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Techguns.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SAFE = ATTACHMENTS.register("safe_mode", () -> AttachmentType.builder(() -> false)
            .serialize(Codec.BOOL.fieldOf("enabled")).copyOnDeath().sync((holder, player) -> holder == player, ByteBufCodecs.BOOL).build());
    public static final PermissionNode<Boolean> UNSAFE = new PermissionNode<>(Techguns.MOD_ID, "allowunsafemode", PermissionTypes.BOOLEAN, (player, uuid, context) -> true);
    public static void register(IEventBus bus, ModContainer container) {
        ATTACHMENTS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "techguns-weapons-server.toml");
        NeoForge.EVENT_BUS.register(SafeMode.class);
    }
    public static boolean allowed(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return !player.getData(SAFE);
        if (OP_ONLY.get()) return level.getServer().getPlayerList().getOps().get(player.nameAndId()) != null;
        return !(player instanceof ServerPlayer server) || PermissionAPI.getPermission(server, UNSAFE);
    }
    public static boolean enabled(Player player) { return player.getData(SAFE) || !allowed(player); }
    public static boolean toggle(Player player) {
        if (!(player.level() instanceof ServerLevel) || !player.isAlive() || player.isSpectator()) return false;
        if (enabled(player) && !allowed(player)) {
            player.setData(SAFE, true);
            player.sendOverlayMessage(Component.translatable("hud.techguns.unsafe_denied"));
            return false;
        }
        player.setData(SAFE, !enabled(player));
        player.sendOverlayMessage(Component.translatable(player.getData(SAFE) ? "hud.techguns.safe" : "hud.techguns.unsafe"));
        return true;
    }
    private static void enforce(Player player) { if (!allowed(player) && !player.getData(SAFE)) player.setData(SAFE, true); }
    @SubscribeEvent public static void permissions(PermissionGatherEvent.Nodes event) { event.addNodes(UNSAFE); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { enforce(event.getEntity()); }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) { if (event.getEntity().tickCount % 20 == 0 && event.getEntity().level() instanceof ServerLevel) enforce(event.getEntity()); }
    private SafeMode() {}
}
