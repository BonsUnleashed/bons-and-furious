package bons.furious.patch.fallentraits_coo49;

/**
 * Per-entity memo of Fallen Traits' SizeTrait.getScale for fallentraits_render_scale_frame_memo, added to LivingEntity by
 * the group's FtScaleMemoMixin (only while the switch is on). The tick stamp is the entity's tickCount + 1 when the scale
 * was taken (0 = none).
 */
public interface FtScaleMemo {
    int bons$fallentraitsCoo49ScaleTick();

    float bons$fallentraitsCoo49Scale();

    void bons$fallentraitsCoo49KeepScale(int tick, float scale);
}
