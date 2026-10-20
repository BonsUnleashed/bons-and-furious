package bons.furious.mixin.vanilla_zip_index;

import bons.furious.patch.vanilla_zip_index.ZipListings;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_zip_pack_listing_index (Minecraft 1.20.1 on Forge 47.4.16, client and server): FilePackResources.listResources
 * (m_8031_), the ZipFile.entries() call that feeds vanilla's filter loop. ZipListings answers it with the bucket of
 * type/namespace/path/ from a per-ZipFile index (built once per open ZipFile, central-directory order); vanilla's loop then runs
 * unchanged on it, so the output gets the same calls in the same order (see ZipListings). The pack's index and its lock live in
 * the two fields added here. Fusion's @ModifyVariable of the output at HEAD, its other hooks and CreativeCore's accessor are
 * untouched. Priority 1500: a mod that replaced listResources with an @Overwrite would otherwise stop the game at start ("cannot
 * inject into ... merged by ..."); with require = 0 and that mod's mixin untested (Neighbours), the switch then steps aside.
 */
@Mixin(value = FilePackResources.class, remap = false, priority = 1500)
public abstract class FilePackResourcesZipIndexMixin implements ZipListings.Holder {
    @Unique
    private volatile ZipListings.Index bons$zipIndex;
    @Unique
    private final Object bons$zipIndexLock = new Object();

    @Override
    public ZipListings.Index bons$zipIndex() {
        return this.bons$zipIndex;
    }

    @Override
    public void bons$zipIndex(ZipListings.Index index) {
        this.bons$zipIndex = index;
    }

    @Override
    public Object bons$zipIndexLock() {
        return this.bons$zipIndexLock;
    }

    /** The target method's own parameters follow the Operation (no MixinExtras sugar, so no LocalRef objects per call). */
    @WrapOperation(method = "m_8031_", require = 0, at = @At(value = "INVOKE", target = "Ljava/util/zip/ZipFile;entries()Ljava/util/Enumeration;"))
    private Enumeration<? extends ZipEntry> bons$indexedEntries(ZipFile zip, Operation<Enumeration<? extends ZipEntry>> original,
                                                               PackType type, String namespace, String path, PackResources.ResourceOutput output) {
        return ZipListings.entries(this, zip, type, namespace, path, original);
    }
}
