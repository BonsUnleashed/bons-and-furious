package bons.furious.mixin.l2hostility_plate_c49;

import bons.furious.patch.l2hostility_plate_c49.NamePlateSight;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.client.event.RenderNameTagEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * l2hostility_name_plate_hover_direct (L2 Hostility 2.5.19, client): a @Redirect of the RayTraceUtil.rayTraceEntity call in
 * ClientEvents.renderNamePlate (capturing the RenderNameTagEvent argument): NamePlateSight.trace answers null when the
 * hovered-mob trace cannot hit (the look ray stays clear of the mob's grown box), else runs the shipped search itself. Kind
 * change, off by default: mobs on the look ray are traced about 5 percent slower (see NamePlateSight). Our own logic only
 * (All Rights Reserved target; none of its code is carried).
 */
@Mixin(targets = "dev.xkmc.l2hostility.events.ClientEvents", remap = false)
public abstract class NamePlateHoverMixin {
    @Redirect(method = "renderNamePlate(Lnet/minecraftforge/client/event/RenderNameTagEvent;)V",
            at = @At(value = "INVOKE", target = "Ldev/xkmc/l2library/util/raytrace/RayTraceUtil;rayTraceEntity(Lnet/minecraft/world/entity/player/Player;DLjava/util/function/Predicate;)Lnet/minecraft/world/phys/EntityHitResult;"),
            require = 1, allow = 1)
    private static EntityHitResult bons$l2hostility_plate_c49Sight(Player player, double reach, Predicate<Entity> pred, RenderNameTagEvent event) {
        return NamePlateSight.trace(player, reach, pred, event.getEntity());
    }
}
