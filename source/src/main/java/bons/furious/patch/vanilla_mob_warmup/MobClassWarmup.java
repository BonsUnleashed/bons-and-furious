package bons.furious.patch.vanilla_mob_warmup;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * vanilla_mob_class_warmup: the first spawn of each kind of mob in a session makes the server thread load and verify that
 * mob's classes and its AI classes through the mod loader's transforming class loader; at nightfall many kinds spawn for
 * the first time within seconds. Once per JVM, after a server has started, this loads and links the same classes on a
 * background thread:
 * <ul>
 * <li>the entity classes the registered entity types construct: the constructor reference of each factory lambda, or the
 *     classes the factory's lambda body creates, read from the host class's bytecode;</li>
 * <li>mod classes (NeoForge scan data) whose superclass or interface chain reaches a vanilla AI base type (Goal, Behavior,
 *     BehaviorControl, Sensor, PathNavigation, Move/Look/Jump/BodyRotation control, NodeEvaluator);</li>
 * <li>every vanilla class under net/minecraft/world/entity and net/minecraft/world/level/pathfinder (the vanilla mobs'
 *     goals and brain behaviours, sensors, navigation and path finding), read from the game's own jar.</li>
 * </ul>
 * Skipped: classes annotated {@code @Mixin} (Mixin refuses to load them) and classes with a client-only class in their
 * chain (so a dedicated server never asks for one). Each class is loaded with {@code initialize=false} and linked through
 * {@code getDeclaredConstructors()}, which verifies it without running its static initializer and without resolving the
 * other methods' signatures. Nothing is constructed and no game state is read or written besides the frozen entity type
 * registry, so the game later does exactly the same work, minus the loading. Every failure is counted and skipped.
 * Port of the Forge 1.20.1 helper: the scan data, mod list and game jar come from NeoForge's FML, the entity types from
 * the game's own registry.
 */
public final class MobClassWarmup {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String FACTORY = "net/minecraft/world/entity/EntityType$EntityFactory";
    private static final Set<String> ENTITY_ROOT = Set.of(ENTITY);
    private static final Set<String> AI_ROOTS = Set.of(
            "net/minecraft/world/entity/ai/goal/Goal", "net/minecraft/world/entity/ai/behavior/Behavior",
            "net/minecraft/world/entity/ai/behavior/BehaviorControl", "net/minecraft/world/entity/ai/sensing/Sensor",
            "net/minecraft/world/entity/ai/navigation/PathNavigation", "net/minecraft/world/entity/ai/control/MoveControl",
            "net/minecraft/world/entity/ai/control/LookControl", "net/minecraft/world/entity/ai/control/JumpControl",
            "net/minecraft/world/entity/ai/control/BodyRotationControl", "net/minecraft/world/level/pathfinder/NodeEvaluator");
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";

    private final ClassLoader loader = Entity.class.getClassLoader();
    /** internal name -> {super, interfaces...}; null = unknown (not readable) */
    private final Map<String, String[]> headers = new HashMap<>();
    private final Set<String> mixins = new HashSet<>();

    private MobClassWarmup() { }

    /** Starts the warm-up thread once per JVM; later calls (a second world in singleplayer) do nothing. */
    public static void start() {
        if (!STARTED.compareAndSet(false, true)) return;
        Thread thread = new Thread(() -> new MobClassWarmup().run(), "Bons and Furious mob class warm-up");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private void run() {
        long start = System.nanoTime();
        int entityCount = 0, aiCount = 0, linked = 0, failed = 0;
        vanillaCount = 0;
        try {
            for (ModFileScanData scan : ModList.get().getAllScanData()) {
                for (ModFileScanData.ClassData data : scan.getClasses()) {
                    List<Type> itf = new ArrayList<>(data.interfaces());
                    String[] h = new String[1 + itf.size()];
                    h[0] = data.parent() == null ? null : data.parent().getInternalName();
                    for (int i = 0; i < itf.size(); i++) h[i + 1] = itf.get(i).getInternalName();
                    headers.put(data.clazz().getInternalName(), h);
                }
                for (ModFileScanData.AnnotationData a : scan.getAnnotations())
                    if (MIXIN.equals(a.annotationType().getDescriptor())) mixins.add(a.clazz().getInternalName());
            }
            Set<String> entities = new TreeSet<>();
            for (String host : factoryHosts()) collectFactoryClasses(host, entities);
            Map<String, Boolean> entityMemo = new HashMap<>();
            entities.removeIf(n -> !reaches(n, ENTITY_ROOT, entityMemo, 0) || skipped(n));
            Set<String> ai = new TreeSet<>();
            Map<String, Boolean> aiMemo = new HashMap<>();
            for (String n : new ArrayList<>(headers.keySet()))
                if (!entities.contains(n) && reaches(n, AI_ROOTS, aiMemo, 0) && !skipped(n)) ai.add(n);
            entityCount = entities.size();
            aiCount = ai.size();
            List<String> all = new ArrayList<>(entities);
            all.addAll(ai);
            for (String n : vanillaClasses()) if (!entities.contains(n)) { all.add(n); vanillaCount++; }
            for (String n : all) {
                try {
                    Class<?> c = Class.forName(n.replace('/', '.'), false, loader);
                    if (constructorsSafe(n)) {
                        c.getDeclaredConstructors();
                        linked++;
                    } else {
                        loadedOnly++;
                    }
                } catch (Throwable t) {
                    failed++;
                    LOGGER.debug("Bons and Furious: mob class warm-up skipped {} ({})", n, t.toString());
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Bons and Furious: mob class warm-up stopped early", t);
        }
        LOGGER.info("Bons and Furious: mob class warm-up loaded and linked {} classes ({} entity, {} mod AI, {} vanilla entity/AI) in {} ms on a background thread{}{}",
                linked, entityCount, aiCount, vanillaCount, (System.nanoTime() - start) / 1_000_000L,
                loadedOnly == 0 ? "" : ("; " + loadedOnly + " loaded only (a constructor names a client-only class)"),
                failed == 0 ? "" : ("; " + failed + " skipped"));
    }

    private int loadedOnly;

    /** Linking resolves every constructor's parameter types; a dedicated server would log an error for a client-only one
     *  (the game itself never resolves it when that constructor is not called), so such a class is only loaded. */
    private boolean constructorsSafe(String internal) {
        byte[] b = bytes(internal);
        if (b == null) return false;
        ClassNode node = new ClassNode();
        new ClassReader(b).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        for (MethodNode m : node.methods) {
            if (!m.name.equals("<init>")) continue;
            for (Type t : Type.getArgumentTypes(m.desc)) {
                Type e = t.getSort() == Type.ARRAY ? t.getElementType() : t;
                if (e.getSort() == Type.OBJECT && clientOnly(e.getInternalName())) return false;
            }
        }
        return true;
    }

    private int vanillaCount;

    /** Every vanilla class under net/minecraft/world/entity and net/minecraft/world/level/pathfinder (goals, brain
     *  behaviours, sensors, navigation, path finding, the mobs' inner goal classes), read from the game's own jar. */
    private List<String> vanillaClasses() {
        List<String> out = new ArrayList<>();
        try {
            java.nio.file.Path root = ModList.get().getModFileById("minecraft").getFile().getSecureJar().getRootPath();
            for (String pkg : VANILLA_PACKAGES) {
                java.nio.file.Path dir = root.resolve(pkg);
                if (!java.nio.file.Files.isDirectory(dir)) continue;
                try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(dir)) {
                    files.map(p -> root.relativize(p).toString().replace('\\', '/'))
                         .filter(n -> n.endsWith(".class"))
                         .forEach(n -> out.add(n.substring(0, n.length() - ".class".length())));
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Bons and Furious: mob class warm-up could not list the vanilla classes", t);
        }
        out.sort(null);
        return out;
    }

    private static final String[] VANILLA_PACKAGES = {"net/minecraft/world/entity/", "net/minecraft/world/level/pathfinder/"};

    /** Hosts of the registered entity types' factories: the class that created each factory lambda, or the factory's own class. */
    private Set<String> factoryHosts() throws ReflectiveOperationException {
        Field factory = null;
        for (Field f : EntityType.class.getDeclaredFields())
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == EntityType.EntityFactory.class) { factory = f; break; }
        if (factory == null) return Set.of();
        factory.setAccessible(true);
        Set<String> hosts = new TreeSet<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Object f = factory.get(type);
            if (f == null) continue;
            String name = f.getClass().getName();
            int lambda = name.indexOf("$$Lambda");
            hosts.add((lambda > 0 ? name.substring(0, lambda) : name).replace('.', '/'));
        }
        return hosts;
    }

    /** Entity classes a host's factory lambdas construct (constructor references, or NEW inside the lambda body). */
    private void collectFactoryClasses(String host, Set<String> out) {
        byte[] bytes = bytes(host);
        if (bytes == null) return;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        boolean factoryClass = false;
        for (String i : node.interfaces) if (FACTORY.equals(i)) factoryClass = true;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof InvokeDynamicInsnNode indy && indy.bsm != null
                        && "java/lang/invoke/LambdaMetafactory".equals(indy.bsm.getOwner())
                        && FACTORY.equals(Type.getReturnType(indy.desc).getInternalName())
                        && indy.bsmArgs.length > 1 && indy.bsmArgs[1] instanceof Handle impl) {
                    if (impl.getTag() == Opcodes.H_NEWINVOKESPECIAL) out.add(impl.getOwner());
                    else if (impl.getOwner().equals(node.name)) addNews(node, impl.getName(), impl.getDesc(), out);
                }
            }
            if (factoryClass && !method.name.equals("<init>") && !method.name.equals("<clinit>")) addNews(node, method.name, method.desc, out);
        }
    }

    private static void addNews(ClassNode node, String name, String desc, Set<String> out) {
        for (MethodNode m : node.methods)
            if (m.name.equals(name) && m.desc.equals(desc))
                for (AbstractInsnNode insn : m.instructions)
                    if (insn.getOpcode() == Opcodes.NEW) out.add(((TypeInsnNode) insn).desc);
    }

    private boolean skipped(String n) {
        return mixins.contains(n) || n.contains("/mixin/") || n.contains("/mixins/") || clientChain(n, 0);
    }

    private static boolean clientOnly(String n) {
        return n.startsWith("net/minecraft/client/") || n.startsWith("com/mojang/blaze3d/");
    }

    private final Map<String, Boolean> clientMemo = new HashMap<>();

    private boolean clientChain(String n, int depth) {
        if (n == null || depth > 64) return false;
        if (clientOnly(n)) return true;
        Boolean known = clientMemo.get(n);
        if (known != null) return known;
        clientMemo.put(n, false);
        boolean found = false;
        String[] h = header(n);
        if (h != null) for (String p : h) if (clientChain(p, depth + 1)) { found = true; break; }
        clientMemo.put(n, found);
        return found;
    }

    private boolean reaches(String n, Set<String> roots, Map<String, Boolean> memo, int depth) {
        if (n == null || depth > 64 || clientOnly(n)) return false;
        if (roots.contains(n)) return true;
        Boolean known = memo.get(n);
        if (known != null) return known;
        memo.put(n, false);
        boolean found = false;
        String[] h = header(n);
        if (h != null) for (String p : h) if (reaches(p, roots, memo, depth + 1)) { found = true; break; }
        memo.put(n, found);
        return found;
    }

    private String[] header(String n) {
        if (headers.containsKey(n)) return headers.get(n);
        String[] h = null;
        if (!n.startsWith("java/") && !n.startsWith("javax/") && !n.startsWith("jdk/") && !n.startsWith("sun/")) {
            byte[] b = bytes(n);
            if (b != null) {
                ClassReader reader = new ClassReader(b);
                String[] itf = reader.getInterfaces();
                h = new String[1 + itf.length];
                h[0] = reader.getSuperName();
                System.arraycopy(itf, 0, h, 1, itf.length);
            }
        }
        headers.put(n, h);
        return h;
    }

    private byte[] bytes(String internal) {
        try (InputStream in = loader.getResourceAsStream(internal + ".class")) {
            return in == null ? null : in.readAllBytes();
        } catch (Throwable t) {
            return null;
        }
    }
}
