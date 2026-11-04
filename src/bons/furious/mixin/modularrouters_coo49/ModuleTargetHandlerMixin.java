package bons.furious.mixin.modularrouters_coo49;

import bons.furious.patch.modularrouters_coo49.RouterHandlerFirst;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.desht.modularrouters.logic.ModuleTarget;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * modularrouters_cached_handler_first (Modular Routers 12.1.1+mc1.20.1, server; kind opt, default on; no Modular Routers
 * code carried). ModuleTarget.getItemHandler(): the world lookup (MiscUtil.getWorldForGlobalPos) is left out while the
 * remembered item handler is present; getItemHandlerFor then answers that handler exactly as it would have with the world.
 * See RouterHandlerFirst.
 */
@Mixin(value = ModuleTarget.class, remap = false)
public abstract class ModuleTargetHandlerMixin {
    @Shadow
    private LazyOptional<IItemHandler> cachedItemCap;

    @WrapOperation(method = "getItemHandler()Lnet/minecraftforge/common/util/LazyOptional;",
            at = @At(value = "INVOKE", target = "Lme/desht/modularrouters/util/MiscUtil;getWorldForGlobalPos(Lnet/minecraft/core/GlobalPos;)Lnet/minecraft/server/level/ServerLevel;"),
            require = 1, allow = 1)
    private ServerLevel bons$modularroutersHandlerFirst(GlobalPos pos, Operation<ServerLevel> original) {
        if (RouterHandlerFirst.enabled && this.cachedItemCap.isPresent()) {
            RouterHandlerFirst.acted();
            return null;
        }
        return original.call(pos);
    }
}
