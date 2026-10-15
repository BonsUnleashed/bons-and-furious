package bons.furious.patch.benssharks;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * benssharks_tick_listeners: one of Ben's Sharks' original LivingTickEvent handlers, called directly (the original static
 * method, unchanged) for the Ben's entities its guard can accept. The domain is a superset of the handler's own guard, so the
 * handler still decides; it is only no longer called for every other living entity in the world.
 */
public final class Proc {
    public final String cls;
    public final Consumer<LivingEvent.LivingTickEvent> run;
    private final Class<?>[] classes;      // instanceof domain (generated from bytecode)
    private final Class<?> idsOwner;        // registry-id list domain: owner class + field
    private final String idsField;
    private final boolean all;
    private volatile Set<String> ids;       // resolved from the original private static list

    private Proc(String cls, Consumer<LivingEvent.LivingTickEvent> run, Class<?>[] classes, Class<?> idsOwner,
                 String idsField, boolean all) {
        this.cls = cls;
        this.run = run;
        this.classes = classes;
        this.idsOwner = idsOwner;
        this.idsField = idsField;
        this.all = all;
    }

    public static Proc classes(String cls, Consumer<LivingEvent.LivingTickEvent> run, Class<?>... classes) {
        return new Proc(cls, run, classes, null, null, false);
    }

    public static Proc ids(String cls, Consumer<LivingEvent.LivingTickEvent> run, Class<?> owner, String field) {
        return new Proc(cls, run, null, owner, field, false);
    }

    public static Proc all(String cls, Consumer<LivingEvent.LivingTickEvent> run) {
        return new Proc(cls, run, null, null, null, true);
    }

    /** Reads the original registry-id list (exactly what the handler compares against). */
    @SuppressWarnings("unchecked")
    void resolve() throws ReflectiveOperationException {
        if (idsOwner == null) return;
        Field f = idsOwner.getDeclaredField(idsField);
        f.setAccessible(true);
        ids = new HashSet<>((List<String>) f.get(null));
    }

    boolean matches(LivingEntity e) {
        if (all) return true;
        if (classes != null) {
            for (Class<?> c : classes) if (c.isInstance(e)) return true;
            return false;
        }
        if (ids != null) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(e.m_6095_());
            return key != null && ids.contains(key.toString());
        }
        return false;
    }

    String domain() {
        if (all) return "all Ben's entities";
        if (classes != null) return classes.length + " classes";
        return idsField + "=" + (ids == null ? "?" : ids.size());
    }
}
