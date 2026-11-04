package bons.furious.mixin.lootr_c49;

import bons.furious.patch.lootr_c49.LootrTickerMemo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import noobanidus.mods.lootr.block.entities.TileTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * lootr_ticker_blocker_memo (Lootr 0.7.35.94, MIT; server side): two call-site wraps in TileTicker.serverTick. Entry.age
 * marks the entry whose chunk tests follow; the own-chunk hasChunk (ordinal 0) still runs as Lootr makes it, and for a
 * chest that is already waiting LootrTickerMemo answers "wait" when one of Lootr's own chunk tests fails now (the
 * remembered one, or the first one found). Lootr's area and LOADED_CHUNKS loops are not touched, so a chest that converts on
 * its first evaluation pays only these two wraps. Why the decisions equal Lootr's own: LootrTickerMemo.
 */
@Mixin(value = TileTicker.class, remap = false)
public abstract class TileTickerMemoMixin {
    @WrapOperation(method = "serverTick(Lnet/minecraftforge/event/TickEvent$ServerTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnoobanidus/mods/lootr/block/entities/TileTicker$Entry;age(Lnet/minecraft/server/MinecraftServer;)J"),
            require = 1, allow = 1)
    private static long bons$lootrC49Age(TileTicker.Entry entry, MinecraftServer server, Operation<Long> original) {
        LootrTickerMemo.entry(entry);
        return original.call(entry, server);
    }

    @WrapOperation(method = "serverTick(Lnet/minecraftforge/event/TickEvent$ServerTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;m_5563_(II)Z", ordinal = 0),
            require = 1, allow = 1)
    private static boolean bons$lootrC49OwnChunk(ServerChunkCache cache, int x, int z, Operation<Boolean> original) {
        return LootrTickerMemo.ownChunk(cache, original.call(cache, x, z));
    }
}
