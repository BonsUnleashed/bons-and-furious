package bons.furious.patch.mahoutsukai_coo49;

import java.util.Arrays;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stepsword.mahoutsukai.item.ModItems;
import stepsword.mahoutsukai.potion.ModEffects;

/**
 * Bons and Furious switches mahoutsukai_ripper_effect_first (both sides, exact) and mahoutsukai_authority_client_skip
 * (client, exact result; off by default since the 1.0.49 speed gate: in single player the integrated server's side pays
 * the side test) for Mahou Tsukai 1.21.1-v1.36.31 (All Rights Reserved: no Mahou Tsukai code is carried; the hooks hold our
 * own logic only). Idea: Collections Of Optimizations (mahoutsukai.leanRipperTick, mahoutsukai.skipClientAuthorityTick),
 * idea text only.
 *
 * Mahou Tsukai's ServerHandler.livingEvent (an EntityTickEvent.Pre listener) calls sixteen effect handlers for every living
 * entity on every tick on both sides. Two of them do work before the test that decides whether they can do anything at all:
 *
 *  TheRipper.ripperLivingTick(living): "living != null && (main hand item != off hand item || main hand item != the
 *  Ripper) && EffectUtil.hasBuff(living, RIPPER_INVISIBILITY)" guards its whole body (remove the effect, tell the
 *  trackers). The first two parts only read (the hand stacks, their items, a registry holder); when the effect is absent
 *  the whole condition is false whatever they answer, and when both hands hold the Ripper it is false whatever the effect.
 *  ripper() decides with the same reads in the cheaper order: no effect -> the handler's parameter becomes null at its HEAD
 *  (MahouRipperFirstMixin, @ModifyVariable), so its opening null test ends it, exactly where the original ends; the effect
 *  and the Ripper in both hands -> null as well (the original ends after its hand reads); the effect without that -> the
 *  parameter is unchanged and the handler runs as shipped (it removes the effect). The effect is asked the way
 *  EffectUtil.hasBuff asks it (LivingEntity.hasEffect with the same ModEffects holder). The id of an entity found with the
 *  Ripper in both hands is remembered (a 64-slot open-addressing int set, a hint for the order of the checks only, never
 *  for an answer; users whose ids share a home slot sit side by side): on its next ticks the hands are looked at first,
 *  so the Ripper's own user costs two hand reads instead of the shipped three and no effect read; once its hands change
 *  it is forgotten and decided like any other entity. The hand getters are read fewer times than as shipped, as on every
 *  entity this switch passes over (they are plain getters).
 *
 *  BorrowedAuthorityPotion.authorityLivingUpdate(entity): reads the living_mahou data attachment (Utils.getLivingMahou:
 *  entity.getData(LIVING_ATTACHMENTS)), its getAuthorityHit() and getDeltaMovement(), and then does everything it does
 *  inside "if (!entity.level().isClientSide)". On a client level it therefore only reads; the one write the read path can
 *  cause is the attachment's creation on its first read (AttachmentType.builder(LivingMahou::new): a fresh default object
 *  with no side effect in its constructor; the type has no sync handler), which then simply happens at the attachment's
 *  next real use. authority(): on a client level the parameter becomes null at the HEAD (MahouAuthorityClientMixin,
 *  client dist only), so the handler's opening null test ends it; server levels and a null entity are passed on unchanged.
 *
 * Runtime flags: -Dbons_and_furious.mahoutsukaiRipperEffectFirst=false, -Dbons_and_furious.mahoutsukaiAuthorityClientSkip=false.
 */
public final class MahouTicks {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean ripperEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mahoutsukaiRipperEffectFirst", "true"));
    public static volatile boolean authorityEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mahoutsukaiAuthorityClientSkip", "true"));
    /** For probes and harnesses (plain fields): handler calls ended at their HEAD (no effect; the Ripper in both hands). */
    public static long ripperSkipped, ripperHeldSkipped, authoritySkipped;
    private static volatile boolean ripperAnnounced, authorityAnnounced;
    private static final int NOBODY = Integer.MIN_VALUE, GONE = Integer.MIN_VALUE + 1, SIZE = 64, MASK = SIZE - 1;
    /**
     * The ids of entities found holding the Ripper in both hands: an open-addressing set (home slot id & 63, linear probing,
     * GONE marks a forgotten id), so users whose ids share a home slot sit side by side and never push each other out. A
     * hint for the order of the checks only, never for an answer, so plain ints suffice (no reference kept, any thread may
     * write: a lost, stale or doubled entry only changes which check comes first; the client and server copies of one
     * player in single player share their id and entry). Only a 65th user at the same time finds the set full: it is then
     * emptied once and the users enter again on their next tick.
     */
    private static final int[] HELD = new int[SIZE];

    static {
        Arrays.fill(HELD, NOBODY);
    }

    private MahouTicks() {
    }

    /** Is the id in the set? The home slot answers almost every call (empty or the id itself). */
    private static boolean held(int id) {
        int s = id & MASK, v = HELD[s];
        if (v == id) return true;
        if (v == NOBODY) return false;
        for (int i = 1; i < SIZE; i++) {
            v = HELD[s = (s + 1) & MASK];
            if (v == id) return true;
            if (v == NOBODY) return false;
        }
        return false;
    }

    private static void hold(int id) {
        int s = id & MASK, free = -1;
        for (int i = 0; i < SIZE; i++, s = (s + 1) & MASK) {
            int v = HELD[s];
            if (v == id) return;
            if (v == GONE) {
                if (free < 0) free = s;
            } else if (v == NOBODY) {
                HELD[free < 0 ? s : free] = id;
                return;
            }
        }
        if (free >= 0) {
            HELD[free] = id;
            return;
        }
        Arrays.fill(HELD, NOBODY);
        HELD[id & MASK] = id;
    }

    private static void forget(int id) {
        int s = id & MASK;
        for (int i = 0; i < SIZE; i++, s = (s + 1) & MASK) {
            int v = HELD[s];
            if (v == id) {
                HELD[s] = GONE;
                return;
            }
            if (v == NOBODY) return;
        }
    }

    /**
     * MahouRipperFirstMixin: the ripper handler's parameter, or null when the shipped handler would do nothing: the entity
     * lacks RIPPER_INVISIBILITY, or both its hands hold the Ripper.
     */
    public static LivingEntity ripper(LivingEntity living) {
        if (!ripperEnabled || living == null) return living;
        int id = living.getId();
        boolean hinted = held(id);
        if (hinted) {
            if (bothRipper(living)) {
                ripperHeldSkipped++;
                return null;
            }
            forget(id);
        }
        if (!living.hasEffect(ModEffects.RIPPER_INVISIBILITY)) {
            ripperSkipped++;
            if (!ripperAnnounced) {
                ripperAnnounced = true;
                LOGGER.info("Bons and Furious: mahoutsukai_ripper_effect_first applies (the Ripper's living tick returns at once for entities without its invisibility effect)");
            }
            return null;
        }
        if (!hinted && bothRipper(living)) {
            hold(id);
            ripperHeldSkipped++;
            return null;
        }
        return living;
    }

    /** The shipped handler's hand test: main hand item == off hand item and the main hand item is the Ripper. */
    private static boolean bothRipper(LivingEntity living) {
        Item main = living.getMainHandItem().getItem();
        return main == living.getOffhandItem().getItem() && main == ModItems.theripper.get();
    }

    /** MahouAuthorityClientMixin: the borrowed authority handler's parameter, or null on a client level. */
    public static LivingEntity authority(LivingEntity entity) {
        if (!authorityEnabled || entity == null) return entity;
        Level level = entity.level();
        if (level == null || !level.isClientSide) return entity;
        authoritySkipped++;
        if (!authorityAnnounced) {
            authorityAnnounced = true;
            LOGGER.info("Bons and Furious: mahoutsukai_authority_client_skip applies (the borrowed authority living tick returns at once on the client)");
        }
        return null;
    }
}
