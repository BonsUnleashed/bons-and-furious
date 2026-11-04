package bons.furious.patch.forge_listener_guard;

/**
 * forge_listener_prologue_guard: the twelve Forge post sites whose MinecraftForge.EVENT_BUS.post call is wrapped, and
 * what the group's mixin config plugin found there once every mod's mixins were applied (ListenerGuardPlugin.postApply).
 * Kept free of game classes: it is loaded while Forge's hook classes are being transformed.
 */
public final class Sites {
    public static final int PRE_CLIENT_TICK = 0, POST_CLIENT_TICK = 1, PLAYER_PRE_TICK = 2, PLAYER_POST_TICK = 3,
            PRE_SERVER_TICK = 4, POST_SERVER_TICK = 5, PRE_LEVEL_TICK = 6, POST_LEVEL_TICK = 7, RENDER_TICK_START = 8,
            RENDER_TICK_END = 9, LIVING_TICK = 10, RENDER_STAGE = 11;
    public static final int COUNT = 12;
    static final String EFF = "net/minecraftforge/event/ForgeEventFactory", HOOKS = "net/minecraftforge/common/ForgeHooks",
            HOOKS_CLIENT = "net/minecraftforge/client/ForgeHooksClient";
    /** site -> {owner, method name, descriptor} of the Forge method that posts there. */
    static final String[][] METHODS = {
            {EFF, "onPreClientTick", "()V"},
            {EFF, "onPostClientTick", "()V"},
            {EFF, "onPlayerPreTick", "(Lnet/minecraft/world/entity/player/Player;)V"},
            {EFF, "onPlayerPostTick", "(Lnet/minecraft/world/entity/player/Player;)V"},
            {EFF, "onPreServerTick", "(Ljava/util/function/BooleanSupplier;Lnet/minecraft/server/MinecraftServer;)V"},
            {EFF, "onPostServerTick", "(Ljava/util/function/BooleanSupplier;Lnet/minecraft/server/MinecraftServer;)V"},
            {EFF, "onPreLevelTick", "(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"},
            {EFF, "onPostLevelTick", "(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"},
            {EFF, "onRenderTickStart", "(F)V"},
            {EFF, "onRenderTickEnd", "(F)V"},
            {HOOKS, "onLivingTick", "(Lnet/minecraft/world/entity/LivingEntity;)Z"},
            {HOOKS_CLIENT, "dispatchRenderStage", "(Lnet/minecraftforge/client/event/RenderLevelStageEvent$Stage;Lnet/minecraft/client/renderer/LevelRenderer;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V"}};
    static final String[] NAMES = {"ClientTickEvent START", "ClientTickEvent END", "PlayerTickEvent START", "PlayerTickEvent END",
            "ServerTickEvent START", "ServerTickEvent END", "LevelTickEvent START", "LevelTickEvent END", "RenderTickEvent START",
            "RenderTickEvent END", "LivingTickEvent", "RenderLevelStageEvent"};
    /** True: our wrapper is in that method; written once per class by postApply (class loading is single-threaded per class). */
    static final boolean[] HOOKED = new boolean[COUNT];
    /** True: another mod's mixin also changes that method, so the site always posts the stock way (through its chain). */
    static final boolean[] FOREIGN = new boolean[COUNT];
    static final String[] FOREIGN_BY = new String[COUNT];

    private Sites() {}
}
