package agentcraft.pure.more;
import agentcraft.bioengineering.*;
import agentcraft.pure.mixin.RecipeManagerAccess;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;

/** Read the current native type index only when selection can contain final IndustryRecipe objects. */
public final class IndustryLists {
    private static final Comparator<IndustryRecipe> ORDER=Comparator.comparing(r->r.id.toString());
    public static List<BiologyProcess> tryList(RecipeManager manager,Industry.Kind kind) {
        if(kind==null||kind==Industry.Kind.ASSEMBLER||kind==Industry.Kind.CHEMICAL||kind==Industry.Kind.CENTRIFUGE||kind==Industry.Kind.REFINERY||manager.getClass()!=RecipeManager.class)return null;
        Map<?,?> indexed=((RecipeManagerAccess)manager).ac$recipesByType(Industry.RECIPE.get());
        var selected=new ArrayList<IndustryRecipe>();
        for(var entry:indexed.entrySet()) {
            if(!(entry.getValue() instanceof IndustryRecipe r)||r.id==null||r.id.getClass()!=ResourceLocation.class||!r.id.equals(entry.getKey()))return null;
            // IndustryRecipe is final, identity-equal, and always reports this recipe type.
            // Unfamiliar entries use the original full collection.
            if(r.machine==kind||(kind.vat()&&r.machine.vat()&&r.machine.tier<=kind.tier))selected.add(r);
        }
        selected.sort(ORDER);
        // Installed ResourceLocation also has mutable lookup setters and a cached toString.
        // Guard textual ties explicitly: their original HashSet encounter order is significant.
        if(selected.size()>1){String previous=selected.get(0).id.toString();for(int i=1;i<selected.size();i++){String current=selected.get(i).id.toString();if(previous.equals(current))return null;previous=current;}}
        return selected.stream().map(BiologyProcess::new).toList();
    }
}
