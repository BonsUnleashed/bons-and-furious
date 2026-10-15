package bons.portprobe;

import bons.furious.patch.jei_deferral.Deferral;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Optional client-only recipe content/order evidence; never shipped. */
@JeiPlugin
public final class JeiOracle42 implements IModPlugin {
    private static IJeiRuntime runtime;
    private static int starts, stops;

    public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("bons_port_probe", "recipes42"); }
    public void onRuntimeAvailable(IJeiRuntime value) {
        runtime = value;
        starts++;
        if (Boolean.getBoolean("bons_and_furious.jeiOracle42")) {
            var originals = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.ANVIL).includeHidden().get().limit(2).toList();
            if (originals.size() != 2) throw new AssertionError("Missing repair recipes for late-plugin oracle");
            for (int i = 0; i < originals.size(); i++) {
                var original = originals.get(i);
                var uid = ResourceLocation.fromNamespaceAndPath("bons_port_probe", "late_" + i);
                var marker = new mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe() {
                    public ResourceLocation getUid() { return uid; }
                    public List<ItemStack> getLeftInputs() { return original.getLeftInputs(); }
                    public List<ItemStack> getRightInputs() { return original.getRightInputs(); }
                    public List<ItemStack> getOutputs() { return original.getOutputs(); }
                };
                runtime.getRecipeManager().addRecipes(RecipeTypes.ANVIL, List.of(marker));
            }
        }
    }
    public void onRuntimeUnavailable() { runtime = null; stops++; }

    public static boolean ready() { return runtime != null && !Deferral.busy(); }

    public static Map<String, Object> dump() throws Exception {
        if (!ready()) throw new AssertionError("JEI not ready or deferred lanes unfinished");
        if (Boolean.getBoolean("bons_and_furious.jeiReconnect42") && (starts != 2 || stops < 1))
            throw new AssertionError("Actual JEI reconnect not observed: " + starts + "/" + stops);
        List<String> anvil = new ArrayList<>(), grind = new ArrayList<>();
        for (var recipe : runtime.getRecipeManager().createRecipeLookup(RecipeTypes.ANVIL).includeHidden().get().toList())
            anvil.add(recipe.getUid() + "|" + stacks(recipe.getLeftInputs()) + "|" + stacks(recipe.getRightInputs()) + "|" + stacks(recipe.getOutputs()));
        for (var recipe : runtime.getRecipeManager().createRecipeLookup(RecipeTypes.GRINDSTONE).includeHidden().get().toList())
            grind.add(recipe.getUid() + "|" + stacks(recipe.getTopInputs()) + "|" + stacks(recipe.getBottomInputs()) + "|" + stacks(recipe.getOutputs())
                    + "|" + recipe.getMinXpReward() + "|" + recipe.getMaxXpReward());
        if (anvil.isEmpty() || grind.isEmpty()) throw new AssertionError("Empty JEI oracle");
        if (!anvil.get(anvil.size() - 2).startsWith("bons_port_probe:late_0|")
                || !anvil.get(anvil.size() - 1).startsWith("bons_port_probe:late_1|"))
            throw new AssertionError("Later-plugin recipes not retained at end in insertion order");
        Map<String, Object> out = new TreeMap<>();
        out.put("anvil", digest("anvil", anvil));
        out.put("grindstone", digest("grindstone", grind));
        out.put("starts", starts); out.put("stops", stops);
        out.put("finished_runs", Deferral.finishedRuns());
        out.put("added", Deferral.addedLastRun());
        var lifecycle = new bons.furious.patch.jei_deferral.JeiDeferralPlugin();
        int finishedBefore = Deferral.finishedRuns();
        for (int i = 0; i < 2; i++) {
            Deferral.arm();
            Deferral.scheduleAnvil(java.util.stream.Stream.empty(), 0);
            if (!Deferral.busy()) throw new AssertionError("Cancellation fixture not armed");
            lifecycle.onRuntimeUnavailable();
            if (Deferral.busy() || Deferral.takeAnvilArm() || Deferral.takeGrindstoneArm())
                throw new AssertionError("Cancelled JEI state leaked");
            lifecycle.onRuntimeAvailable(runtime);
            if (Deferral.busy() || Deferral.finishedRuns() != finishedBefore)
                throw new AssertionError("Empty runtime restart leaked work");
        }
        out.put("cancellation_restart_checks", 2);
        out.put("passed", true);
        return out;
    }

    private static String stacks(List<ItemStack> stacks) {
        List<String> values = new ArrayList<>();
        for (ItemStack stack : stacks)
            values.add(stack.isEmpty() ? "EMPTY" : canonical(stack.save(Minecraft.getInstance().level.registryAccess())));
        return values.toString();
    }

    private static String canonical(net.minecraft.nbt.Tag tag) {
        if (tag instanceof net.minecraft.nbt.CompoundTag compound) {
            Map<String, String> values = new TreeMap<>();
            for (String key : compound.getAllKeys()) values.put(key, canonical(compound.get(key)));
            return values.toString();
        }
        if (tag instanceof net.minecraft.nbt.ListTag list) {
            List<String> values = new ArrayList<>();
            for (var entry : list) values.add(canonical(entry));
            return values.toString();
        }
        return tag.toString();
    }

    private static Map<String, Object> digest(String name, List<String> rows) throws Exception {
        byte[] bytes = (String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
        Files.write(Path.of("jei42-" + name + ".txt"), bytes);
        return Map.of("count", rows.size(), "ordered_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
}