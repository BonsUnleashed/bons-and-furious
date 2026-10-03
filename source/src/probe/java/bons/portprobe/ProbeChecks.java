package bons.portprobe;
import bons.furious.guard.Guards;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

final class ProbeChecks {
    static Map<String,Object> forceTargets(boolean client) throws Exception {
        JsonObject targets;
        try(var in=ProbeChecks.class.getResourceAsStream("/probe-targets.json")) {
            targets=JsonParser.parseString(new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
        Map<String,Object> out=new TreeMap<>();
        List<String> failures=new ArrayList<>();
        for(var e:targets.entrySet()) {
            JsonObject row=e.getValue().getAsJsonObject();
            if(!client && row.get("client").getAsBoolean())continue;
            String key=e.getKey();Guards.Decision decision=Guards.decide(key);
            Map<String,Object> detail=new LinkedHashMap<>();detail.put("decision",decision.toString());
            // Composite compatibility patches can require two optional mods; neither a missing dependency nor its skipped patch is a failure.
            if(decision.state()==Guards.State.MISMATCH && !decision.detail().contains("not found"))failures.add(key+": "+decision.detail());
            if(decision.state()==Guards.State.APPLY) {
                Map<String,Object> classes=new TreeMap<>();
                for(var target:row.getAsJsonArray("targets")) {
                    String name=target.getAsString();
                    try {
                        Class<?> type=Class.forName(name,false,ProbeChecks.class.getClassLoader());
                        Set<String> applied=new TreeSet<>();
                        for(var method:type.getDeclaredMethods()) {
                            var merged=method.getAnnotation(MixinMerged.class);
                            if(merged!=null && merged.mixin().startsWith("bons."))applied.add(merged.mixin());
                        }
                        classes.put(name,applied);
                    } catch(Throwable t) { classes.put(name,t.toString());failures.add(key+": "+t); }
                }
                detail.put("classes",classes);
            }
            out.put(key,detail);
        }
        out.put("failures",failures);out.put("passed",failures.isEmpty());
        return out;
    }
}
