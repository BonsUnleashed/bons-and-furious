package bons.furious.patch.number_prechecks;

/**
 * Bons and Furious switch textanimator_parse_prechecks (since 1.0.45): the IllegalArgumentException thrown instead of
 * calling Text Animator's Effect.create for a tag it would reject anyway (an unregistered effect type, or "typewriter"
 * where it is not allowed), without filling a stack trace.
 *
 * It is thrown only at the Effect.create call in CommonProxy.iterateFormatted, whose catch (Exception) block stores the
 * exception and never reads it (javap of Text Animator 2.5.4: astore 21, the slot is dead afterwards; handler taint audit
 * in tools/mixedproof/NumberPrechecksHandlerAudit.java). One fresh instance per throw; the message is built lazily in Text
 * Animator's own wording.
 */
final class QuietIllegalArgumentException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String type;
    private final boolean typewriter;

    QuietIllegalArgumentException(String type, boolean typewriter) {
        super();
        this.type = type;
        this.typewriter = typewriter;
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public String getMessage() {
        return typewriter ? "Typewriter effect is not allowed here" : "Unknown effect type: " + type;
    }
}
