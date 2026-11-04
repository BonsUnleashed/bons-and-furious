package bons.furious.patch.vanilla_render_tradeoffs;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_armor_layer_bake_reuse (Minecraft 1.20.1 on Forge 47.4.28, client; since 1.0.49; kind
 * change, OFF by default). Idea: Collections Of Optimizations (armor.cacheArmorModelBakes), idea text only.
 *
 * What the game does. Forge's armour hook ForgeHooksClient.getArmorModel asks the worn item for its model on every render
 * of every worn piece; many items (every MCreator armour: three bakes per chestplate call) build it from scratch with
 * Minecraft.getEntityModels().bakeLayer(layer) (EntityModelSet.m_171103_), which bakes a whole new ModelPart tree each time.
 *
 * What the switch does. While getArmorModel runs on the render thread (a depth counter), bakeLayer(layer) on an
 * EntityModelSet returns the tree it baked for that layer the first time instead of a new one; the memo belongs to that
 * EntityModelSet and is dropped at both ends of its onResourceManagerReload (m_6213_). Outside armour lookups, and on
 * every other thread, bakeLayer is untouched. A layer that fails to bake throws as before and stores nothing.
 *
 * Trade-off (off by default): one ModelPart tree is shared by every armour lookup of that layer, so a pose, visibility or
 * scale a mod leaves on a sub-part (Forge resets only the seven top-level humanoid parts per render) carries into the next
 * render of that armour, and bakeLayer hooks of other mods (Entity Model Features, Fusion) run once per cached layer
 * instead of once per call. No saved data, packet or server state is involved.
 *
 * -Dbons_and_furious.vanillaArmorLayerBakeReuse=false switches it off at run time (every call bakes again).
 */
public final class ArmorBakeReuse {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaArmorLayerBakeReuse", "true"));
    /** Harness/rig counters: bakes stored, lookups answered from the memo. */
    public static final AtomicLong STORED = new AtomicLong(), REUSED = new AtomicLong();
    /** getArmorModel nesting on the render thread (only that thread changes or reads it). */
    private static int depth;
    private static boolean announced;

    private ArmorBakeReuse() {}

    /** ForgeHooksClient.getArmorModel, wrapped: counts the lookup's depth on the render thread. */
    public static Model armorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> fallback, Operation<Model> original) {
        if (!enabled || !RenderSystem.isOnRenderThread()) return original.call(entity, stack, slot, fallback);
        depth++;
        try {
            return original.call(entity, stack, slot, fallback);
        } finally {
            depth--;
        }
    }

    /** True inside an armour model lookup on the render thread. */
    public static boolean active() {
        return depth > 0 && enabled && RenderSystem.isOnRenderThread();
    }

    /** EntityModelSet.bakeLayer inside an armour lookup: the memo's tree for the layer, baked once. */
    public static ModelPart bake(Map<ModelLayerLocation, ModelPart> memo, ModelLayerLocation layer, Operation<ModelPart> original) {
        ModelPart cached = memo.get(layer);
        if (cached != null) {
            REUSED.incrementAndGet();
            return cached;
        }
        ModelPart baked = original.call(layer);
        if (layer != null && baked != null) {
            memo.put(layer, baked);
            STORED.incrementAndGet();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_armor_layer_bake_reuse applies (armour models built per call reuse their baked layers until the next resource reload)");
            }
        }
        return baked;
    }

    /** Test hook (harness only): enter/leave an armour lookup without Forge's call. */
    public static void enterForTest() {
        depth++;
    }

    public static void leaveForTest() {
        depth--;
    }
}
