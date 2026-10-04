package bons.furious.mixin.create_contraptions;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * create_collision_single_pass (Minecraft 1.20.1, both sides): read-only access to a shape's voxel grid (f_83211_) and its
 * per-axis coordinate list (m_7700_, called virtually so subclasses such as Radium's specialized shapes answer for
 * themselves), the two things Shapes.joinUnoptimized reads from its operands. Changes nothing.
 */
@Mixin(value = VoxelShape.class, remap = false)
public interface VoxelShapeAccessor {
    @Accessor("f_83211_")
    DiscreteVoxelShape bons$voxels();

    @Invoker("m_7700_")
    DoubleList bons$coords(Direction.Axis axis);
}
