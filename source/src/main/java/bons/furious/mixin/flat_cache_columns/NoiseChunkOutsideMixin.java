package bons.furious.mixin.flat_cache_columns;

import bons.furious.patch.flat_cache_columns.OutsideColumns;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_flat_cache_outside_columns (Minecraft 1.21.1 world generation, tested build NeoForge 21.1.252; both sides), part 1 of 2:
 * NoiseChunk.
 *
 * The constructor wraps the noise router once (router.mapAll(this::wrap), NoiseRouter.mapAll): every flat_cache of
 * the router becomes a FlatCache there. The wrapper around that one call first takes the verdict for the router that is
 * about to be wrapped (OutsideColumns.world: checked once per router), then marks the time the call runs, so that part
 * 2 knows which FlatCaches are the router's and can ask whether their marker passed. Tables are only handed out once
 * the constructor has returned. require = 0: when another mod has replaced the call, nothing is marked, no FlatCache
 * gets a table and the game runs the original code (one log line).
 *
 * Ported to 1.21.1: the constructor is unchanged where this switch reads it (the two blend caches are made first, then the
 * one NoiseRouter.mapAll call with this::wrap; the final density is mapped later by DensityFunction.mapAll); Mojang names.
 * terrain_density_memo, vanilla_noise_wrap_presize and terrain_router_shared_node_memo change the visitor argument of the
 * same call (@ModifyArg); this wrapper stands around the call itself and hands their visitor through.
 */
@Mixin(value = NoiseChunk.class, remap = false)
public abstract class NoiseChunkOutsideMixin implements OutsideColumns.Chunk {
    @Shadow @Final int cellCountXZ;

    @Unique
    private OutsideColumns.World bons$outsideWorldOf;
    @Unique
    private boolean bons$outsideWrapped;
    @Unique
    private boolean bons$outsideWrappingNow;
    @Unique
    private boolean bons$outsideIsDecided;

    @WrapOperation(method = "<init>", require = 0, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter bons$outsideRouterWrap(NoiseRouter router, DensityFunction.Visitor visitor, Operation<NoiseRouter> original) {
        this.bons$outsideWrapped = true;
        this.bons$outsideWorldOf = OutsideColumns.world(router);
        this.bons$outsideWrappingNow = true;
        try {
            return original.call(router, visitor);
        } finally {
            this.bons$outsideWrappingNow = false;
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$outsideDecide(CallbackInfo ci) {
        if (!this.bons$outsideWrapped) OutsideColumns.noBracket();
        this.bons$outsideIsDecided = true;
    }

    @Override
    public boolean bons$outsideDecided() {
        return this.bons$outsideIsDecided;
    }

    @Override
    public OutsideColumns.World bons$outsideWorld() {
        return this.bons$outsideWorldOf;
    }

    @Override
    public boolean bons$outsideWrapping() {
        return this.bons$outsideWrappingNow;
    }

    @Override
    public int bons$outsideCells() {
        return this.cellCountXZ;
    }
}
