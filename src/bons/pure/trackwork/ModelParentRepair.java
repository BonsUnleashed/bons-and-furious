package bons.pure.trackwork;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** Changes a broken parent reference in the installed mod's own resource.
 * No Trackwork model geometry is included in Bons.
 */
public final class ModelParentRepair {
    private static final Map<String, String> REVIEWED = Map.of(
        "models/block/oleo_wheel_single.json", "1f420de25734f0f816579050f36ee31b9c190165c7272bcc8f704971cd1d0220",
        "models/block/oleo_wheel_twin.json", "16b137b0d71e3effb715101c2b4a1ec76e53b41ccf2ba7258efb45ed85712907",
        "models/block/small_simple_wheel.json", "7334eddf1f5835ab743967d36ea59cb1a739105717b6cde437db631aa44982c5",
        "models/block/track_link.json", "f12073165557d3ea3a4bfe31c60dbb288e98ba57c68d77f2782b0572d2562dc3",
        "models/block/wrapped_link.json", "158afda55c14bdd3f17f56092240aeaf9d94d2e89db3f38d511323457df2b5af"
    );

    private ModelParentRepair() {}

    public static byte[] repair(String path, byte[] original) {
        String expected = REVIEWED.get(path);
        if (expected == null) return original;
        final String digest;
        try {
            digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required SHA-256 implementation is unavailable", impossible);
        }
        // Unknown/new resource versions retain their original bytes.
        if (!expected.equals(digest)) return original;
        JsonObject model = JsonParser.parseString(new String(original, StandardCharsets.UTF_8)).getAsJsonObject();
        model.addProperty("parent", "minecraft:block/block");
        return model.toString().getBytes(StandardCharsets.UTF_8);
    }
}
