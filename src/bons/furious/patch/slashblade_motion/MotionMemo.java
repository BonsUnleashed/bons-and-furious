package bons.furious.patch.slashblade_motion;

import com.google.common.eventbus.EventBus;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import jp.nyatla.nymmd.MmdMotionPlayer;
import jp.nyatla.nymmd.MmdMotionPlayerGL2;
import jp.nyatla.nymmd.MmdPmdModelMc;
import jp.nyatla.nymmd.MmdPmdModel_BasicClass;
import jp.nyatla.nymmd.MmdVmdMotionMc;
import jp.nyatla.nymmd.MmdVmdMotion_BasicClass;
import jp.nyatla.nymmd.core.PmdBone;
import jp.nyatla.nymmd.core.PmdFace;
import jp.nyatla.nymmd.types.MmdMatrix;
import jp.nyatla.nymmd.types.MmdVector3;
import org.slf4j.Logger;

/**
 * Bons and Furious switch slashblade_motion_update_memo (SlashBlade: Resharped 1.9.65, MIT; client only). Target classes:
 * the NyMMD library SlashBlade carries (jp.nyatla.nymmd). Not in our pack: public value.
 *
 * What SlashBlade does. SlashBlade animates players through PlayerAnimator with VmdAnimation, which drives one shared
 * MmdMotionPlayerGL2 with the alex.pmd model (about 530 KB of vertices). PlayerAnimator calls
 * VmdAnimation.get3DTransform for every body part and transform type of an animated player (body, head, both arms, both
 * legs, torso, bends, held items: about 20 calls per frame per player, twice with a shadow pass), and every call runs
 * setupAnim, i.e. MmdMotionPlayer.updateMotion(time) with the same time: all bones reset, keyframes interpolated, IK solved,
 * matrices rebuilt and every vertex of the model skinned again. LayerMainBlade (the blade in hand) drives its own player
 * with bladeholder.pmd the same way once per rendered entity, and idle wielders share one clamped time.
 *
 * What the switch does. updateMotion(time) is left out when it would repeat this player's last completed update: the
 * model's bones were last written by this player (a per-model stamp that every updateMotion call clears first, whoever
 * makes it - another player sharing the model, a subclass, or any call while the switch is off), and the motion object,
 * the time (float bits) and the skinning-matrix array are the ones of that update. Why the state that call would leave is
 * the state already there:
 * - With no face track mapped to a model face (MmdMotionPlayer.m_ppFaceList all null), updateMotion writes only absolute
 *   values: every bone is reset, then gets its keyframe position and rotation (MotionData.getMotionPosRot), its matrix
 *   (updateMatrix: all 16 entries), the IK passes start from that reset state with scratch values written before they are
 *   read, and the skinning matrices and GL2's vertex buffer are recomputed from the bones and the constant vertex arrays.
 *   Java floating point is deterministic, so the same inputs write the same bits again. A mapped face would blend vertex
 *   positions cumulatively (PmdFace.blendFace), so such a motion always runs the original.
 * - Only updateMotion writes that state: setPmd replaces the skinning-matrix array (part of the key), setVmd with another
 *   motion changes the motion (part of the key), and SlashBlade's readers (VmdAnimation.get3DTransform, LayerMainBlade)
 *   copy bone values and matrices out without writing them.
 * - The update's other effects are excluded: the look-at step (lookMeEnable, reads a target set from outside) and the two
 *   UpdateBoneEvent posts on the player's Guava EventBus run their original whenever the look-at is on or the bus has a
 *   subscriber (SlashBlade 1.9.65 registers none; with none, a post only allocates the event and a DeadEvent).
 * - Exact classes only (MmdMotionPlayerGL2, MmdPmdModelMc, MmdVmdMotionMc / MmdVmdMotion_BasicClass): a subclass may
 *   override what the update calls. A call that throws records nothing, so its repeat runs (and throws) again.
 *
 * -Dbons_and_furious.slashbladeMotionUpdateMemo=false switches it off at run time.
 * -Dbons_and_furious.slashbladeMotionUpdateMemo.shadow=true (verification runs only): a call the switch would leave out runs
 * anyway and its result is compared with the state the switch would have kept (bones, skinning matrices, GL2 vertex
 * buffer, vertex positions); SHADOW_CHECKS / SHADOW_MISMATCHES count it, at most 20 WARN lines.
 */
public final class MotionMemo {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.slashbladeMotionUpdateMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.slashbladeMotionUpdateMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced, warned;

    /**
     * Guava EventBus.subscribers (SubscriberRegistry) and SubscriberRegistry.subscribers (class -> subscriber set), read
     * through constant VarHandles (a plain field load once compiled); null when Guava's internals are not as expected.
     */
    private static final VarHandle BUS_REGISTRY, REGISTRY_MAP;
    /** The same two fields through core reflection, when the VarHandles cannot be had (module access). */
    private static final Field BUS_REGISTRY_FIELD, REGISTRY_MAP_FIELD;
    /** MmdMotionPlayerGL2._fbuf, for the shadow comparison only. */
    private static final Field GL2_BUFFER;

    static {
        VarHandle registry = null, map = null;
        Field registryField = null, mapField = null, buffer = null;
        try {
            Field r = EventBus.class.getDeclaredField("subscribers");
            Field m = r.getType().getDeclaredField("subscribers");
            if (Map.class.isAssignableFrom(m.getType())) {
                try {
                    registry = MethodHandles.privateLookupIn(EventBus.class, MethodHandles.lookup()).unreflectVarHandle(r);
                    map = MethodHandles.privateLookupIn(r.getType(), MethodHandles.lookup()).unreflectVarHandle(m);
                } catch (Throwable t) {
                    registry = map = null;
                    r.setAccessible(true);
                    m.setAccessible(true);
                    registryField = r;
                    mapField = m;
                }
            }
        } catch (Throwable t) {
            registry = map = null;
            registryField = mapField = null;
        }
        if (SHADOW) {
            try {
                buffer = MmdMotionPlayerGL2.class.getDeclaredField("_fbuf");
                buffer.setAccessible(true);
            } catch (Throwable t) {
                buffer = null;
            }
        }
        BUS_REGISTRY = registry;
        REGISTRY_MAP = map;
        BUS_REGISTRY_FIELD = registryField;
        REGISTRY_MAP_FIELD = mapField;
        GL2_BUFFER = buffer;
    }

    private MotionMemo() {
    }

    /**
     * Start of MmdMotionPlayer.updateMotion(float). True: the call repeats the player's last completed update and returns
     * at once. False: the call runs; it is recorded by tail if it returns normally and its inputs qualify.
     */
    public static boolean head(MmdMotionPlayer player, MotionMemoPlayer memo, float positionInMsec) {
        MmdPmdModel_BasicClass model = memo.bons$memoModel();
        if (!enabled || player.getClass() != MmdMotionPlayerGL2.class) {
            // any update rewrites the model's bones, also one the switch does not take part in (a subclass, or the switch
            // off): the model's stamp then belongs to nobody, so no player can answer a repeat from older bones
            if (model != null) {
                ((MotionMemoModel) model).bons$memoLastWriter(null);
            }
            memo.bons$memoPending(null, null, 0, null, null);
            return false;
        }
        MmdVmdMotion_BasicClass motion = memo.bons$memoMotion();
        MmdMatrix[] skin = player._skinning_mat;
        int time = Float.floatToRawIntBits(positionInMsec);
        boolean plain = plainInputs(player, memo, model, motion);
        Snapshot kept = null;
        if (plain && memo.bons$memoLastMotion() == motion && memo.bons$memoLastTime() == time && memo.bons$memoLastSkin() == skin
                && ((MotionMemoModel) model).bons$memoLastWriter() == player) {
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: slashblade_motion_update_memo applies (a repeated MMD motion update with unchanged inputs is left out){}",
                        SHADOW ? " - shadow verification on" : "");
            }
            if (!SHADOW) {
                return true;
            }
            kept = Snapshot.take(player, model);
        }
        if (model != null) {
            ((MotionMemoModel) model).bons$memoLastWriter(null);    // no repeat can be answered until tail records again
        }
        // plain inputs cannot change while the call runs: it is NyMMD code only (exact classes, no EventBus subscriber)
        memo.bons$memoPending(plain ? model : null, motion, time, skin, kept);
        return false;
    }

    /** Normal return of MmdMotionPlayer.updateMotion(float) (an exception leaves no record and no stamp). */
    public static void tail(MmdMotionPlayer player, MotionMemoPlayer memo) {
        MmdPmdModel_BasicClass model = memo.bons$memoPendingModel();
        if (model == null) {
            return;
        }
        MmdVmdMotion_BasicClass motion = memo.bons$memoPendingMotion();
        int time = memo.bons$memoPendingTime();
        MmdMatrix[] skin = memo.bons$memoPendingSkin();
        Object kept = memo.bons$memoPendingShadow();
        memo.bons$memoPending(null, null, 0, null, null);
        if (memo.bons$memoModel() == model && memo.bons$memoMotion() == motion && player._skinning_mat == skin) {
            memo.bons$memoRecord(motion, time, skin);
            ((MotionMemoModel) model).bons$memoLastWriter(player);
        }
        if (kept instanceof Snapshot snapshot) {
            SHADOW_CHECKS.incrementAndGet();
            String diff = snapshot.differs(Snapshot.take(player, model));
            if (diff != null) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) {
                    LOGGER.warn("Bons and Furious: slashblade_motion_update_memo shadow mismatch #{}: {} differs after a repeated update (time {})", m, diff,
                            Float.intBitsToFloat(time));
                }
            }
        }
    }

    /** Inputs whose repeated update is a pure function of (model, motion, time): see the class comment. */
    private static boolean plainInputs(MmdMotionPlayer player, MotionMemoPlayer memo, MmdPmdModel_BasicClass model, MmdVmdMotion_BasicClass motion) {
        if (model == null || motion == null || model.getClass() != MmdPmdModelMc.class) {
            return false;
        }
        Class<?> m = motion.getClass();
        if (m != MmdVmdMotionMc.class && m != MmdVmdMotion_BasicClass.class) {
            return false;
        }
        if (memo.bons$memoLookMe()) {
            return false;
        }
        PmdFace[] faces = memo.bons$memoFaceList();
        if (faces == null) {
            return false;
        }
        for (PmdFace face : faces) {
            if (face != null) {
                return false;
            }
        }
        return noSubscribers(player.eventBus);
    }

    private static boolean noSubscribers(EventBus bus) {
        if (REGISTRY_MAP != null || REGISTRY_MAP_FIELD != null) {
            try {
                Map<?, ?> map = REGISTRY_MAP != null ? (Map<?, ?>) REGISTRY_MAP.get(BUS_REGISTRY.get(bus))
                        : (Map<?, ?>) REGISTRY_MAP_FIELD.get(BUS_REGISTRY_FIELD.get(bus));
                if (map.isEmpty()) {
                    return true;
                }
                for (Object set : map.values()) {
                    if (!((Collection<?>) set).isEmpty()) {
                        return false;
                    }
                }
                return true;
            } catch (Throwable t) {
                // fall through to the warning: never skip
            }
        }
        if (!warned) {
            warned = true;
            LOGGER.warn("Bons and Furious: slashblade_motion_update_memo stays inactive: the Guava EventBus subscriber registry is not readable");
        }
        return false;
    }

    /** The state a repeated update writes, copied (shadow verification only). */
    private record Snapshot(double[] bones, double[] skin, float[] buffer, float[] positions) {
        static Snapshot take(MmdMotionPlayer player, MmdPmdModel_BasicClass model) {
            PmdBone[] bones = model.getBoneArray();
            double[] b = new double[bones.length * 23];
            int i = 0;
            for (PmdBone bone : bones) {
                b[i++] = bone.m_vec3Position.x;
                b[i++] = bone.m_vec3Position.y;
                b[i++] = bone.m_vec3Position.z;
                b[i++] = bone.m_vec4Rotate.x;
                b[i++] = bone.m_vec4Rotate.y;
                b[i++] = bone.m_vec4Rotate.z;
                b[i++] = bone.m_vec4Rotate.w;
                double[] mat = new double[16];
                bone.m_matLocal.getValue(mat);
                System.arraycopy(mat, 0, b, i, 16);
                i += 16;
            }
            MmdMatrix[] mats = player._skinning_mat;
            double[] s = new double[mats.length * 16];
            double[] one = new double[16];
            for (int k = 0; k < mats.length; k++) {
                mats[k].getValue(one);
                System.arraycopy(one, 0, s, k * 16, 16);
            }
            float[] buf = null;
            if (GL2_BUFFER != null) {
                try {
                    float[] live = (float[]) GL2_BUFFER.get(player);
                    buf = live == null ? null : live.clone();
                } catch (Throwable t) {
                    buf = null;
                }
            }
            MmdVector3[] pos = model.getPositionArray();
            float[] p = new float[pos.length * 3];
            for (int k = 0; k < pos.length; k++) {
                p[k * 3] = pos[k].x;
                p[k * 3 + 1] = pos[k].y;
                p[k * 3 + 2] = pos[k].z;
            }
            return new Snapshot(b, s, buf, p);
        }

        /** Bit-level comparison; null when identical, else what differs. */
        String differs(Snapshot o) {
            if (!bitsEqual(bones, o.bones)) return "bone state";
            if (!bitsEqual(skin, o.skin)) return "skinning matrices";
            if (!Arrays.equals(buffer, o.buffer)) return "vertex buffer";
            if (!Arrays.equals(positions, o.positions)) return "vertex positions";
            return null;
        }

        private static boolean bitsEqual(double[] a, double[] b) {
            if (a.length != b.length) return false;
            for (int i = 0; i < a.length; i++) {
                if (Double.doubleToRawLongBits(a[i]) != Double.doubleToRawLongBits(b[i])) return false;
            }
            return true;
        }
    }
}
