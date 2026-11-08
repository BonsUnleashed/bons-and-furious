package bons.furious.patch.mahoutsukai_coo49;

import java.util.HashSet;
import net.minecraft.world.entity.Entity;

/**
 * mahoutsukai_all_entities_memo (client): what MahouEntityLookupMixin adds to the vanilla EntityLookup. The version counts
 * every add / remove call; the kept set is the one Mahou Tsukai's ClientProxy.getAllEntities built for that version and
 * for that value of the entity-id change counter (MahouEntitySets). Member names carry the group (shared vanilla class).
 */
public interface MahouEntityLookupDuck {
    int bons$mahoutsukaiCoo49Version();

    /** True for good once an entity whose class has its own hashCode or equals was offered to this lookup's add. */
    boolean bons$mahoutsukaiCoo49Odd();

    HashSet<Entity> bons$mahoutsukaiCoo49KeptSet();

    int bons$mahoutsukaiCoo49KeptVersion();

    int bons$mahoutsukaiCoo49KeptIds();

    void bons$mahoutsukaiCoo49Keep(HashSet<Entity> set, int version, int ids);
}
