package bons.furious.mixin.integratedvillages_coo49;

import bons.furious.patch.integratedvillages_coo49.WorkstationPicks;
import com.craisinlord.integrated_villages.world.processors.WorkstationProcessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * integratedvillages_workstation_pick_local (Integrated Villages 1.3.2+1.20.1-forge, server side; no Integrated Villages
 * code is carried). WorkstationProcessor.processBlock (m_7382_): the RandomSource.create() call that opens a placement and
 * every read of the processor's outputBlocksString field are wrapped, so one placement builds its candidates in this
 * thread's own list instead of the field shared by every placement and every world generation thread. Same candidates,
 * same uniform pick with the mod's own generator; no growth, no race. See WorkstationPicks.
 */
@Mixin(value = WorkstationProcessor.class, remap = false)
public abstract class WorkstationPickLocalMixin {
    @WrapOperation(method = "m_7382_", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;m_216327_()Lnet/minecraft/util/RandomSource;"),
            require = 1, allow = 1)
    private RandomSource bons$integratedvillagesBeginPick(Operation<RandomSource> original) {
        return WorkstationPicks.begin(original);
    }

    @WrapOperation(method = "m_7382_", at = @At(value = "FIELD",
            target = "Lcom/craisinlord/integrated_villages/world/processors/WorkstationProcessor;outputBlocksString:Ljava/util/ArrayList;",
            opcode = org.objectweb.asm.Opcodes.GETFIELD), require = 1)
    private ArrayList<String> bons$integratedvillagesCandidates(WorkstationProcessor processor, Operation<ArrayList<String>> original) {
        return WorkstationPicks.candidates(processor, original);
    }
}
