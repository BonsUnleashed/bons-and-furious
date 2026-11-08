package bons.furious.patch.fluidium_coo49;

import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the fluidium_coo49 group (since 1.0.49). Everything is Bons and Furious' standard plugin (switches,
 * guard table, yields, log lines), plus one look at FTB Chunks' ClaimedChunkManagerImpl for fluidium_claim_lookup_memo: when
 * FtbClaimEpochMixin has been applied to it, postApply reads the class as it now is (every mixin up to ours merged) and
 * records whether (1) the claim map field is private and written (putfield) only by the constructor, (2) no method but the
 * constructor, getChunk, getAllClaimedChunks, registerClaim and unregisterClaim names the field, and (3) registerClaim and
 * unregisterClaim each call a handler merged by FtbClaimEpochMixin (the epoch bump). The guard rows fingerprint those five
 * methods; this look catches a writer the fingerprints cannot see (a new method in another FTB Chunks build, or another
 * mod's mixin merged before ours). The memo answers from its table only when {@link #managerVerdict} is "" (ClaimMemo also
 * stands down at run time when a mixin of another mod with priority at or above ours was applied to the class after this
 * look). No game class is loaded here (the verdict is a plain string).
 */
public final class FluidiumPlugin implements IMixinConfigPlugin {
    static final String EPOCH_MIXIN = "bons.furious.mixin.fluidium_coo49.FtbClaimEpochMixin";
    static final String MANAGER = "dev/ftb/mods/ftbchunks/data/ClaimedChunkManagerImpl";
    static final String FIELD = "claimedChunks", FIELD_DESC = "Ljava/util/Map;";
    static final Set<String> READERS = Set.of(
            "<init>(Ldev/ftb/mods/ftbteams/api/TeamManager;)V",
            "getChunk(Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;)Ldev/ftb/mods/ftbchunks/data/ClaimedChunkImpl;",
            "getAllClaimedChunks()Ljava/util/Collection;",
            "registerClaim(Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;Ldev/ftb/mods/ftbchunks/api/ClaimedChunk;)V",
            "unregisterClaim(Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;)V");
    static final String REGISTER = "registerClaim(Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;Ldev/ftb/mods/ftbchunks/api/ClaimedChunk;)V";
    static final String UNREGISTER = "unregisterClaim(Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;)V";
    static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    /** Null until FtbClaimEpochMixin was applied to ClaimedChunkManagerImpl; then "" (the memo may answer) or why it may not. */
    public static volatile String managerVerdict;
    /** FtbClaimEpochMixin's priority as Mixin applied it (mixins of other mods at or above it were not looked at). */
    public static volatile int epochPriority = Integer.MIN_VALUE;
    private final PureMixinPlugin delegate = new PureMixinPlugin();

    @Override
    public void onLoad(String mixinPackage) {
        this.delegate.onLoad(mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return this.delegate.getRefMapperConfig();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.delegate.shouldApplyMixin(targetClassName, mixinClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        this.delegate.acceptTargets(myTargets, otherTargets);
    }

    @Override
    public List<String> getMixins() {
        return this.delegate.getMixins();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        this.delegate.preApply(targetClassName, targetClass, mixinClassName, mixinInfo);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        this.delegate.postApply(targetClassName, targetClass, mixinClassName, mixinInfo);
        if (EPOCH_MIXIN.equals(mixinClassName) && targetClass != null && MANAGER.equals(targetClass.name)) {
            String verdict;
            try {
                verdict = inspectManager(targetClass);
            } catch (Throwable t) {
                verdict = "ClaimedChunkManagerImpl could not be inspected (" + t + ")";
            }
            epochPriority = mixinInfo == null ? Integer.MIN_VALUE : mixinInfo.getPriority();
            managerVerdict = verdict;
        }
    }

    /** "" when the claim map is private, written only by the constructor and named only by the five known methods, and both
     *  claim writers call the epoch bump; otherwise the first reason found. Public for the offline proof. */
    public static String inspectManager(ClassNode c) {
        FieldNode field = null;
        for (FieldNode f : c.fields) if (f.name.equals(FIELD) && f.desc.equals(FIELD_DESC)) field = f;
        if (field == null) return "ClaimedChunkManagerImpl has no " + FIELD + " map";
        if ((field.access & Opcodes.ACC_PRIVATE) == 0) return "the claim map field is not private";
        for (MethodNode m : c.methods) {
            boolean names = false, writes = false;
            for (AbstractInsnNode i : m.instructions) {
                if (i instanceof FieldInsnNode f && f.name.equals(FIELD) && f.owner.equals(c.name)) {
                    names = true;
                    if (f.getOpcode() == Opcodes.PUTFIELD || f.getOpcode() == Opcodes.PUTSTATIC) writes = true;
                }
            }
            if (!names) continue;
            String sig = m.name + m.desc;
            if (!READERS.contains(sig)) return "ClaimedChunkManagerImpl." + sig + " uses the claim map" + mergedBy(m);
            if (writes && !m.name.equals("<init>")) return "ClaimedChunkManagerImpl." + sig + " assigns the claim map field";
        }
        for (String sig : new String[] {REGISTER, UNREGISTER}) {
            MethodNode m = method(c, sig);
            if (m == null) return "ClaimedChunkManagerImpl." + sig + " is missing";
            if (!callsEpochHandler(c, m)) return "ClaimedChunkManagerImpl." + sig + " does not call the epoch bump";
        }
        return "";
    }

    private static MethodNode method(ClassNode c, String sig) {
        for (MethodNode m : c.methods) if ((m.name + m.desc).equals(sig)) return m;
        return null;
    }

    private static boolean callsEpochHandler(ClassNode c, MethodNode m) {
        for (AbstractInsnNode i : m.instructions) {
            if (!(i instanceof MethodInsnNode mi) || !mi.owner.equals(c.name)) continue;
            MethodNode h = method(c, mi.name + mi.desc);
            if (h != null && EPOCH_MIXIN.equals(mergedMixin(h))) return true;
        }
        return false;
    }

    private static String mergedMixin(MethodNode m) {
        for (List<AnnotationNode> list : java.util.Arrays.asList(m.visibleAnnotations, m.invisibleAnnotations)) {
            if (list == null) continue;
            for (AnnotationNode a : list) {
                if (!a.desc.equals(MERGED) || a.values == null) continue;
                for (int i = 0; i + 1 < a.values.size(); i += 2) if ("mixin".equals(a.values.get(i))) return String.valueOf(a.values.get(i + 1));
            }
        }
        return null;
    }

    private static String mergedBy(MethodNode m) {
        String mixin = mergedMixin(m);
        return mixin == null ? "" : " (merged by the mixin " + mixin + ")";
    }
}
