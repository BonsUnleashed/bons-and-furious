package bons.furious.mixin.simplyswords_c2;

import bons.furious.patch.simplyswords_c2.ConfigFetchMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.architectury.event.events.common.LootEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.sweenus.simplyswords.util.ModLootTableModifiers;
import org.spongepowered.asm.mixin.Mixin;

/**
 * simplyswords_loot_hook_config_scope (Simply Swords 1.56.0-1.20.1, Timefall Development License; both sides): MixinExtras
 * @WrapMethod around Simply Swords' four loot-table hooks (ModLootTableModifiers.lambda$init$0..3). Each hook call runs inside
 * a ConfigFetchMemo scope (switch simplyswords_config_unchanged_reuse, which this one needs): the first config read of the
 * call reads the Loot file as before, the later ones (the unique-weapon hook asks about 35 values per chest table) reuse what
 * that read found. The hook itself always runs, unchanged. No Simply Swords code is carried: our own logic only.
 */
@Mixin(value = ModLootTableModifiers.class, remap = false)
public abstract class LootHookConfigScopeMixin {
    @WrapMethod(method = {"lambda$init$0", "lambda$init$1", "lambda$init$2", "lambda$init$3"})
    private static void bons$lootScope(float weight, LootDataManager tables, ResourceLocation id, LootEvent.LootTableModificationContext context,
                                       boolean builtin, Operation<Void> original) {
        if (!ConfigFetchMemo.enabled) {
            original.call(weight, tables, id, context, builtin);
            return;
        }
        ConfigFetchMemo.enterScope();
        try {
            original.call(weight, tables, id, context, builtin);
        } finally {
            ConfigFetchMemo.exitScope();
        }
    }
}
