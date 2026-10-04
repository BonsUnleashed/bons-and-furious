package bons.furious.mixin.vanilla_raycast;

import bons.furious.patch.vanilla_raycast.ClipFast;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_raycast_fluid_none (Minecraft 1.21.1 with NeoForge 21.1.252; both sides): gives Level its own clip, which
 * otherwise is BlockGetter's default method. For a clip context that ignores fluids (ClipContext.Fluid.NONE) on a level
 * whose class keeps Level.getFluidState, ClipFast.clip walks the ray with vanilla's own traversal (BlockGetter.traverseBlocks)
 * and makes every call of the original step except the fluid-state read, whose result NONE never looks at. Every other clip
 * runs BlockGetter's default method itself (bound once as a special method handle in Level's static initializer, so the
 * call is a constant the JIT inlines). (Mixin 0.8.5 has no injectors in interfaces, so the default method itself cannot be
 * wrapped.) Our own logic only; no Minecraft code is carried.
 *
 * Generic stand-down (1.0.30 assembly): any other mod that gives Level its own clip wins. The method is a soft
 * implementation of BlockGetter.clip (@Implements prefix "bons$raycast$") marked @Intrinsic, in a mixin of priority
 * 1,000,000,000, so Mixin applies it after every other mixin on Level (they sort by priority). When another mixin has
 * already added clip to Level (Valkyrien Skies' feature.clip_replace.MixinLevel, or any other mod's), Mixin keeps
 * that method and skips this one (MixinApplicatorStandard.mergeIntrinsic: "Skipping Intrinsic mixin method", debug level),
 * whatever that mixin's priority (below ours) or its config's overwrite rules; a mixin above our priority still replaces
 * ours. Without another such method this one is added exactly as before. (A plain method at the LOWEST priority would let
 * a later one replace it, but makes a mod whose config sets overwrites.requireAnnotations fail with InvalidMixinException:
 * proven offline, notes/vanilla_raycast.md.) The Valkyrien Skies yield in patches/vanilla_raycast.json stays.
 *
 * Ported to 1.21.1: BlockGetter.clip, its step and miss lambdas, traverseBlocks and ClipContext are unchanged (Mojang
 * names; the mixin runs on Mixin 0.8.7, whose mergeIntrinsic keeps the same skip rule). New on 1.21.1: another mod can
 * also change Level's clip without adding one, by an @Overwrite of BlockGetter's default method (Radium 0.13.1's
 * world.raycast BlockViewMixin, on by default); ClipFast.applies stands down at run time whenever BlockGetter's clip, its
 * lambdas, traverseBlocks or clipWithInteractionOverride carry another mixin (@MixinMerged), or BlockGetter holds an
 * injector handler, so every clip then goes to BlockGetter's (modified) default method; Radium also has a yield entry.
 */
@Mixin(value = Level.class, priority = 1_000_000_000, remap = false)
@Implements(@Interface(iface = BlockGetter.class, prefix = "bons$raycast$", remap = Interface.Remap.NONE))
public abstract class LevelClipMixin {
    @Unique
    private static final MethodHandle bons$defaultClip = bons$bindDefaultClip();

    @Unique
    private static MethodHandle bons$bindDefaultClip() {
        try {
            return MethodHandles.lookup().findSpecial(BlockGetter.class, "clip",
                    MethodType.methodType(BlockHitResult.class, ClipContext.class), Level.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Bons and Furious: vanilla_raycast_fluid_none cannot bind BlockGetter's clip", e);
        }
    }

    /** Level.clip (clip), unless another mod already gives Level its own (then Mixin keeps theirs: @Intrinsic). */
    @Intrinsic
    public BlockHitResult bons$raycast$clip(ClipContext context) {
        if (!ClipFast.enabled || !ClipFast.applies(this, context)) return bons$defaultClip(context);
        BlockGetter level = (BlockGetter) (Object) this;
        if (ClipFast.SHADOW) {
            BlockHitResult theirs = bons$defaultClip(context);
            ClipFast.shadow(ClipFast.clip(level, context), theirs);
            return theirs;
        }
        return ClipFast.clip(level, context);
    }

    @Unique
    private BlockHitResult bons$defaultClip(ClipContext context) {
        try {
            return (BlockHitResult) bons$defaultClip.invokeExact((Level) (Object) this, context);
        } catch (Throwable t) {
            throw ClipFast.<RuntimeException>rethrow(t);       // whatever the default method throws, unchanged
        }
    }
}
