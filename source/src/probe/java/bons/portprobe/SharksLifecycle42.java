package bons.portprobe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Compare guarded listener entry points with unchanged installed procedure bodies. */
public final class SharksLifecycle42 {
    public static Map<String, Object> run(ServerLevel level) throws Exception {
        List<Entity> created = new ArrayList<>();
        int checks = 0;
        int x = level.getSharedSpawnPos().getX(), z = level.getSharedSpawnPos().getZ();
        try {
            Mob guardian = EntityType.GUARDIAN.create(level);
            Mob unrelated = EntityType.COW.create(level);
            if (guardian == null || unrelated == null) throw new AssertionError("Missing listener fixture");
            created.add(guardian); created.add(unrelated);
            guardian.moveTo(x, 300, z, 0, 0);
            unrelated.moveTo(x + 20, 300, z, 0, 0);
            level.getChunk(x >> 4, z >> 4);
            if (!level.addFreshEntity(guardian)) throw new AssertionError("Guardian insertion failed");
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                if (!BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("benssharks")) continue;
                Entity entity = type.create(level);
                if (entity == null) continue;
                created.add(entity);
                entity.moveTo(x + 3, 300, z, 0, 0);
                String simple = entity.getClass().getSimpleName().replace("Entity", "");
                if (!java.util.Set.of("BaskingShark", "BlueShark", "BonnetheadShark", "BullShark",
                        "CookiecutterShark", "GreenlandShark", "MakoShark", "Megalodon", "NurseShark",
                        "Remora", "Shrak", "TigerShark", "WhitetipShark").contains(simple)) continue;
                Class<?> procedure;
                try { procedure = Class.forName("net.mcreator.sharks.procedures." + simple + "OnInitialEntitySpawnProcedure"); }
                catch (ClassNotFoundException absent) { continue; }
                var listener = procedure.getMethod("onEntityTick", EntityTickEvent.Pre.class);
                var execute = procedure.getMethod("execute", net.minecraft.world.level.LevelAccessor.class,
                        double.class, double.class, double.class, Entity.class);
                for (Entity subject : new Entity[]{entity, unrelated}) {
                    for (Entity initial : new Entity[]{null, unrelated}) {
                        guardian.setTarget(initial instanceof net.minecraft.world.entity.LivingEntity living ? living : null);
                        execute.invoke(null, level, subject.getX(), subject.getY(), subject.getZ(), subject);
                        var expected = guardian.getTarget();
                        guardian.setTarget(initial instanceof net.minecraft.world.entity.LivingEntity living ? living : null);
                        listener.invoke(null, new EntityTickEvent.Pre(subject));
                        if (guardian.getTarget() != expected) throw new AssertionError("Listener target mismatch " + simple);
                        checks++;
                    }
                }
            }
            if (checks < 40) throw new AssertionError("Insufficient listener coverage " + checks);
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.fromString("40404040-4242-4242-4242-404040404040"), "SharksProbe"));
            int itemChecks = 0;
            var spetum = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("benssharks", "spetum"));
            if (!(spetum instanceof net.mcreator.sharks.item.SpetumItem)) throw new AssertionError("No Spetum");
            for (var hand : net.minecraft.world.InteractionHand.values()) {
                for (var item : new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.STICK, spetum}) {
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                    var stack = new net.minecraft.world.item.ItemStack(item);
                    net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            stack, tag -> tag.putString("geckoAnim", "probe_animation"));
                    player.setItemInHand(hand, stack);
                    net.mcreator.sharks.init.ItemAnimationFactory.animatedItems(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
                    String animation = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getString("geckoAnim");
                    if (!animation.equals(item == spetum ? "" : "probe_animation")) throw new AssertionError("Spetum hand state mismatch");
                    itemChecks++;
                }
            }
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            int feedingChecks = 0;
            var barracudaType = BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("benssharks", "barracuda"));
            for (var food : new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.COD,
                    net.minecraft.world.item.Items.GOLDEN_APPLE, net.minecraft.world.item.Items.STICK}) {
                List<String> outcomes = new ArrayList<>();
                for (boolean listener : new boolean[]{false, true}) {
                    var fish = (Mob) barracudaType.create(level);
                    if (fish == null) throw new AssertionError("No barracuda");
                    created.add(fish);
                    fish.moveTo(x, 300, z, 0, 0);
                    fish.setHealth(fish.getMaxHealth() / 2);
                    var drop = new net.minecraft.world.entity.item.ItemEntity(level, x + .2, 300, z,
                            new net.minecraft.world.item.ItemStack(food, 3));
                    created.add(drop);
                    if (!level.addFreshEntity(drop)) throw new AssertionError("No feeding drop");
                    if (listener) net.mcreator.sharks.procedures.BarracudaEatProcedure.onEntityTick(new EntityTickEvent.Pre(fish));
                    else net.mcreator.sharks.procedures.BarracudaEatProcedure.execute(level, fish.getX(), fish.getY(), fish.getZ(), fish);
                    outcomes.add(fish.getHealth() + ":" + fish.getActiveEffects().stream().map(Object::toString).sorted().toList()
                            + ":" + drop.getItem().getCount() + ":" + drop.isRemoved());
                    drop.discard(); fish.discard();
                }
                if (!outcomes.get(0).equals(outcomes.get(1))) throw new AssertionError("Barracuda feeding mismatch " + outcomes);
                feedingChecks++;
            }
            return Map.of("passed", true, "guardian_target_comparisons", checks, "server_item_hand_checks", itemChecks,
                    "feeding_comparisons", feedingChecks);
        } finally { for (Entity entity : created) entity.discard(); }
    }
}