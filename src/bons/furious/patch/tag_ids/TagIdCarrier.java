package bons.furious.patch.tag_ids;

/**
 * vanilla_tag_membership_ids (Minecraft 1.20.1 on Forge 47.4.16; both sides): implemented by TagKey through
 * bons.furious.mixin.tag_ids.TagKeyIdMixin. The id is a dense number for the tag key's CONTENT (registry and location),
 * assigned by {@link TagIds#idOf} the first time a key instance is asked about; 0 = not assigned yet. Equal keys always get
 * the same id, so a comparison of ids is a comparison by TagKey.equals.
 */
public interface TagIdCarrier {
    int bons$tagId();

    void bons$tagId(int id);
}
