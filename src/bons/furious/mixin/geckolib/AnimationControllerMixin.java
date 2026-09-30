package bons.furious.mixin.geckolib;

import com.eliotlash.mclib.math.Constant;
import com.eliotlash.mclib.math.IValue;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.keyframe.AnimationPoint;
import software.bernie.geckolib.core.keyframe.Keyframe;
import software.bernie.geckolib.core.object.Axis;

/**
 * geckolib_keyframe_locals (GeckoLib 4.8.4, both sides).
 *
 * getAnimationPointAtTick called getCurrentKeyFrameLocation, which allocated a KeyframeLocation record only to have it
 * unpacked on the next line, for every animated bone axis per frame. The same search now runs inline and keeps the
 * frame and its start tick in locals: the first frame whose cumulative length exceeds the tick, else the last frame
 * with the unchanged tick. The resulting AnimationPoint is the same.
 */
@Mixin(value = AnimationController.class, remap = false)
public abstract class AnimationControllerMixin {
    /**
     * @author BonsUnleashed
     * @reason Find the current keyframe inline instead of through a KeyframeLocation object.
     */
    @Overwrite
    private AnimationPoint getAnimationPointAtTick(List<Keyframe<IValue>> frames, double tick, boolean isRotation, Axis axis) {
        double totalFrameLength = 0;
        double frameTick = tick;
        Keyframe<IValue> currentFrame = null;

        for (Keyframe<IValue> frame : frames) {
            if ((totalFrameLength += frame.length()) > tick) {
                currentFrame = frame;
                frameTick = tick - (totalFrameLength - frame.length());
                break;
            }
        }

        if (currentFrame == null)
            currentFrame = frames.get(frames.size() - 1);

        double startValue = currentFrame.startValue().get();
        double endValue = currentFrame.endValue().get();

        if (isRotation) {
            if (!(currentFrame.startValue() instanceof Constant)) {
                startValue = Math.toRadians(startValue);

                if (axis == Axis.X || axis == Axis.Y)
                    startValue *= -1;
            }

            if (!(currentFrame.endValue() instanceof Constant)) {
                endValue = Math.toRadians(endValue);

                if (axis == Axis.X || axis == Axis.Y)
                    endValue *= -1;
            }
        }

        return new AnimationPoint(currentFrame, frameTick, currentFrame.length(), startValue, endValue);
    }
}
