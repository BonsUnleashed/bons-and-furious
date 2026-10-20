package bons.furious.mixin.reliquary_charm;

import bons.furious.patch.reliquary_charm.CharmTypeMemo;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * reliquary_mob_charm_type_gate (Minecraft 1.20.1, server, since 1.0.46): one int on EntityType where ReliquaryCharmGate
 * keeps whether Reliquary has a mob-charm definition for that type (and the registry version that answer belongs to).
 * Carries no Minecraft code and changes no behaviour; nothing else reads it.
 */
@Mixin(value = EntityType.class, remap = false)
public abstract class EntityTypeCharmMemoMixin implements CharmTypeMemo {
    @Unique
    private int bons$charmMemoValue;

    @Override
    public int bons$charmMemo() {
        return this.bons$charmMemoValue;
    }

    @Override
    public void bons$charmMemo(int memo) {
        this.bons$charmMemoValue = memo;
    }
}
