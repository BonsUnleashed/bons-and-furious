package bons.furious.mixin.alexscaves;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.server.block.poi.ACPOIRegistry;
import com.github.alexmodguy.alexscaves.server.entity.util.MagnetUtil;
import com.github.alexmodguy.alexscaves.server.entity.util.MagneticEntityAccessor;
import com.github.alexmodguy.alexscaves.server.message.PlayerJumpFromMagnetMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * alexscaves_magnet_query (Alex's Caves 2.0.2).
 *
 * MagnetUtil.tickMagnetism ran two point-of-interest queries around every magnetic entity on every server tick, one
 * for attracting and one for repelling magnets. ac$nearbyMagnets runs one query over both magnet types and splits the
 * positions into the two lists in the order the query returns them, so the pull and push callbacks see the same
 * positions in the same order as before. The rest of tickMagnetism is unchanged; it reads LivingEntity.jumping through
 * LivingEntityJumpingAccessor because the field is only public through Alex's Caves' access transformer.
 */
@Mixin(value = MagnetUtil.class, remap = false)
public abstract class MagnetUtilQueryMixin {
    @Shadow
    public static Vec3 getEntityMagneticDelta(Entity entity) {
        throw new AssertionError();
    }

    @Shadow
    public static void setEntityMagneticDelta(Entity entity, Vec3 vec3) {
        throw new AssertionError();
    }

    @Shadow
    public static Direction getEntityMagneticDirection(Entity entity) {
        throw new AssertionError();
    }

    @Shadow
    public static void setEntityMagneticDirection(Entity entity, Direction direction) {
        throw new AssertionError();
    }

    @Shadow
    public static boolean attachesToMagnets(Entity entity) {
        throw new AssertionError();
    }

    @Shadow
    public static boolean isEntityOnMovingMetal(Entity entity) {
        throw new AssertionError();
    }

    @Shadow
    private static Direction getStandingOnMagnetSurface(Entity entity) {
        throw new AssertionError();
    }

    @Shadow
    private static Direction calculateClosestDirection(Entity entity) {
        throw new AssertionError();
    }

    @Shadow
    private static BlockPos getSamplePosForDirection(Entity entity, Direction direction, float expand) {
        throw new AssertionError();
    }

    @Shadow
    private static Vec3 processMovementControls(float dist, LivingEntity living, Direction dir) {
        throw new AssertionError();
    }

    /**
     * @author BonsUnleashed
     * @reason One point-of-interest query per tick for both magnet types instead of two.
     */
    @Overwrite
    public static void tickMagnetism(Entity entity) {
        if (!entity.m_9236_().f_46443_ && entity.m_9236_() instanceof ServerLevel serverLevel) {
            int range = 5;
            Stream<BlockPos>[] magnets = ac$nearbyMagnets(entity.m_20183_(), serverLevel, range);
            Stream<BlockPos> repels = magnets[1];
            Stream<BlockPos> attracts = magnets[0];
            attracts.forEach(magnet -> {
                Vec3 center = Vec3.m_82512_(magnet);
                double distance = Mth.m_14008_(Math.sqrt(entity.m_20238_(center)) / range, 0.0, 1.0);
                Vec3 pull = Vec3.m_82512_(magnet).m_82546_(entity.m_20182_());
                Vec3 pullNorm = pull.m_82553_() < 1.0 ? pull : pull.m_82541_();
                Vec3 pullScale = pullNorm.m_82490_((1.0 - distance) * 0.25);
                setEntityMagneticDelta(entity, getEntityMagneticDelta(entity).m_82490_(0.9).m_82549_(pullScale));
            });
            repels.forEach(magnet -> {
                Vec3 center = Vec3.m_82512_(magnet);
                double distance = Mth.m_14008_(Math.sqrt(entity.m_20238_(center)) / range, 0.0, 1.0);
                Vec3 pull = entity.m_20182_().m_82546_(Vec3.m_82512_(magnet));
                Vec3 pullNorm = pull.m_82553_() < 1.0 ? pull : pull.m_82541_();
                Vec3 pullScale = pullNorm.m_82490_((1.0 - distance) * 0.25);
                setEntityMagneticDelta(entity, getEntityMagneticDelta(entity).m_82490_(0.9).m_82549_(pullScale));
            });
        }
        Vec3 vec3 = getEntityMagneticDelta(entity);
        Direction dir = getEntityMagneticDirection(entity);
        MagneticEntityAccessor magneticAccessor = (MagneticEntityAccessor) entity;
        boolean attatchesToMagnets = AlexsCaves.COMMON_CONFIG.walkingOnMagnets.get() && attachesToMagnets(entity);
        float progress = magneticAccessor.getAttachmentProgress(1.0F);
        if (vec3 != Vec3.f_82478_) {
            Direction standingOnDirection = getStandingOnMagnetSurface(entity);
            float overrideByWalking = 1.0F;
            if (entity instanceof LivingEntity living) {
                if (((LivingEntityJumpingAccessor) living).bons$isJumping() && standingOnDirection == dir) {
                    if (living.m_9236_().f_46443_) {
                        AlexsCaves.sendMSGToServer(new PlayerJumpFromMagnetMessage(living.m_19879_(), ((LivingEntityJumpingAccessor) living).bons$isJumping()));
                    }
                    magneticAccessor.postMagnetJump();
                }
                float detract = living.f_20900_ * living.f_20900_ + living.f_20901_ * living.f_20901_ + living.f_20902_ * living.f_20902_;
                overrideByWalking -= Math.min(1.0, Math.sqrt(detract) * 0.7F);
            }
            if (!isEntityOnMovingMetal(entity)) {
                if (attatchesToMagnets) {
                    Vec3 vec31;
                    if (dir == Direction.DOWN && standingOnDirection == null) {
                        vec31 = vec3.m_82542_(overrideByWalking, overrideByWalking, overrideByWalking);
                        entity.m_20256_(entity.m_20184_().m_82549_(vec31));
                        entity.m_6210_();
                    } else {
                        magneticAccessor.stepOnMagnetBlock(getSamplePosForDirection(entity, dir, 0.5F));
                        float f1 = Math.abs(dir.m_122429_());
                        float f2 = Math.abs(dir.m_122430_());
                        float f3 = Math.abs(dir.m_122431_());
                        vec31 = vec3.m_82542_(overrideByWalking * f1, overrideByWalking * f2, overrideByWalking * f3);
                        if (entity.m_20089_() == Pose.SWIMMING) {
                            entity.m_20124_(Pose.STANDING);
                        }
                        if (entity instanceof LivingEntity living) {
                            vec31 = processMovementControls(0.0F, living, dir);
                        }
                        entity.m_20256_(vec31);
                    }
                    Direction closest = calculateClosestDirection(entity);
                    if (closest != null && closest != Direction.DOWN) {
                        entity.f_19789_ = 0.0F;
                    }
                    if (closest != dir && magneticAccessor.canChangeDirection() && (progress == 1.0F || closest == Direction.UP)) {
                        entity.m_20256_(entity.m_20184_().m_82520_(0.0, 0.4F, 0.0));
                        setEntityMagneticDirection(entity, closest);
                        entity.m_6210_();
                        entity.m_20124_(Pose.STANDING);
                    }
                } else {
                    entity.m_20256_(entity.m_20184_().m_82549_(vec3));
                }
            }
            setEntityMagneticDelta(entity, vec3.m_82490_(0.08F));
        }
        if (!attatchesToMagnets && dir != Direction.DOWN) {
            setEntityMagneticDirection(entity, Direction.DOWN);
            entity.m_6210_();
            entity.m_20124_(Pose.STANDING);
        }
    }

    /**
     * One query over the attracting and repelling magnet POI types, split into the two position lists in query order:
     * [0] attracting, [1] repelling.
     */
    @Unique
    @SuppressWarnings("unchecked")
    private static Stream<BlockPos>[] ac$nearbyMagnets(BlockPos pos, ServerLevel level, int range) {
        ResourceKey<PoiType> attracting = ACPOIRegistry.ATTRACTING_MAGNETS.getKey();
        ResourceKey<PoiType> repelling = ACPOIRegistry.REPELLING_MAGNETS.getKey();
        List<BlockPos> attracts = new ArrayList<>();
        List<BlockPos> repels = new ArrayList<>();
        try (Stream<PoiRecord> records = level.m_8904_().m_27181_(type -> type.m_203565_(attracting) || type.m_203565_(repelling),
                pos, range, PoiManager.Occupancy.ANY)) {
            records.forEach(record -> {
                if (record.m_218018_().m_203565_(attracting)) {
                    attracts.add(record.m_27257_());
                }
                if (record.m_218018_().m_203565_(repelling)) {
                    repels.add(record.m_27257_());
                }
            });
        }
        return new Stream[] {attracts.stream(), repels.stream()};
    }
}
