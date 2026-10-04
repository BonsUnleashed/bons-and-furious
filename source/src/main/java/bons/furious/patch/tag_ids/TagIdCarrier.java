package bons.furious.patch.tag_ids;

/**
 * vanilla_tag_membership_ids (Minecraft 1.21.1 with NeoForge 21.1.252; both sides): implemented by TagKey through
 * bons.furious.mixin.tag_ids.TagKeyIdMixin. The id is a dense number for the tag key's CONTENT (registry and location),
 * assigned by {@link TagIds#idOf} the first time a key instance is asked about; 0 = not assigned yet. Equal keys always get
 * the same id, so a comparison of ids is a comparison by TagKey.equals.
 *
 * Ported to 1.21.1: unchanged (TagKey is still the record (registry, location) with generated equals/hashCode).
 */
public interface TagIdCarrier {
    int bons$tagId();

    void bons$tagId(int id);
}
