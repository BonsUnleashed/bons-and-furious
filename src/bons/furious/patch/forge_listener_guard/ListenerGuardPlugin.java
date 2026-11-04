package bons.furious.patch.forge_listener_guard;

import bons.pure.config.PureMixinPlugin;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the forge_listener_guard group (forge_listener_prologue_guard, since 1.0.49): Bons and Furious'
 * standard plugin (switch, guard table, yields, log lines; every decision is the standard plugin's) plus one look at each
 * Forge hook class once every mod's mixins are merged into it (Mixin calls a config plugin's postApply after all mixins of
 * all configs have been applied to the class).
 *
 * MixinExtras applies @WrapOperation, @ModifyExpressionValue and @WrapWithCondition later than that, in its own
 * extension's postApply, so at this point no wrapper (ours or another mod's) is in the code yet; their handler METHODS
 * are, with their annotations. So for each of the twelve post sites (Sites.METHODS) another mod's change is found from
 * what is merged into the class, not from the code:
 *  - the site method itself is merged by another mod's mixin (an @Overwrite);
 *  - a merged method of another mod's mixin carries an annotation whose "method" selector can select the site method
 *    (by name, any descriptor; any selector with a wildcard or a regular expression; or a "target" descriptor
 *    annotation): an injector of any kind (@Inject, @Redirect, @ModifyArg, @WrapOperation, @WrapMethod, ...) on it;
 *  - the site method (with the code it hands to an invokedynamic or calls in its own class) does not contain exactly
 *    one IEventBus.post call (another mod's redirect replaced it, or the method is not as shipped);
 *  - the site method calls a method merged by another mod's mixin.
 * Any of these makes the site stand down: our wrapper then calls the original operation, so the other mod's chain and the
 * stock bus run exactly as without this switch. Same delegation pattern as the mcreator_entity_guards group's
 * EntityGuardsPlugin.
 */
public final class ListenerGuardPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    static final String OURS = "bons.furious.mixin.forge_listener_guard.";
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus", POST = "post", POST_DESC = "(Lnet/minecraftforge/eventbus/api/Event;)Z";
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
        try {
            inspect(targetClass);
        } catch (Throwable t) {
            // never stop a class from loading: an unreadable site stays on the stock path
            for (int s = 0; s < Sites.COUNT; s++) {
                if (Sites.METHODS[s][0].equals(targetClass.name)) {
                    Sites.FOREIGN[s] = true;
                    Sites.FOREIGN_BY[s] = "inspection failed: " + t;
                }
            }
            LOGGER.warn("Bons and Furious: forge_listener_prologue_guard could not inspect {} ({}); its post sites stay on the stock path",
                    targetClass.name.replace('/', '.'), t.toString());
        }
    }

    /** Sets Sites.HOOKED / FOREIGN for every post site of this class. Public for the offline harness. */
    public static void inspect(ClassNode target) {
        for (int s = 0; s < Sites.COUNT; s++) {
            String[] site = Sites.METHODS[s];
            if (!site[0].equals(target.name)) continue;
            MethodNode m = find(target, site[1], site[2]);
            String foreign = null;
            boolean ours = false;
            if (m == null) {
                foreign = "the method is missing";
            } else {
                String by = mergedBy(m);
                if (by != null && !by.startsWith(OURS)) foreign = by + " (replaces the method)";
                for (MethodNode h : target.methods) {
                    String hb = mergedBy(h);
                    if (hb == null || h == m) continue;
                    String sel = selects(h, site[1]);
                    if (sel == null) continue;
                    if (hb.startsWith(OURS)) ours = true;
                    else if (foreign == null) foreign = hb + " (" + sel + ")";
                }
                if (foreign == null) {
                    int[] posts = {0};
                    String call = walk(target, m, new HashSet<>(), posts, 0);
                    if (call != null) foreign = call + " (called from the method)";
                    else if (posts[0] != 1) foreign = "the method holds " + posts[0] + " IEventBus.post calls instead of 1";
                }
            }
            Sites.HOOKED[s] = ours;
            if (foreign != null) {
                Sites.FOREIGN[s] = true;
                Sites.FOREIGN_BY[s] = foreign;
                LOGGER.info("Bons and Furious: forge_listener_prologue_guard steps aside at {} ({}.{}): {} also changes that method; its listeners are called as shipped",
                        Sites.NAMES[s], target.name.replace('/', '.'), site[1], foreign);
            }
        }
    }

    /**
     * The annotation (simple name) of a merged method whose method selector can select a method named `name`, else null.
     * Conservative: a wildcard, a regular expression, an empty name or a "target" descriptor selects every method.
     */
    static String selects(MethodNode h, String name) {
        for (List<AnnotationNode> list : java.util.Arrays.asList(h.visibleAnnotations, h.invisibleAnnotations)) {
            if (list == null) continue;
            for (AnnotationNode a : list) {
                if (a.desc.equals(MERGED) || a.values == null) continue;
                for (int i = 0; i + 1 < a.values.size(); i += 2) {
                    Object key = a.values.get(i), value = a.values.get(i + 1);
                    if ("target".equals(key) && value instanceof List<?> t && !t.isEmpty() && t.get(0) instanceof AnnotationNode) return simple(a.desc);
                    if (!"method".equals(key) || !(value instanceof List<?> l)) continue;
                    for (Object o : l) {
                        if (o instanceof String sel && selectorNames(sel, name)) return simple(a.desc);
                    }
                }
            }
        }
        return null;
    }

    /** Whether a Mixin method selector string can select a method of that name. */
    static boolean selectorNames(String sel, String name) {
        String s = sel.trim();
        if (s.isEmpty() || s.startsWith("/") || s.indexOf('*') >= 0) return true;
        if (s.startsWith("L")) {
            int semi = s.indexOf(';');
            if (semi > 0) s = s.substring(semi + 1);
        }
        int end = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '{' || c == ':') {
                end = i;
                break;
            }
        }
        String n = s.substring(0, end);
        return n.isEmpty() || n.equals(name);
    }

    private static String simple(String desc) {
        String d = desc.substring(desc.lastIndexOf('/') + 1);
        return "@" + (d.endsWith(";") ? d.substring(0, d.length() - 1) : d);
    }

    /**
     * Counts IEventBus.post calls in m and in the methods of the same class it calls or hands to an invokedynamic (where
     * MixinExtras moves a wrapped call), transitively; returns the first method merged by another mod's mixin that is
     * reached, else null.
     */
    private static String walk(ClassNode target, MethodNode m, Set<String> seen, int[] posts, int depth) {
        if (!seen.add(m.name + m.desc) || depth > 6) return null;
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof MethodInsnNode mi) {
                if (mi.getOpcode() == Opcodes.INVOKEINTERFACE && mi.owner.equals(BUS) && mi.name.equals(POST) && mi.desc.equals(POST_DESC)) posts[0]++;
                else if (mi.owner.equals(target.name)) {
                    String f = visit(target, find(target, mi.name, mi.desc), seen, posts, depth);
                    if (f != null) return f;
                }
            } else if (n instanceof InvokeDynamicInsnNode d) {
                for (Object a : d.bsmArgs) {
                    if (a instanceof Handle h && h.getOwner().equals(target.name)) {
                        String f = visit(target, find(target, h.getName(), h.getDesc()), seen, posts, depth);
                        if (f != null) return f;
                    }
                }
            }
        }
        return null;
    }

    private static String visit(ClassNode target, MethodNode callee, Set<String> seen, int[] posts, int depth) {
        if (callee == null) return null;
        String by = mergedBy(callee);
        if (by != null && !by.startsWith(OURS)) return by;
        // only our own handlers and generated helpers (synthetic, or a generated name such as MixinExtras' bridges) are
        // followed: a call to another site method (the RenderType overload of dispatchRenderStage calls the Stage
        // overload) is that site's business
        if (by == null && (callee.access & Opcodes.ACC_SYNTHETIC) == 0 && callee.name.indexOf('$') < 0) return null;
        return walk(target, callee, seen, posts, depth + 1);
    }

    static MethodNode find(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        return null;
    }

    /** The mixin named by a method's @MixinMerged, or null. */
    static String mergedBy(MethodNode m) {
        for (List<AnnotationNode> list : java.util.Arrays.asList(m.visibleAnnotations, m.invisibleAnnotations)) {
            if (list == null) continue;
            for (AnnotationNode a : list) {
                if (!a.desc.equals(MERGED) || a.values == null) continue;
                for (int i = 0; i + 1 < a.values.size(); i += 2) if ("mixin".equals(a.values.get(i))) return String.valueOf(a.values.get(i + 1));
            }
        }
        return null;
    }
}
