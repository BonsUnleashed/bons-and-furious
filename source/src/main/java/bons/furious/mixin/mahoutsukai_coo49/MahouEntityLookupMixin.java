package bons.furious.mixin.mahoutsukai_coo49;

import bons.furious.patch.mahoutsukai_coo49.MahouEntityLookupDuck;
import bons.furious.patch.mahoutsukai_coo49.MahouEntitySets;
import java.util.HashSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * mahoutsukai_all_entities_memo (vanilla EntityLookup, client dist; applied only while Mahou Tsukai is installed): counts
 * every add (add) and remove (remove) call, the only writers of the lookup's id and UUID maps, with a
 * @ModifyVariable at their HEAD that hands the argument back unchanged; remembers for good once an entity whose class has
 * its own hashCode / equals was offered to add (then no set of this lookup is ever kept; the class is looked at only once
 * such a class was constructed somewhere, MahouEntitySets.oddSeen, so the add costs one counter step and one flag read
 * while none exists); and holds the entity set Mahou Tsukai's ClientProxy.getAllEntities last built for this lookup
 * (MahouEntitySets). Nothing of the lookup's behaviour changes.
 */
@Mixin(value = EntityLookup.class, remap = false)
public abstract class MahouEntityLookupMixin implements MahouEntityLookupDuck {
    @Unique
    private int bons$mahoutsukaiCoo49Version;
    @Unique
    private boolean bons$mahoutsukaiCoo49Odd;
    @Unique
    private HashSet<Entity> bons$mahoutsukaiCoo49Set;
    @Unique
    private int bons$mahoutsukaiCoo49SetVersion;
    @Unique
    private int bons$mahoutsukaiCoo49SetIds;

    @ModifyVariable(method = "add(Lnet/minecraft/world/level/entity/EntityAccess;)V", at = @At("HEAD"), argsOnly = true, require = 1, allow = 1)
    private EntityAccess bons$mahoutsukaiCoo49Added(EntityAccess entity) {
        this.bons$mahoutsukaiCoo49Version++;
        if (MahouEntitySets.oddSeen() && !this.bons$mahoutsukaiCoo49Odd && !MahouEntitySets.plain(entity)) this.bons$mahoutsukaiCoo49Odd = true;
        return entity;
    }

    @Override
    public boolean bons$mahoutsukaiCoo49Odd() {
        return this.bons$mahoutsukaiCoo49Odd;
    }

    @ModifyVariable(method = "remove(Lnet/minecraft/world/level/entity/EntityAccess;)V", at = @At("HEAD"), argsOnly = true, require = 1, allow = 1)
    private EntityAccess bons$mahoutsukaiCoo49Removed(EntityAccess entity) {
        this.bons$mahoutsukaiCoo49Version++;
        return entity;
    }

    @Override
    public int bons$mahoutsukaiCoo49Version() {
        return this.bons$mahoutsukaiCoo49Version;
    }

    @Override
    public HashSet<Entity> bons$mahoutsukaiCoo49KeptSet() {
        return this.bons$mahoutsukaiCoo49Set;
    }

    @Override
    public int bons$mahoutsukaiCoo49KeptVersion() {
        return this.bons$mahoutsukaiCoo49SetVersion;
    }

    @Override
    public int bons$mahoutsukaiCoo49KeptIds() {
        return this.bons$mahoutsukaiCoo49SetIds;
    }

    @Override
    public void bons$mahoutsukaiCoo49Keep(HashSet<Entity> set, int version, int ids) {
        this.bons$mahoutsukaiCoo49Set = set;
        this.bons$mahoutsukaiCoo49SetVersion = version;
        this.bons$mahoutsukaiCoo49SetIds = ids;
    }
}
