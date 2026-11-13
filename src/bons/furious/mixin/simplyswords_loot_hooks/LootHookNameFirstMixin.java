package bons.furious.mixin.simplyswords_loot_hooks;

import bons.furious.patch.simplyswords_loot_hooks.LootHookNames;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.architectury.event.events.common.LootEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.sweenus.simplyswords.util.ModLootTableModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * simplyswords_loot_table_name_first (Simply Swords 1.56.0-1.20.1, Timefall Development License; both sides).
 *
 * Simply Swords registers four loot table hooks (ModLootTableModifiers.init, lambda$init$0..3). Each runs for every loot
 * table that loads and starts with Config.getBoolean("enableLootDrops", "Loot", ...), which reads the Loot config file
 * from disk, and only then looks at the table's name. Hooks 0-2 do nothing unless the path contains "chests" and not
 * "spectrum"; hook 3 does nothing unless Simply Swords' built-in loot list names the table or the same name test holds.
 * The wrapper around that first getBoolean answers false, without the read, for a table no branch of the hook can act
 * on: the hook's condition is false either way, so the same pools are added to the same tables. Every other table runs
 * the original call. The wrappers around the hooks' addPool calls change nothing: they count the pools and, with the
 * verify flag, check each one against the name test (LootHookNames.pool). No Simply Swords code is carried.
 */
@Mixin(value = ModLootTableModifiers.class, remap = false)
public abstract class LootHookNameFirstMixin {
    @WrapOperation(method = {"lambda$init$0", "lambda$init$1", "lambda$init$2"}, require = 3, allow = 3, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/sweenus/simplyswords/config/Config;getBoolean(Ljava/lang/String;Ljava/lang/String;Z)Z"))
    private static boolean bons$chestHooks(String key, String parent, boolean fallback, Operation<Boolean> original,
                                           @Local(argsOnly = true) ResourceLocation id) {
        if (LootHookNames.ON && !LootHookNames.chestTable(id) && LootHookNames.ACTIVE) return false;
        return original.call(key, parent, fallback);
    }

    @WrapOperation(method = "lambda$init$3", require = 1, allow = 1, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/sweenus/simplyswords/config/Config;getBoolean(Ljava/lang/String;Ljava/lang/String;Z)Z"))
    private static boolean bons$uniqueHook(String key, String parent, boolean fallback, Operation<Boolean> original,
                                           @Local(argsOnly = true) ResourceLocation id) {
        if (LootHookNames.ON && !LootHookNames.uniqueTable(id) && LootHookNames.ACTIVE) return false;
        return original.call(key, parent, fallback);
    }

    @WrapOperation(method = {"lambda$init$0", "lambda$init$1", "lambda$init$2"}, require = 3, allow = 3, at = @At(value = "INVOKE",
            target = "Ldev/architectury/event/events/common/LootEvent$LootTableModificationContext;addPool(Lnet/minecraft/world/level/storage/loot/LootPool$Builder;)V"))
    private static void bons$chestPool(LootEvent.LootTableModificationContext context, LootPool.Builder pool, Operation<Void> original,
                                       @Local(argsOnly = true) ResourceLocation id) {
        LootHookNames.pool(id, false);
        original.call(context, pool);
    }

    @WrapOperation(method = "lambda$init$3", require = 2, allow = 2, at = @At(value = "INVOKE",
            target = "Ldev/architectury/event/events/common/LootEvent$LootTableModificationContext;addPool(Lnet/minecraft/world/level/storage/loot/LootPool$Builder;)V"))
    private static void bons$uniquePool(LootEvent.LootTableModificationContext context, LootPool.Builder pool, Operation<Void> original,
                                        @Local(argsOnly = true) ResourceLocation id) {
        LootHookNames.pool(id, true);
        original.call(context, pool);
    }
}
