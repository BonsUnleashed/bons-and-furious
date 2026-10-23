package bons.furious.patch.number_prechecks;

/**
 * Bons and Furious, group number_prechecks (since 1.0.45): the NumberFormatException the pre-check switches throw where
 * the real parser would have thrown one, without filling a stack trace.
 *
 * It is thrown only into catch blocks that store the exception and never read it (javap of each tested build: EMF 3.2.4
 * MathExpressionParser.readVariableOrConstant, GeckoLib 4.8.4 / AzureLib 3.0.13 MolangParser.parseJson, Text Animator
 * 2.5.4 Effect.create; the handler taint audit in tools/mixedproof/NumberPrechecksHandlerAudit.java proves the stored
 * value is dead), so nothing can observe the missing stack. One fresh instance per throw: no Throwable state (cause,
 * suppressed list, stack) is shared between threads or calls. The message is built only if somebody asks for it, in the
 * wording of the client's JDK 25 parser.
 */
final class QuietNumberFormatException extends NumberFormatException {
    private static final long serialVersionUID = 1L;
    private final String input;

    QuietNumberFormatException(String input) {
        super();
        this.input = input;
    }

    /** No stack walk: Throwable's constructor calls this, and nothing ever reads the stack of these exceptions. */
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public String getMessage() {
        String s = input;
        if (s == null) return null;
        int i = 0, n = s.length();
        while (i < n && s.charAt(i) <= ' ') i++;
        return i == n ? "empty String" : "For input string: \"" + s + "\"";
    }
}
