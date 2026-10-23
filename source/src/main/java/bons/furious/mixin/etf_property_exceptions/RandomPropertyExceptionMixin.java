package bons.furious.mixin.etf_property_exceptions;

import bons.furious.patch.etf_property_exceptions.EtfPropertyExceptions;
import java.io.PrintStream;
import java.io.PrintWriter;
import org.spongepowered.asm.mixin.Mixin;
import traben.entity_texture_features.features.property_reading.properties.RandomProperty;

/**
 * etf_stackless_property_exception (Entity Texture Features 7.1 + Entity Model Features 3.2.4, client only, since
 * 1.0.45): adds fillInStackTrace() to RandomProperty$RandomPropertyException, which returns the exception without a
 * stack walk while the switch is on (Throwable's constructor calls it), and a tripwire on the stack readers
 * (getStackTrace, printStackTrace). Every handler that can receive this exception discards it (audit), so the empty
 * stack cannot be observed. See EtfPropertyExceptions. ETF is LGPL-3.0; no ETF code is carried.
 */
@Mixin(value = RandomProperty.RandomPropertyException.class, remap = false)
public abstract class RandomPropertyExceptionMixin extends Exception {
    private static final long serialVersionUID = 1L;

    @Override
    public Throwable fillInStackTrace() {
        return EtfPropertyExceptions.fillStack() ? super.fillInStackTrace() : this;
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        EtfPropertyExceptions.stackRead(this);
        return super.getStackTrace();
    }

    @Override
    public void printStackTrace(PrintStream s) {
        EtfPropertyExceptions.stackRead(this);
        super.printStackTrace(s);
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        EtfPropertyExceptions.stackRead(this);
        super.printStackTrace(s);
    }
}
