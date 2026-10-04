package bons.furious.mixin.armorbake_c2;

import bons.furious.patch.armorbake_c2.CubeBakeMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * modernfix_cube_bake_memo (ModernFix 5.27.77 compact_entity_models on Minecraft 1.20.1, client).
 *
 * The whole of CubeDefinition.bake (including ModernFix's cube-sharing wrapper inside it) goes through CubeBakeMemo,
 * which returns the cube ModernFix shares for this definition while its inputs and the texture size are unchanged, and
 * otherwise runs the method as before. Stays off without ModernFix's mixin (see CubeBakeMemo).
 */
@Mixin(value = CubeDefinition.class, remap = false)
public abstract class CubeBakeMemoMixin implements CubeBakeMemo.Holder {
    @Shadow
    @Final
    private Vector3f f_171435_;                  // origin
    @Shadow
    @Final
    private Vector3f f_171436_;                  // dimensions
    @Shadow
    @Final
    private Set<Direction> f_271491_;            // visibleFaces
    @Unique
    private CubeBakeMemo.Entry bons$cubeMemo;

    @WrapMethod(method = "m_171455_")
    private ModelPart.Cube bons$rememberedCube(int texWidth, int texHeight, Operation<ModelPart.Cube> original) {
        return CubeBakeMemo.bake(this, texWidth, texHeight, original);
    }

    @Override
    public CubeBakeMemo.Entry bons$cubeMemo() {
        return this.bons$cubeMemo;
    }

    @Override
    public void bons$cubeMemo(CubeBakeMemo.Entry entry) {
        this.bons$cubeMemo = entry;
    }

    @Override
    public Vector3f bons$origin() {
        return this.f_171435_;
    }

    @Override
    public Vector3f bons$dimensions() {
        return this.f_171436_;
    }

    @Override
    public Set<Direction> bons$visibleFaces() {
        return this.f_271491_;
    }
}
