package agentcraft.pure.mixin;
import agentcraft.pure.UnchangedMerge;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
@Mixin(value=DataCommands.class,remap=false)
public abstract class DataCommandsMixin {
    @Shadow @Final private static SimpleCommandExceptionType f_139352_;
    @Inject(method="m_139394_",at=@At(value="INVOKE",target="Lnet/minecraft/nbt/CompoundTag;m_6426_()Lnet/minecraft/nbt/CompoundTag;"),locals=LocalCapture.CAPTURE_FAILHARD,require=1)
    private static void ac$unchanged(CommandSourceStack source, DataAccessor accessor, CompoundTag update,
            CallbackInfoReturnable<Integer> callback, CompoundTag current) throws CommandSyntaxException {
        // Serialization and vanilla's depth validation have already happened.
        if(UnchangedMerge.matches(current,update)) throw f_139352_.create();
    }
}
