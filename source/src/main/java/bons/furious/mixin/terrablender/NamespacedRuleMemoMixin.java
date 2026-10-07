package bons.furious.mixin.terrablender;

import bons.furious.patch.terrablender.NamespaceRuleMemo;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * terrablender_namespace_rule_memo (TerraBlender 4.1.0.8 for NeoForge 1.21.1, LGPL-3.0; server side of world
 * generation).
 *
 * NamespacedSurfaceRuleSource.NamespacedRule.tryApply, rewritten with a per-rule memo of the last biome Holder and the
 * namespace rule TerraBlender selects for it (NamespaceRuleMemo explains why the selection depends on nothing else).
 * One NamespacedRule exists per chunk surface context and is used by one thread, so two plain fields suffice. The
 * method body is TerraBlender's (LGPL), restructured around the memo: biome read once, namespace rule first, base rule
 * when it gives nothing.
 *
 * The record's components and SurfaceRules.Context's biome supplier have protected Minecraft types this source cannot
 * name, so they are read through method handles created in the record's own static initializer: the lookup has exactly
 * the access TerraBlender's own body has (its access transformer opens SurfaceRules.Context and its fields), so it
 * resolves whenever TerraBlender's code itself links.
 *
 * Ported to 1.21.1: the overwritten body is re-derived from TerraBlender 4.1.0.8's tryApply (decompiled), which makes the
 * same calls in the same order as 3.0.1.10's: context.biome.get() once, biome.is(key -> rules.containsKey(namespace)),
 * rules.get(unwrapKey namespace).tryApply, then baseRule.tryApply when that gave null (4.1.0.8 only dropped the
 * {@code @Nullable} annotation). Member names are Mojang's (tryApply, biome), which NeoForge uses at runtime.
 */
@Mixin(targets = "terrablender.worldgen.surface.NamespacedSurfaceRuleSource$NamespacedRule", remap = false)
public abstract class NamespacedRuleMemoMixin {
    @Unique
    private static final MethodHandle bons$CONTEXT, bons$BASE, bons$RULES, bons$BIOME, bons$TRY_APPLY;

    static {
        try {
            MethodHandles.Lookup own = MethodHandles.lookup();
            Class<?> self = own.lookupClass();
            Class<?> context = Class.forName("net.minecraft.world.level.levelgen.SurfaceRules$Context", false, self.getClassLoader());
            Class<?> rule = Class.forName("net.minecraft.world.level.levelgen.SurfaceRules$SurfaceRule", false, self.getClassLoader());
            MethodType getter = MethodType.methodType(Object.class, Object.class);
            bons$CONTEXT = own.findGetter(self, "context", context).asType(getter);
            bons$BASE = own.findGetter(self, "baseRule", rule).asType(getter);
            bons$RULES = own.findGetter(self, "rules", Map.class).asType(MethodType.methodType(Map.class, Object.class));
            bons$BIOME = own.findGetter(context, "biome", Supplier.class).asType(MethodType.methodType(Supplier.class, Object.class));
            bons$TRY_APPLY = own.findVirtual(rule, "tryApply", MethodType.methodType(BlockState.class, int.class, int.class, int.class))
                    .asType(MethodType.methodType(BlockState.class, Object.class, int.class, int.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Unique   // 1.0.34: transient (both): Gson reads a record's non-transient fields as components and fails without an accessor
    private transient Holder<Biome> bons$biome;
    @Unique
    private transient Object bons$selected;

    /**
     * @author BonsUnleashed
     * @reason Select the namespace rule once per biome Holder instead of once per block (same rule, same order).
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    public BlockState tryApply(int x, int y, int z) {
        Object self = this;
        Holder<Biome> biome;
        Object selected;
        BlockState state = null;
        try {
            biome = (Holder<Biome>) ((Supplier<?>) bons$BIOME.invokeExact((Object) bons$CONTEXT.invokeExact(self))).get();
            if (!NamespaceRuleMemo.enabled) {
                selected = NamespaceRuleMemo.select((Map<String, ?>) bons$RULES.invokeExact(self), biome);
            } else if (biome == null || biome != this.bons$biome) {
                selected = NamespaceRuleMemo.select((Map<String, ?>) bons$RULES.invokeExact(self), biome);
                this.bons$biome = biome;
                this.bons$selected = selected;
            } else {
                selected = this.bons$selected;
                if (NamespaceRuleMemo.SHADOW) NamespaceRuleMemo.shadow((Map<String, ?>) bons$RULES.invokeExact(self), biome, selected);
            }
            if (selected != null) state = (BlockState) bons$TRY_APPLY.invokeExact(selected, x, y, z);
            if (state == null) state = (BlockState) bons$TRY_APPLY.invokeExact((Object) bons$BASE.invokeExact(self), x, y, z);
        } catch (Throwable t) {
            throw NamespaceRuleMemo.rethrow(t);
        }
        return state;
    }
}
