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
 * modernfix_cube_bake_memo (ModernFix compact_entity_models; 1.21.1 tested build modernfix-neoforge-5.27.24+mc1.21.1 on
 * Minecraft 1.21.1 / NeoForge 21.1.252, client). Mojang member names.
 *
 * The whole of CubeDefinition.bake (including ModernFix's cube-sharing wrapper inside it) goes through CubeBakeMemo,
 * which returns the cube ModernFix shares for this definition while its inputs and the texture size are unchanged, and
 * otherwise runs the method as before. Stays off without ModernFix's mixin (see CubeBakeMemo).
 *
 * Ported to 1.21.1: unchanged - CubeDefinition.bake and its fields are the same source, and ModernFix 5.27.24's
 * CubeDefinitionMixin (handler modernfix$deduplicateCube, @WrapOperation on the same NEW ModelPart$Cube) decompiles
 * identically to 5.27.77's.
 */
@Mixin(value = CubeDefinition.class, remap = false)
public abstract class CubeBakeMemoMixin implements CubeBakeMemo.Holder {
    @Shadow
    @Final
    private Vector3f origin;
    @Shadow
    @Final
    private Vector3f dimensions;
    @Shadow
    @Final
    private Set<Direction> visibleFaces;
    @Unique
    private CubeBakeMemo.Entry bons$cubeMemo;

    @WrapMethod(method = "bake")
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
        return this.origin;
    }

    @Override
    public Vector3f bons$dimensions() {
        return this.dimensions;
    }

    @Override
    public Set<Direction> bons$visibleFaces() {
        return this.visibleFaces;
    }
}
