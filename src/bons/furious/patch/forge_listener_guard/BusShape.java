package bons.furious.patch.forge_listener_guard;

import bons.furious.guard.Fingerprint;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * forge_listener_prologue_guard: the guard on eventbus (and FML's LogicalSide). eventbus 6.2.33 is on the boot layer, so
 * Bons and Furious' guard table cannot fingerprint it (it reads only game-layer classes). This check reads the class files
 * of the running eventbus and fmlcore (both modules serve their class files as resources; eventbus is an open module)
 * and compares the methods the dispatch replays or relies on with the tested builds' fingerprints (Fingerprint.of, the
 * guard table's own measure). Any difference, or a class that cannot be read, turns the switch off with one WARN.
 */
public final class BusShape {
    /** internal class, method, descriptor -> sha256 of eventbus 6.2.33 / fmlcore 1.20.1-47.4.28 (tested builds). */
    static final String[][] EXPECTED = {
            {"net/minecraftforge/eventbus/EventBus", "post", "(Lnet/minecraftforge/eventbus/api/Event;)Z", "ae2072d5849dde317ea05eb1dfef7bb8c342e2e0ab23833f6d6f297a0d09df99"},
            {"net/minecraftforge/eventbus/EventBus", "post", "(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraftforge/eventbus/api/IEventBusInvokeDispatcher;)Z", "fbb001891a3ca035bc588552c24dbb246c55610ab33e3a1361ca3823b32f6203"},
            {"net/minecraftforge/eventbus/ASMEventHandler", "invoke", "(Lnet/minecraftforge/eventbus/api/Event;)V", "b14c913e1b6972a282286b11a12c0ced0d11aabbc45420da083f073d4ac62fc6"},
            {"net/minecraftforge/eventbus/ASMEventHandler", "toString", "()Ljava/lang/String;", "cdd88bd643ea5d4d0f465d652714cbca06cb5470b7e3e499c0876cbf8db1fdbc"},
            {"net/minecraftforge/eventbus/ASMEventHandler", "<init>", "(Lnet/minecraftforge/eventbus/IEventListenerFactory;Ljava/lang/Object;Ljava/lang/reflect/Method;ZLnet/minecraftforge/eventbus/api/SubscribeEvent;)V", "a12461c0722b601fb69db00fe34a0d8a96c7b358ce77b8a23def9fa40788403a"},
            {"net/minecraftforge/eventbus/ASMEventHandler", "of", "(Lnet/minecraftforge/eventbus/IEventListenerFactory;Ljava/lang/Object;Ljava/lang/reflect/Method;Z)Lnet/minecraftforge/eventbus/ASMEventHandler;", "9108cb8e9e8f4ab457cde5a3aad9bb5daf89ac661eeb4a4855fecbd1580f6a32"},
            {"net/minecraftforge/eventbus/ASMEventHandler$1", "invoke", "(Lnet/minecraftforge/eventbus/api/Event;)V", "8b078ce626b2f3c9c736d10a999348703d8a3d32ad08fa182b7eb2ce861e893f"},
            {"net/minecraftforge/eventbus/ClassLoaderFactory", "transformNode", "(Ljava/lang/String;Ljava/lang/reflect/Method;Lorg/objectweb/asm/tree/ClassNode;)V", "e3d05a9e71e84bc9d738b1865cbb8becd762f49ba3c28f5604b85e3af456b8ea"},
            {"net/minecraftforge/eventbus/ClassLoaderFactory", "create", "(Ljava/lang/reflect/Method;Ljava/lang/Object;)Lnet/minecraftforge/eventbus/api/IEventListener;", "0abe4edd39f334b670284a5ae3da949435f70d809423df34ebc8df94aacaaf6f"},
            {"net/minecraftforge/eventbus/ModLauncherFactory", "createWrapper", "(Ljava/lang/reflect/Method;)Ljava/lang/Class;", "a3576bc72b7313a9a69f14961d57451ededa993a8f962674f497f37635ba8e9f"},
            {"net/minecraftforge/eventbus/ModLauncherFactory", "processWrapperClass", "(Ljava/lang/String;Lorg/objectweb/asm/tree/ClassNode;)V", "188425cf49537e3e582f713b9e7e6e932a7003819442852bb2e2166a9fc5fc23"},
            {"net/minecraftforge/eventbus/IEventListenerFactory", "getUniqueName", "(Ljava/lang/reflect/Method;)Ljava/lang/String;", "c54149f2a2e8bd6266456256c7636a645893d793341170eebd56c01f3512415d"},
            {"net/minecraftforge/eventbus/api/EventPriority", "invoke", "(Lnet/minecraftforge/eventbus/api/Event;)V", "c24a4c414393e412b60a15b6d139184ca6186dd47ad6456a5c4bcab121edfdb1"},
            {"net/minecraftforge/eventbus/api/Event", "isCanceled", "()Z", "963cef274dfbc5e1e81a799acedc2ecccc59bb74c988d3f4f89a606dc4e2da23"},
            {"net/minecraftforge/eventbus/api/Event", "getListenerList", "()Lnet/minecraftforge/eventbus/ListenerList;", "e5eed7c2cd0a4c2a6a64a216cc1647f9c8a2a25814ed8da2aaa736e4dc7e9fc7"},
            {"net/minecraftforge/eventbus/api/Event", "setPhase", "(Lnet/minecraftforge/eventbus/api/EventPriority;)V", "cf699530a7adedaeaec4c76ed5675ba7462f7d46efd68db302757b26810e8054"},
            {"net/minecraftforge/eventbus/api/EventListenerHelper", "getListenerList", "(Ljava/lang/Class;)Lnet/minecraftforge/eventbus/ListenerList;", "155f1d2ef0b50e211a5ec51b5f5ae014af0da7a17adef89124de4094eea69091"},
            {"net/minecraftforge/eventbus/api/EventListenerHelper", "getListenerListInternal", "(Ljava/lang/Class;Z)Lnet/minecraftforge/eventbus/ListenerList;", "2667f5da34a87253247866706415172e32e25735b34b8e7cc5b1fe21482a9217"},
            {"net/minecraftforge/eventbus/api/EventListenerHelper", "computeListenerList", "(Ljava/lang/Class;Z)Lnet/minecraftforge/eventbus/ListenerList;", "155a1a39dd945bd968320ac345ad918b1f65374d15cf46e0389cb1c465d36ed0"},
            {"net/minecraftforge/eventbus/ListenerList", "getListeners", "(I)[Lnet/minecraftforge/eventbus/api/IEventListener;", "9534d52ef3b810ec77d6e8dea98481eadef8425d15524c8a9307910eaf34f634"},
            {"net/minecraftforge/eventbus/ListenerList", "unregister", "(ILnet/minecraftforge/eventbus/api/IEventListener;)V", "da23be7ccd992822051e8196d062b7caeda14cf48d5406b4e2144b41b131ba9f"},
            {"net/minecraftforge/eventbus/ListenerList$ListenerListInst", "getListeners", "()[Lnet/minecraftforge/eventbus/api/IEventListener;", "1a101e97f5b069c706913d0875e235d13bf57b61a740c121898943ca2108021c"},
            {"net/minecraftforge/eventbus/ListenerList$ListenerListInst", "buildCache", "()[Lnet/minecraftforge/eventbus/api/IEventListener;", "6149a640de6bf996f32fd5d7d1c8f2d2d5f4011f1d8050b7ce24dd3e8348b4c0"},
            {"net/minecraftforge/eventbus/ListenerList$ListenerListInst", "forceRebuild", "()V", "8a9aecfecba2641b4e35fd356158a182b1814713a93e2de6833514b313475eb6"},
            {"net/minecraftforge/eventbus/ListenerList$ListenerListInst", "register", "(Lnet/minecraftforge/eventbus/api/EventPriority;Lnet/minecraftforge/eventbus/api/IEventListener;)V", "718457f6bbf64bba85614986ba65c26a232f44f979e57419f50c7aa16ffa3729"},
            {"net/minecraftforge/eventbus/ListenerList$ListenerListInst", "unregister", "(Lnet/minecraftforge/eventbus/api/IEventListener;)V", "a7c06656f7e59119d3adcf7a9733d181124f01c22008db10e81536cee6053fb1"},
            {"net/minecraftforge/fml/LogicalSide", "isClient", "()Z", "50ff73708bc60abd3c975900854ecdcbd5275d02fb0d92ad592c6c1460efa81f"},
            {"net/minecraftforge/fml/LogicalSide", "isServer", "()Z", "7a808fa6c5501eabcb349c71c7b4831e52e75b1f43bf0b21c2c3cd26dc9cede0"}};
    private static volatile String verdict;
    /** Harness only (Harness.acceptBusShape): the first offline run, before the tested fingerprints are recorded here. */
    static volatile boolean harnessAccept;

    private BusShape() {}

    /** Harness: true while no tested fingerprint is recorded in EXPECTED yet. */
    static boolean unrecorded() {
        for (String[] e : EXPECTED) if (!"?".equals(e[3])) return false;
        return true;
    }

    /** null when every method matches; otherwise what differs (cached). */
    public static String check() {
        if (harnessAccept) return null;
        String v = verdict;
        if (v == null) verdict = v = compute();
        return v.isEmpty() ? null : v;
    }

    private static String compute() {
        Map<String, String> actual = actual();
        StringBuilder bad = new StringBuilder();
        for (String[] e : EXPECTED) {
            String got = actual.get(e[0] + "." + e[1] + e[2]);
            if (!e[3].equals(got)) {
                if (bad.length() > 0) bad.append(", ");
                bad.append(e[0].substring(e[0].lastIndexOf('/') + 1)).append('.').append(e[1]).append(got == null ? " not readable" : " differs");
            }
        }
        return bad.toString();
    }

    /** The fingerprints of the running classes (internal.method+desc -> sha256 or absent); the harness prints them. */
    public static Map<String, String> actual() {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        for (String[] e : EXPECTED) {
            ClassNode n = nodes.computeIfAbsent(e[0], BusShape::read);
            if (n == null) continue;
            for (MethodNode m : n.methods) if (m.name.equals(e[1]) && m.desc.equals(e[2])) out.put(e[0] + "." + e[1] + e[2], Fingerprint.of(m));
        }
        return out;
    }

    private static ClassNode read(String internal) {
        Class<?> anchor = internal.startsWith("net/minecraftforge/fml/") ? net.minecraftforge.fml.LogicalSide.class
                : net.minecraftforge.eventbus.api.Event.class;
        try (InputStream in = anchor.getResourceAsStream("/" + internal + ".class")) {
            if (in == null) return null;
            ClassNode n = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(n, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return n;
        } catch (Throwable t) {
            return null;
        }
    }
}
