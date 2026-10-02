package bons.furious.patch.jei;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.registries.RegistryManager;
import org.slf4j.Logger;

/**
 * Bons and Furious switch jei_server_item_registry (JEI 15.59.0.212 + Forge 47.4.16, client). No JEI code here.
 *
 * JEI's ItemStackHelper.isIngredientOnServer(stack) - called for every stack of every recipe slot while JEI indexes recipes
 * - does Services.PLATFORM.getRegistry(Registries.ITEM).contains(item); getRegistry is
 * new RegistryWrapper(RegistryManager.ACTIVE.getRegistry(key.location())), a HashBiMap lookup and an allocation per stack.
 *
 * The wrapper is kept once Forge's active item registry exists and handed out again for that call. Identical because the
 * wrapper holds nothing but that ForgeRegistry, is used only for contains() and never escapes the call, and
 * RegistryManager.ACTIVE (a static final) never replaces or drops a registry: its registries map only gains entries
 * (createRegistry throws on a duplicate, getRegistry(name, other) adds only when absent), the field is never reassigned,
 * and the one method that clears it, clean(), has no caller in Forge 47.4.16 or in any installed jar (scanned). Registry
 * sync with a server changes the registry object in place, so contains() keeps answering for the current contents.
 * Nothing is kept while the active item registry is still missing (the original then fails the same way on its own).
 */
public final class ServerItemRegistry {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.jeiServerItemRegistry=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.jeiServerItemRegistry", "true"));
    /** Shadow mode for rigs: compare the kept wrapper's registry with the active one on every call (WARN on a difference). */
    public static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.jeiServerItemRegistry.verify");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Object kept;
    private static volatile Object keptPlatform;
    private static Field forgeRegistryField;
    /** Shadow-mode counters (read by probes). */
    public static long verified;
    public static long mismatches;

    private ServerItemRegistry() {
    }

    /** The kept wrapper for (platform, key), or null when the caller must run the original. */
    public static Object kept(Object platform, ResourceKey<?> key) {
        Object k = kept;
        if (k == null || platform != keptPlatform || key != Registries.f_256913_) return null;
        if (VERIFY) verify(k);
        return k;
    }

    /** After the original produced a wrapper: keep it when it is the item registry's and that registry exists. */
    public static void offer(Object platform, ResourceKey<?> key, Object wrapper) {
        if (wrapper == null || key != Registries.f_256913_ || kept != null) return;
        if (RegistryManager.ACTIVE.getRegistry(key.m_135782_()) == null) return;
        keptPlatform = platform;
        kept = wrapper;
        LOGGER.info("Bons and Furious: jei_server_item_registry keeps JEI's wrapper of the active item registry");
    }

    private static void verify(Object wrapper) {
        verified++;
        try {
            if (forgeRegistryField == null) {
                Field f = wrapper.getClass().getDeclaredField("forgeRegistry");
                f.setAccessible(true);
                forgeRegistryField = f;
            }
            if (forgeRegistryField.get(wrapper) != RegistryManager.ACTIVE.getRegistry(Registries.f_256913_.m_135782_()) && mismatches++ < 20) {
                LOGGER.warn("Bons and Furious: jei_server_item_registry shadow check: the active item registry is no longer the kept one");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (mismatches++ < 20) LOGGER.warn("Bons and Furious: jei_server_item_registry shadow check failed: {}", e.toString());
        }
    }
}
