package bons.portprobe;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import bons.furious.patch.number_prechecks.FloatGrammar;
import bons.furious.patch.number_prechecks.TextAnimatorParsePrecheck;
import bons.furious.patch.farmersdelight_basket.BasketQuery;
import com.hollingsworth.arsnouveau.common.entity.debug.FixedStack;
import snownee.textanimator.effect.Effect;
import snownee.textanimator.effect.EffectFactory;
import vectorwing.farmersdelight.common.block.entity.Basket;

/** Exact targeted comparisons against JDK/upstream operations in a native disposable game. Never shipped. */
final class DeltaProof47 {
 static Map<String,Object> run(MinecraftServer server) throws Exception {
  Map<String,Object> result=new LinkedHashMap<>();
  Random rng=new Random(1471211);int grammar=0;
  String alphabet="0123456789.+-abcdefABCDEFxXpPNIinty _/😀٠１２\t\n";
  List<String> cases=new ArrayList<>(List.of("0","-0","NaN","Infinity","+Infinity","-Infinity","0x1.fp3",".1e-9","1F","\u0000-0\u0020","", "\u20030"));
  for(int i=0;i<20000;i++){StringBuilder s=new StringBuilder();for(int j=rng.nextInt(40);j>0;j--)s.append(alphabet.charAt(rng.nextInt(alphabet.length())));cases.add(s.toString());}
  for(String s:cases) {
   boolean reject=FloatGrammar.mustThrow(s);
   if(reject) {
    try {Double.parseDouble(s);throw new AssertionError("Rejected valid double: "+s);}catch(NumberFormatException expected){}
    try {Float.parseFloat(s);throw new AssertionError("Rejected valid float: "+s);}catch(NumberFormatException expected){}
   }
   if(FloatGrammar.mustThrow("["+s+"]",1,s.length()+1)!=reject)throw new AssertionError("Substring grammar disagreement");
   grammar++;
  }
  result.put("number_grammar_cases",grammar);
  if(ModList.get().isLoaded("ars_nouveau"))result.put("ars_stack_operations",stacks());
  if(ModList.get().isLoaded("textanimator"))result.put("text_effect_comparisons",effects());
  if(ModList.get().isLoaded("farmersdelight"))result.put("basket_queries",server.submit(()->baskets(server.overworld())).get());
  result.put("passed",true);return result;
 }
 static int stacks() {
  int checks=0;
  for(int max:new int[]{1,2,16,128}) {
   FixedStack<Object> actual=new FixedStack<>(max);Stack<Object> expected=new Stack<>();
   for(int i=0;i<512;i++) {
    if(i%17==0){actual.addAll(List.of("a","b","c"));expected.addAll(List.of("a","b","c"));}
    var iterator=actual.iterator();var expectedIterator=expected.iterator();Object value=i%7==0?null:i;
    while(expected.size()>=max)expected.remove(0);Object a=expected.push(value),b=actual.push(value);
    if(a!=b||!actual.equals(expected)||actual.capacity()!=expected.capacity())throw new AssertionError("FixedStack state/capacity at "+max+":"+i);
    try {iterator.next();throw new AssertionError("FixedStack iterator was not invalidated");}catch(ConcurrentModificationException ok){}
    try {expectedIterator.next();throw new AssertionError("Reference iterator was not invalidated");}catch(ConcurrentModificationException ok){}
    checks++;
   }
  }
  return checks;
 }
 static int effects() {
  int checks=0;boolean enabled=TextAnimatorParsePrecheck.enabled;
  List<String> types=new ArrayList<>(EffectFactory.listTypes());types.addAll(List.of("typewriter","unknown_proof47","Player",""));
  try {
   for(String type:types)for(String param:new String[]{"","c=#ff8800","x=NaN","x=Infinity","x=0x1.fp2","x=-0","x=1e-6","x=true","x=false","flag","x=","==x==abc","x=１２","x=white","x= a"})for(boolean allow:new boolean[]{false,true}) {
    String[] split=param.isEmpty()?new String[]{type}:new String[]{type,param};
    TextAnimatorParsePrecheck.enabled=false;String expected=effect(split,allow);
    TextAnimatorParsePrecheck.enabled=true;String actual=effect(split,allow);
    if(!expected.equals(actual))throw new AssertionError("Effect differs "+Arrays.toString(split)+": "+expected+" / "+actual);
    checks++;
   }
  } finally {TextAnimatorParsePrecheck.enabled=enabled;}
  return checks;
 }
 static String effect(String[] split,boolean allow) {
  try {Effect e=TextAnimatorParsePrecheck.create(split,allow,args->Effect.create((String[])args[0],(boolean)args[1]));return e.getClass().getName()+"|"+e.getName()+"|"+e.serialize();}
  catch(RuntimeException e){return "rejected";} // The upstream caller discards these exceptions.
 }
 static int baskets(ServerLevel level) {
  // Use the player's already visible entity section; loading a distant chunk alone leaves new entities hidden.
  BlockPos pos=level.players().getFirst().blockPosition().above(20);level.getChunk(pos);
  var block=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("farmersdelight","basket"));
  if(!(block instanceof EntityBlock entityBlock))throw new AssertionError("Basket block missing");
  Basket basket=(Basket)entityBlock.newBlockEntity(pos,block.defaultBlockState());
  List<ItemEntity> entities=new ArrayList<>();boolean enabled=BasketQuery.enabled;int checks=0,positive=0;
  try {
   for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)for(int z=-2;z<=2;z++) {
    ItemEntity item=new ItemEntity(level,pos.getX()+.5+x*.6,pos.getY()+.5+y*.6,pos.getZ()+.5+z*.6,new ItemStack(Items.STONE));
    item.setNoGravity(true);level.addFreshEntity(item);entities.add(item);
   }
   for(int round=0;round<3;round++)for(int facing=0;facing<6;facing++) {
    BasketQuery.enabled=false;var expected=basket.getItemsToCollect(level,facing);
    BasketQuery.enabled=true;var actual=basket.getItemsToCollect(level,facing);
    if(!expected.equals(actual)||expected==actual)throw new AssertionError("Basket ordered query differs");
    if(!actual.isEmpty())positive++;
    checks++;
    if(round==1)for(int i=facing;i<entities.size();i+=6)entities.get(i).discard();
   }
   if(positive<6)throw new AssertionError("Basket positive cases not exercised");
   return checks;
  } finally {BasketQuery.enabled=enabled;for(var item:entities)if(!item.isRemoved())item.discard();}
 }
}
