package bons.portprobe;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Isolated development smoke test; never shipped.
 *
 * <p>1.0.29 port additions: {@code -Dbons_and_furious.probeWorldTicks=N} (default 120, the 1.0.27 port's value) sets how
 * long the world runs before the checks; {@code -Dbons_and_furious.probeCommands=<file>} ("worldTick command" per line,
 * relative to the game directory) runs commands on the integrated server at those world ticks; afterwards every
 * shadow helper's counters are harvested (ShadowHarvest), and any shadow mismatch fails the probe.
 */
@EventBusSubscriber(modid="bons_port_probe",value=Dist.CLIENT)
public final class ClientProbe {
    private static int ticks,worldTicks;
    private static boolean finished;
    private static NavigableMap<Integer,List<String>> commands;
    private static final List<Map<String,Object>> commandLog=Collections.synchronizedList(new ArrayList<>());
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
        if(mc.level!=null && mc.player!=null) {
            runCommands(mc,worldTicks);
            worldTicks++;
        }
        int wanted=Integer.getInteger("bons_and_furious.probeWorldTicks",120);
        if(worldTicks<wanted && ticks<Math.max(2400,wanted*4))return;
        finished=true;Map<String,Object> result=new LinkedHashMap<>();
        try {
            result.put("world_loaded",mc.level!=null);result.put("world_ticks",worldTicks);
            if(mc.level==null)throw new AssertionError("World did not load; screen="+mc.screen);
            result.put("dimension",mc.level.dimension().location().toString());
            result.put("target_checks",ProbeChecks.forceTargets(true));
            result.put("behavior",BehaviorChecks.run());
            result.put("commands",new ArrayList<>(commandLog));
            Map<String,Object> shadow=ShadowHarvest.harvest(true);
            result.put("shadow",shadow);
            boolean passed=Boolean.TRUE.equals(((Map<?,?>)result.get("target_checks")).get("passed")) && ShadowHarvest.clean(shadow);
            result.put("passed",passed);
            Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),message->System.out.println("BONS_PORT_SCREENSHOT "+message.getString()));
        } catch(Throwable t) {result.put("passed",false);result.put("error",t.toString());t.printStackTrace();}
        try {Files.writeString(Path.of("client-probe-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");}
        catch(Exception e) {throw new RuntimeException(e);}
        System.out.println("BONS_CLIENT_PROBE "+result.get("passed")+" shadow="+((Map<?,?>)result.getOrDefault("shadow",Map.of())).get("_summary"));
        mc.stop();
    }

    private static void runCommands(Minecraft mc,int worldTick) {
        if(commands==null) {
            commands=new TreeMap<>();
            String file=System.getProperty("bons_and_furious.probeCommands");
            if(file!=null && !file.isBlank()) try {
                for(String line:Files.readAllLines(Path.of(file),StandardCharsets.UTF_8)) {
                    line=line.strip();
                    if(line.isEmpty()||line.startsWith("#"))continue;
                    int space=line.indexOf(' ');
                    commands.computeIfAbsent(Integer.parseInt(line.substring(0,space)),k->new ArrayList<>()).add(line.substring(space+1).strip());
                }
            } catch(Exception e) {commandLog.add(Map.of("error","probe commands: "+e));}
        }
        var server=mc.getSingleplayerServer();
        if(server==null)return;
        for(String command:commands.getOrDefault(worldTick,List.of())) {
            server.execute(()->{
                Map<String,Object> row=new LinkedHashMap<>();row.put("tick",worldTick);row.put("command",command);
                try {server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),command);row.put("ok",true);}
                catch(Throwable t) {row.put("ok",false);row.put("error",t.toString());}
                commandLog.add(row);
            });
        }
    }
}
