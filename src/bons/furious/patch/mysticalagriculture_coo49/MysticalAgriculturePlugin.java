package bons.furious.patch.mysticalagriculture_coo49;

import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the mysticalagriculture_coo49 group (since 1.0.49). Everything is Bons and Furious' standard plugin
 * (switches, guard table, yields, log lines); in addition, after each of mysticalagriculture_soul_extractor_idle_skip's three
 * probe mixins has been applied (they have priority 3000, so every other mod's mixin on the class is already there), MaProbe
 * records whether the methods the switch relies on are still as shipped. Same delegation pattern as the cooking recipe index
 * group's RecipeIndexPlugin.
 */
public final class MysticalAgriculturePlugin implements IMixinConfigPlugin {
    private static final String PKG = "bons.furious.mixin.mysticalagriculture_coo49.";
    private static final Set<String> PROBES = Set.of(PKG + "RecipeManagerProbeMixin", PKG + "IngredientProbeMixin", PKG + "SoulRecipeProbeMixin");
    private final PureMixinPlugin delegate = new PureMixinPlugin();

    @Override
    public void onLoad(String mixinPackage) {
        this.delegate.onLoad(mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return this.delegate.getRefMapperConfig();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.delegate.shouldApplyMixin(targetClassName, mixinClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        this.delegate.acceptTargets(myTargets, otherTargets);
    }

    @Override
    public List<String> getMixins() {
        return this.delegate.getMixins();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        this.delegate.preApply(targetClassName, targetClass, mixinClassName, mixinInfo);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        this.delegate.postApply(targetClassName, targetClass, mixinClassName, mixinInfo);
        if (PROBES.contains(mixinClassName)) MaProbe.record(mixinClassName.substring(PKG.length()), targetClass);
        if (SCAN.equals(mixinClassName))
            census(targetClass, "check", "(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Z", "bons$mysticalagricultureSoulScan",
                    "mysticalagriculture_soul_extractor_idle_skip steps aside: the recipe look-up in Cucumber's CachedRecipe.check is changed by another mod; the Soul Extractor's recipe look-up runs as shipped");
        if (CROP.equals(mixinClassName))
            census(targetClass, "getDisplayName", "()Lnet/minecraft/network/chat/Component;", "bons$mysticalagricultureCropName",
                    "mysticalagriculture_crop_name_key_memo steps aside: the String.format call in Mystical Agriculture's Crop.getDisplayName is changed by another mod; crop names are formatted as shipped");
    }

    private static final String SCAN = PKG + "SoulRecipeScanMixin", CROP = PKG + "CropNameKeyMixin";

    /**
     * The soul switch's hook and the crop switch's redirect have require = 0: when another mod already changed the call they
     * sit at, Mixin leaves ours out and the method runs as shipped. One INFO line says so.
     */
    private static void census(ClassNode target, String method, String desc, String handler, String line) {
        try {
            for (MethodNode m : target.methods) {
                if (!m.name.equals(method) || !m.desc.equals(desc)) continue;
                for (AbstractInsnNode n : m.instructions)
                    if (n instanceof MethodInsnNode mi && mi.name.endsWith(handler)) return;
            }
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: " + line);
        } catch (Throwable t) {
            // the census only logs
        }
    }
}
