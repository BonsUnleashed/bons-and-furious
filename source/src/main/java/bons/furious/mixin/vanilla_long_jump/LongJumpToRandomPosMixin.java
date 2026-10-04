package bons.furious.mixin.vanilla_long_jump;

import bons.furious.patch.vanilla_long_jump.LongJumpPicks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.LongJumpToRandomPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_long_jump_weighted_pick (Minecraft 1.21.1 on NeoForge 21.1.252, server side incl. the integrated server):
 * LongJumpToRandomPos.getJumpCandidate, the weighted draw plus removal that goats' and (through super) frogs' long-jump
 * target search repeats candidate by candidate, is answered by LongJumpPicks' Fenwick trees for the two vanilla classes
 * (same draw, same candidate, same list afterwards; see LongJumpPicks). Other classes extending LongJumpToRandomPos, and
 * every call while the switch is off, run the original method. In the 1.21.1 target set only Radium 0.13.1 targets
 * this class: its ai.task.run.long_jump_weighted_choice (on by default) makes the same change (a cancel in start and two
 * redirects in getJumpCandidate), so the switch yields to it (patches/vanilla_long_jump.json "yields").
 * Ported to 1.21.1: Javadoc only; getJumpCandidate(ServerLevel) and the jumpCandidates field are unchanged.
 */
@Mixin(value = LongJumpToRandomPos.class, remap = false)
public abstract class LongJumpToRandomPosMixin implements LongJumpPicks.Holder {
    @Shadow
    protected List<LongJumpToRandomPos.PossibleJump> jumpCandidates;
    @Unique
    private LongJumpPicks.Picker bons$picker;

    @WrapMethod(method = "getJumpCandidate")
    private Optional<LongJumpToRandomPos.PossibleJump> bons$weightedPick(ServerLevel level, Operation<Optional<LongJumpToRandomPos.PossibleJump>> original) {
        return LongJumpPicks.pick(this, this, level, original);
    }

    @Override
    public List<LongJumpToRandomPos.PossibleJump> bons$candidates() {
        return this.jumpCandidates;
    }

    @Override
    public LongJumpPicks.Picker bons$picker() {
        return this.bons$picker;
    }

    @Override
    public void bons$picker(LongJumpPicks.Picker picker) {
        this.bons$picker = picker;
    }
}
