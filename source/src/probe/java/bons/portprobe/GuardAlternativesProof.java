package bons.portprobe;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Bons and Furious 1.0.46 shared guard layer: unit checks of Guards' new decision helpers on the compiled class
 * (work/F/dev/<group>/classes): compareVersions (redundant_since), expectedPrint and alternativeBuild (sha256_also: one
 * build label for ALL guard lines, never mixed; "-" = absent; the audited-mod-absent marker satisfies any label).
 * The whole decision (Guards.compute with the bytecode provider) is exercised by `dev apply` in both pack modes.
 *
 *   py -3 mixedproof/LaneF_job.py mg forge_registry_wrapper mixedproof/GuardAlternativesProof.java --side SERVER --xmx 1g
 */
public final class GuardAlternativesProof {
    static int checks, fails;

    static void ok(boolean c, String what) {
        checks++;
        if (!c) { fails++; System.out.println("FAIL " + what); }
    }

    public static void main(String[] args) throws Exception {
        Class<?> g = Class.forName("bons.furious.guard.Guards");
        Method cmp = g.getDeclaredMethod("compareVersions", String.class, String.class);
        Method exp = g.getDeclaredMethod("expectedPrint", String[].class, String.class);
        Method alt = g.getDeclaredMethod("alternativeBuild", List.class, String[].class);
        for (Method m : new Method[] {cmp, exp, alt}) m.setAccessible(true);
        Field gone = g.getDeclaredField("AUDITED_MOD_ABSENT");
        gone.setAccessible(true);
        String GONE = (String) gone.get(null);

        Object[][] v = {
                {"47.4.28", "47.4.24", 1}, {"47.4.24", "47.4.24", 0}, {"47.4.16", "47.4.24", -1}, {"47.4.25", "47.4.26", -1},
                {"15.71.0.224", "15.62.0.218", 1}, {"15.62.0.218", "15.62.0.218", 0}, {"15.62.0.214", "15.62.0.218", -1},
                {"15.59.0.212", "15.62.0.214", -1}, {"5.27.89+mc1.20.1", "5.27.89", 0}, {"5.27.77+mc1.20.1", "5.27.89", -1},
                {"5.27.90+mc1.20.1", "5.27.89", 1}, {"47.4", "47.4.0", 0}, {"47.4.28", "47.4", 1}, {"1.20.1", "1.20.1", 0},
                {"abc", "1", -1}, {"47.4.28-beta", "47.4.28", 0}, {"10.4.16.80", "10.4.9", 1}};
        for (Object[] c : v) {
            int r = Integer.signum((Integer) cmp.invoke(null, c[0], c[1]));
            ok(r == (Integer) c[2], "compareVersions(" + c[0] + ", " + c[1] + ") = " + r + ", want " + c[2]);
        }

        // guard lines: [class, method, desc, tested print, alternatives]
        String[] a = {"a/A", "m", "()V", "T1", "B1=X1;B2=-"};
        String[] b = {"a/B", "n", "()V", "T2", "B1=X2"};
        String[] c = {"a/C", "o", "()V", "-", "B2=Y3"};
        ok(exp.invoke(null, a, "").equals("T1"), "tested print");
        ok(exp.invoke(null, a, "B1").equals("X1"), "B1 row");
        ok(exp.invoke(null, a, "B2").equals("-"), "absent row");
        ok(exp.invoke(null, b, "B2").equals("T2"), "no row for B2: unchanged there");
        ok(exp.invoke(null, c, "B1").equals("-"), "absent in the tested build, no B1 row: absent in B1 too");
        List<String[]> guard = new ArrayList<>(List.of(a, b, c));
        ok("B1".equals(alt.invoke(null, guard, new String[] {"X1", "X2", null})), "every line as build B1");
        ok("B2".equals(alt.invoke(null, guard, new String[] {null, "T2", "Y3"})), "every line as build B2");
        ok(alt.invoke(null, guard, new String[] {"X1", "T2", "Y3"}) == null, "B1 for a, B2 for c: builds mixed, refused");
        ok(alt.invoke(null, guard, new String[] {"X1", "X2", "Y3"}) == null, "c present in B1: refused");
        ok(alt.invoke(null, guard, new String[] {"X1", "Z", null}) == null, "unknown print: refused");
        ok("B1".equals(alt.invoke(null, guard, new String[] {GONE, "X2", null})), "audited mod absent satisfies the line");
        List<String[]> single = new ArrayList<>();
        single.add(new String[] {"a/A", "m", "()V", "T1", ""});
        ok(alt.invoke(null, single, new String[] {"Q"}) == null, "no alternatives: null");
        b[3] = "T2|D2";
        ok("B2".equals(alt.invoke(null, guard, new String[] {null, "D2", "Y3"})), "production variant within one alternate build");
        ok(alt.invoke(null, guard, new String[] {null, "UNKNOWN", "Y3"}) == null, "unknown production variant refused");
        System.out.println("GUARD HELPERS checks " + checks + " failures " + fails);
        System.out.println(fails == 0 ? "RESULT PASS" : "RESULT FAIL");
        if (fails != 0) throw new AssertionError("Guard helper failures: " + fails);
    }
}
