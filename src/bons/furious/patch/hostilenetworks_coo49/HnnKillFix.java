package bons.furious.patch.hostilenetworks_coo49;

import dev.shadowsoffire.hostilenetworks.data.DataModel;
import dev.shadowsoffire.hostilenetworks.data.ModelTier;
import dev.shadowsoffire.hostilenetworks.item.DataModelItem;
import dev.shadowsoffire.hostilenetworks.item.DeepLearnerItem;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch hostilenetworks_unbound_model_kill_fix (since 1.0.49; Hostile Neural Networks 5.3.3; server).
 * Helper of HnnUnboundModelKillMixin. Hostile Neural Networks is MIT licensed; the corrected loop below repeats its four
 * steps per slot with the brackets of its evident intent.
 *
 * The bug. HostileEvents.updateModels(learner, type, bonus), run for every deep learner of a player who kills something,
 * skips a slot when "model.isEmpty() || (!holder.isBound() || holder.get().type() != type) && !holder.get().subtypes()
 * .contains(type)". Java reads that as empty || ((unbound || mismatch) && !subtypes.contains), so a data model whose id is
 * no longer registered (a removed mob mod, a renamed datapack model) reaches holder.get(), which throws
 * NullPointerException ("Trying to access unbound value") out of LivingDeathEvent on every kill.
 *
 * The fix. Only when some non-empty slot of the learner holds an unbound model, updateModels runs the corrected loop and
 * returns: per slot skip when empty, unbound, or (type differs and the subtypes do not contain it); otherwise the same
 * getData, ModelTier.getByData, setData(data + getDataPerKill(tier) + bonus) as the original; then the same saveItems. For
 * a bound model both readings give the same decision, so a learner without unbound models runs the original method
 * unchanged (the check reads the slots through a separate handler copy, DeepLearnerItem.getItemHandler deserialises a new
 * one every call). Before: the kill throws and the learner keeps its old data. After: the unbound slot is left alone and
 * the other slots gain their data. -Dbons_and_furious.hostilenetworksUnboundModelKillFix=false runs the original.
 */
public final class HnnKillFix {
    /** hostilenetworks_unbound_model_kill_fix: -Dbons_and_furious.hostilenetworksUnboundModelKillFix=false runs the original. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.hostilenetworksUnboundModelKillFix", "true"));
    /** Corrected runs (offline proof and probes). */
    public static long corrected;
    private static volatile boolean announced;

    private HnnKillFix() {
    }

    /** True when the learner was handled here (it holds an unbound model); false = run the original. */
    public static boolean handled(ItemStack learner, EntityType<?> type, int bonus) {
        if (!enabled) return false;
        ItemStackHandler handler = DeepLearnerItem.getItemHandler(learner);
        boolean unbound = false;
        for (int i = 0; i < 4 && !unbound; i++) {
            ItemStack model = handler.getStackInSlot(i);
            unbound = !model.m_41619_() && !DataModelItem.getStoredModel(model).isBound();
        }
        if (!unbound) return false;
        if (!announced) {
            announced = true;
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: hostilenetworks_unbound_model_kill_fix: a deep learner holds a data model whose id is not registered; it is skipped instead of failing the kill");
        }
        for (int i = 0; i < 4; i++) {
            ItemStack model = handler.getStackInSlot(i);
            if (model.m_41619_()) continue;
            DynamicHolder<DataModel> holder = DataModelItem.getStoredModel(model);
            if (!holder.isBound()) continue;
            DataModel m = holder.get();
            if (m.type() != type && !m.subtypes().contains(type)) continue;
            int data = DataModelItem.getData(model);
            ModelTier tier = ModelTier.getByData(holder, data);
            DataModelItem.setData(model, data + holder.get().getDataPerKill(tier) + bonus);
        }
        DeepLearnerItem.saveItems(learner, handler);
        corrected++;
        return true;
    }
}
