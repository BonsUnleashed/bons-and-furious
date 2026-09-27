package agentcraft.pure.mixin;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeManager.class)
public interface RecipeManagerAccess {
    @Invoker(value="m_44054_",remap=false)
    <C extends Container,T extends Recipe<C>> Map<ResourceLocation,T> ac$recipesByType(RecipeType<T> type);
}
