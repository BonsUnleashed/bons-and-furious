package bons.furious.patch.vanilla_c2;

/**
 * Bons and Furious switch vanilla_cursor_counters (Minecraft 1.21.1 with NeoForge 21.1.252, both sides): runtime switch of
 * Cursor3DCounterMixin. No Mojang code here.
 *
 * Cursor3D walks the cells of a box for BlockCollisions (collision checks, Entity.checkSupportingBlock): each advance()
 * splits its running index into x, y, z with two integer divisions (index % width, index / width, then % height and
 * / height). For a cursor whose three sizes are positive and whose cell count fits in an int (every box BlockCollisions
 * builds), the mixin steps x, carrying into y and z, instead: the same x, y, z and index after every advance (a mixed-radix
 * counter is exactly that decomposition of 0, 1, 2, ...), so nextX/Y/Z and getNextType read the same values. Every other
 * cursor (a size of zero or less, or a count that overflows int) runs the original advance unchanged. The mode is decided
 * once per cursor, in its constructor, so -Dbons_and_furious.cursorCounters=false applies to cursors made afterwards.
 *
 * Ported to 1.21.1: unchanged logic; 1.21.1's Cursor3D is the same class (same fields, constructor arithmetic, advance
 * divisions and readers), so the counter gives the same x, y, z and index.
 */
public final class CursorCounters {
    /** Runtime switch, read when a cursor is made. -Dbons_and_furious.cursorCounters=false keeps the original advance. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cursorCounters", "true"));

    private CursorCounters() {
    }

    /** Counter mode: three positive sizes whose product is the cursor's cell count without int overflow. */
    public static boolean counterMode(int width, int height, int depth, int end) {
        return enabled && width > 0 && height > 0 && depth > 0 && (long) width * height * depth == end;
    }
}
