package bons.furious.patch.climate_sampler_reuse;

import java.util.concurrent.atomic.LongAdder;

/**
 * Bons and Furious switch vanilla_chunk_climate_sampler_reuse: the runtime flag and counters of
 * NoiseChunkSamplerReuseMixin. -Dbons_and_furious.chunkSamplerReuse=false keeps one mapping per call;
 * .metrics=true counts and prints a summary when the JVM exits.
 */
public final class SamplerReuse {
    public static final boolean ACTIVE = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.chunkSamplerReuse", "true"));
    static final boolean METRICS = Boolean.getBoolean("bons_and_furious.chunkSamplerReuse.metrics");
    public static final LongAdder SERVED = new LongAdder(), MADE = new LongAdder();

    static {
        if (METRICS) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Bons and Furious vanilla_chunk_climate_sampler_reuse: samplers made "
                    + MADE.sum() + ", second requests answered with the first sampler " + SERVED.sum()), "Bons and Furious sampler reuse summary"));
        }
    }

    private SamplerReuse() {
    }

    public static void served() {
        if (METRICS) SERVED.increment();
    }

    public static void made() {
        if (METRICS) MADE.increment();
    }
}
