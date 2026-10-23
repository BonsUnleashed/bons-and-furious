package bons.furious.patch.noise;

import bons.furious.guard.Fingerprint;
import bons.pure.config.PureMixinPlugin;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.apache.logging.log4j.LogManager;
import java.util.*;
import java.io.InputStream;

/** Keep unknown replacements intact; only tested bytecode may use the lookup table. */
public final class NoiseMixinPlugin implements IMixinConfigPlugin {
    private final PureMixinPlugin delegate=new PureMixinPlugin();
    private final Set<String> known=new HashSet<>();
    public void onLoad(String p){
        delegate.onLoad(p);
        // Native public-call ABBA: C2ME's existing unrolled sampler is faster.
        // Skip the whole mixin group, including accessors, so it has no hook or
        // field overhead. If early mod discovery is unavailable, stay conservative.
        try {
            var mods=net.neoforged.fml.loading.LoadingModList.get();
            NoiseCompatibility.standAside=mods==null || mods.getModFileById("c2me")!=null || mods.getModFileById("c2meforge")!=null;
        } catch(Throwable t) { NoiseCompatibility.standAside=true; }
        if(NoiseCompatibility.standAside)
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: vanilla_noise_gradient_table leaves the existing sampler unmodified (C2ME or unavailable early mod discovery)");
        try(InputStream in=NoiseMixinPlugin.class.getResourceAsStream("/bons_and_furious.noise.compat.properties")){
            if(in!=null){Properties props=new Properties();props.load(in);for(Object v:props.values())known.add(v.toString());}
        }catch(Exception e){LogManager.getLogger("Bons and Furious").warn("Noise compatibility fingerprints unavailable; keeping existing noise implementation",e);}
    }
    public String getRefMapperConfig(){return delegate.getRefMapperConfig();}
    public boolean shouldApplyMixin(String t,String m){return !NoiseCompatibility.standAside && delegate.shouldApplyMixin(t,m);}
    public void acceptTargets(Set<String>a,Set<String>b){delegate.acceptTargets(a,b);}
    public List<String> getMixins(){return delegate.getMixins();}
    public void preApply(String t,ClassNode c,String m,IMixinInfo i){delegate.preApply(t,c,m,i);}
    public void postApply(String t,ClassNode c,String m,IMixinInfo i){
        delegate.postApply(t,c,m,i);
        if(!m.endsWith(".ImprovedNoiseGradientMixin"))return;
        boolean allowed=false;
        for(MethodNode method:c.methods)if(method.name.equals("sampleAndLerp") && method.desc.equals("(IIIDDDD)D"))
            allowed=known.contains(Fingerprint.of(method));
        NoiseCompatibility.allowed=allowed;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: vanilla_noise_gradient_table {}",allowed?
            "uses exact lazy tables with a verified noise implementation":"steps aside for an unverified noise implementation");
    }
}
