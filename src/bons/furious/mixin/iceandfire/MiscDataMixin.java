package bons.furious.mixin.iceandfire;

import com.github.alexthe666.iceandfire.entity.props.MiscData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * iceandfire_lazy_reference_lists, scepter half (Ice and Fire 2.1.13-beta-5).
 *
 * Every living entity carries a MiscData, and almost none of them is ever targeted by a scepter. initialize() built an
 * ArrayList on each entity's first tick only to drop it again when it stayed empty. The list is now created only when
 * a target is actually found; the resulting targetedByScepter value is the same as before (null when there are none).
 */
@Mixin(value = MiscData.class, remap = false)
public abstract class MiscDataMixin {
    @Shadow
    public List<LivingEntity> targetedByScepter;
    @Shadow
    private List<Integer> targetedByScepterIds;
    @Shadow
    private boolean isInitialized;

    /**
     * @author BonsUnleashed
     * @reason Create the scepter target list only when a target is actually found (null stays null).
     */
    @Overwrite
    private void initialize(Level level) {
        ArrayList<LivingEntity> targets = null;
        if (this.targetedByScepterIds != null) {
            for (int id : this.targetedByScepterIds) {
                // 'found' is never read: the 1.0.19 build kept the lookup in a local, and this keeps its bytecode.
                Entity found;
                if (id == -1 || !((found = level.m_6815_(id)) instanceof LivingEntity living)) {
                    continue;
                }
                if (targets == null) {
                    targets = new ArrayList<>();
                }
                targets.add(living);
            }
        }
        this.targetedByScepter = targets;
        this.targetedByScepterIds = null;
        this.isInitialized = true;
    }
}
