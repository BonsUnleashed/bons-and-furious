package bons.portprobe;
import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Isolated development smoke test; never shipped. */
@EventBusSubscriber(modid="bons_port_probe",value=Dist.CLIENT)
public final class ClientProbe {
    private static int ticks,worldTicks;
    private static boolean finished;
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if(finished || !Boolean.getBoolean("bons_and_furious.clientProbe"))return;
        Minecraft mc=Minecraft.getInstance();ticks++;
        if(mc.screen instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen screen) {
            // NeoForge only offers this button for warnings, never for fatal loading errors.
            String label=net.neoforged.fml.i18n.FMLTranslations.parseMessage("fml.button.continue.launch");
            for(var child:screen.children()) if(child instanceof net.minecraft.client.gui.components.Button button
                    && button.getMessage().getString().equals(label)) {
                System.out.println("BONS_PROBE_LOADING_WARNINGS "+net.neoforged.fml.ModLoader.getLoadingIssues());
                button.onPress();break;
            }
        }
        // This harness only opens its disposable BonsPortProbe fixture.
        if(mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen screen) {
            for(var child:screen.children()) if(child instanceof net.minecraft.client.gui.components.Button button
                    && button.getMessage().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinConfirmButton"))) {
                button.onPress();break;
            }
        }
        if(mc.level!=null && mc.player!=null)worldTicks++;
        if(worldTicks<120 && ticks<2400)return;
        finished=true;Map<String,Object> result=new LinkedHashMap<>();
        try {
            result.put("world_loaded",mc.level!=null);result.put("world_ticks",worldTicks);
            if(mc.level==null)throw new AssertionError("World did not load; screen="+mc.screen);
            result.put("dimension",mc.level.dimension().location().toString());
            result.put("target_checks",ProbeChecks.forceTargets(true));
            result.put("behavior",BehaviorChecks.run());
            result.put("passed",((Map<?,?>)result.get("target_checks")).get("passed"));
            Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),message->System.out.println("BONS_PORT_SCREENSHOT "+message.getString()));
        } catch(Throwable t) {result.put("passed",false);result.put("error",t.toString());t.printStackTrace();}
        try {Files.writeString(Path.of("client-probe-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");}
        catch(Exception e) {throw new RuntimeException(e);}
        System.out.println("BONS_CLIENT_PROBE "+result);
        mc.stop();
    }
}
