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
 * vanilla_raycast_fluid_none (Minecraft 1.20.1 on Forge 47.4.16; both sides): gives Level its own clip (m_45547_), which
 * otherwise is BlockGetter's default method. For a clip context that ignores fluids (ClipContext.Fluid.NONE) on a level
 * whose class keeps Level.getFluidState, ClipFast.clip walks the ray with vanilla's own traversal (BlockGetter.traverseBlocks)
 * and makes every call of the original step except the fluid-state read, whose result NONE never looks at. Every other clip
 * runs BlockGetter's default method itself (bound once as a special method handle in Level's static initializer, so the
 * call is a constant the JIT inlines). (Mixin 0.8.5 has no injectors in interfaces, so the default method itself cannot be
 * wrapped.) Our own logic only; no Minecraft code is carried.
 *
 * Generic stand-down (1.0.30 assembly): any other mod that gives Level its own m_45547_ wins. The method is a soft
 * implementation of BlockGetter.m_45547_ (@Implements prefix "bons$raycast$") marked @Intrinsic, in a mixin of priority
 * 1,000,000,000, so Mixin applies it after every other mixin on Level (they sort by priority). When another mixin has
 * already added m_45547_ to Level (Valkyrien Skies' feature.clip_replace.MixinLevel, or any other mod's), Mixin keeps
 * that method and skips this one (MixinApplicatorStandard.mergeIntrinsic: "Skipping Intrinsic mixin method", debug level),
 * whatever that mixin's priority (below ours) or its config's overwrite rules; a mixin above our priority still replaces
 * ours. Without another such method this one is added exactly as before. (A plain method at the LOWEST priority would let
 * a later one replace it, but makes a mod whose config sets overwrites.requireAnnotations fail with InvalidMixinException:
 * proven offline, notes/vanilla_raycast.md.) The Valkyrien Skies yield in patches/vanilla_raycast.json stays.
 */
@Mixin(value = Level.class, priority = 1_000_000_000, remap = false)
@Implements(@Interface(iface = BlockGetter.class, prefix = "bons$raycast$", remap = Interface.Remap.NONE))
public abstract class LevelClipMixin {
    @Unique
    private static final MethodHandle bons$defaultClip = bons$bindDefaultClip();

    @Unique
    private static MethodHandle bons$bindDefaultClip() {
        try {
            return MethodHandles.lookup().findSpecial(BlockGetter.class, "m_45547_",
                    MethodType.methodType(BlockHitResult.class, ClipContext.class), Level.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Bons and Furious: vanilla_raycast_fluid_none cannot bind BlockGetter's clip", e);
        }
    }

    /** Level.m_45547_ (clip), unless another mod already gives Level its own (then Mixin keeps theirs: @Intrinsic). */
    @Intrinsic
    public BlockHitResult bons$raycast$m_45547_(ClipContext context) {
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
