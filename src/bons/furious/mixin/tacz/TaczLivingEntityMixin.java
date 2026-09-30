package bons.furious.mixin.tacz;

import agentcraft.pure.TaCZScope;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.KnockBackModifier;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.entity.shooter.LivingEntityAim;
import com.tacz.guns.entity.shooter.LivingEntityAmmoCheck;
import com.tacz.guns.entity.shooter.LivingEntityBolt;
import com.tacz.guns.entity.shooter.LivingEntityCrawl;
import com.tacz.guns.entity.shooter.LivingEntityDrawGun;
import com.tacz.guns.entity.shooter.LivingEntityFireSelect;
import com.tacz.guns.entity.shooter.LivingEntityHeat;
import com.tacz.guns.entity.shooter.LivingEntityMelee;
import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.LivingEntitySpeedModifier;
import com.tacz.guns.entity.shooter.LivingEntitySprint;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * tacz_sync_scope (TaCZ 1.1.5), part 2 of 2; part 1 is SyncedEntityDataMixin.
 *
 * TaCZ 1.1.5's own com.tacz.guns.mixin.common.LivingEntityMixin (GPL-3.0), member for member, except onTickServerSide:
 * its eight synced-data writes run inside TaCZScope.begin(shooter) / try / finally TaCZScope.end(), so the holder is
 * looked up once instead of eight times and the scope closes even when a write throws. While this switch applies,
 * MixinSquared cancels TaCZ's class (patches/tacz.json) and this copy takes its place. A replacement is needed because
 * no injector into TaCZ's handler can put a finally block around exactly those eight writes; wrapping the whole handler
 * would widen the scope (also to the client) and allocate on every entity tick.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class TaczLivingEntityMixin extends Entity implements IGunOperator, KnockBackModifier {
    @Unique
    private final LivingEntity tacz$shooter = (LivingEntity) (Object) this;
    @Unique
    private final ShooterDataHolder tacz$data = new ShooterDataHolder();
    @Unique
    private final LivingEntityDrawGun tacz$draw = new LivingEntityDrawGun(this.tacz$shooter, this.tacz$data);
    @Unique
    private final LivingEntityAim tacz$aim = new LivingEntityAim(this.tacz$shooter, this.tacz$data);
    @Unique
    private final LivingEntityCrawl tacz$crawl = new LivingEntityCrawl(this.tacz$shooter, this.tacz$data);
    @Unique
    private final LivingEntityAmmoCheck tacz$ammoCheck = new LivingEntityAmmoCheck(this.tacz$shooter);
    @Unique
    private final LivingEntityFireSelect tacz$fireSelect = new LivingEntityFireSelect(this.tacz$shooter, this.tacz$data);
    @Unique
    private final LivingEntityMelee tacz$melee = new LivingEntityMelee(this.tacz$shooter, this.tacz$data, this.tacz$draw);
    @Unique
    private final LivingEntityShoot tacz$shoot = new LivingEntityShoot(this.tacz$shooter, this.tacz$data, this.tacz$draw);
    @Unique
    private final LivingEntityBolt tacz$bolt = new LivingEntityBolt(this.tacz$data, this.tacz$shooter, this.tacz$draw, this.tacz$shoot);
    @Unique
    private final LivingEntityReload tacz$reload = new LivingEntityReload(this.tacz$shooter, this.tacz$data, this.tacz$draw, this.tacz$shoot);
    @Unique
    private final LivingEntitySpeedModifier tacz$speed = new LivingEntitySpeedModifier(this.tacz$shooter, this.tacz$data);
    @Unique
    private final LivingEntitySprint tacz$sprint = new LivingEntitySprint(this.tacz$shooter, this.tacz$data);
    @Unique
    private final LivingEntityHeat tacz$heat = new LivingEntityHeat(this.tacz$shooter, this.tacz$data);

    public TaczLivingEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    public long getSynShootCoolDown() {
        return ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.getValue(this.tacz$shooter);
    }

    public long getSynMeleeCoolDown() {
        return ModSyncedEntityData.MELEE_COOL_DOWN_KEY.getValue(this.tacz$shooter);
    }

    @Unique
    public long getSynDrawCoolDown() {
        return ModSyncedEntityData.DRAW_COOL_DOWN_KEY.getValue(this.tacz$shooter);
    }

    @Unique
    public boolean getSynIsBolting() {
        return ModSyncedEntityData.IS_BOLTING_KEY.getValue(this.tacz$shooter);
    }

    @Unique
    public ReloadState getSynReloadState() {
        return ModSyncedEntityData.RELOAD_STATE_KEY.getValue(this.tacz$shooter);
    }

    @Unique
    public float getSynAimingProgress() {
        return ModSyncedEntityData.AIMING_PROGRESS_KEY.getValue(this.tacz$shooter);
    }

    @Unique
    public float getSynSprintTime() {
        return ModSyncedEntityData.SPRINT_TIME_KEY.getValue(this.tacz$shooter);
    }

    @Unique
    public boolean getSynIsAiming() {
        return ModSyncedEntityData.IS_AIMING_KEY.getValue(this.tacz$shooter);
    }

    @Unique
    public void initialData() {
        this.tacz$data.initialData();
        this.tacz$data.currentGunItem = () -> this.tacz$shooter.m_21205_();
        AttachmentPropertyManager.postChangeEvent(this.tacz$shooter, this.tacz$shooter.m_21205_());
    }

    @Unique
    public void draw(Supplier<ItemStack> gunItemSupplier) {
        this.tacz$draw.draw(gunItemSupplier);
    }

    @Unique
    public void bolt() {
        this.tacz$bolt.bolt();
    }

    @Unique
    public void reload() {
        this.tacz$reload.reload();
    }

    @Unique
    public void cancelReload() {
        this.tacz$reload.cancelReload();
    }

    public void melee() {
        this.tacz$melee.melee();
    }

    @Unique
    public ShootResult shoot(Supplier<Float> pitch, Supplier<Float> yaw) {
        return this.shoot(pitch, yaw, System.currentTimeMillis() - this.tacz$data.baseTimestamp);
    }

    @Unique
    public ShootResult shoot(Supplier<Float> pitch, Supplier<Float> yaw, long timestamp) {
        return this.tacz$shoot.shoot(pitch, yaw, timestamp);
    }

    @Unique
    public boolean needCheckAmmo() {
        return this.tacz$ammoCheck.needCheckAmmo();
    }

    @Unique
    public boolean consumesAmmoOrNot() {
        return this.tacz$ammoCheck.consumesAmmoOrNot();
    }

    @Unique
    public boolean getProcessedSprintStatus(boolean sprint) {
        return this.tacz$sprint.getProcessedSprintStatus(sprint);
    }

    @Unique
    public void aim(boolean isAim) {
        this.tacz$aim.aim(isAim);
    }

    public void crawl(boolean isCrawl) {
        this.tacz$crawl.crawl(isCrawl);
    }

    public void updateCacheProperty(AttachmentCacheProperty cacheProperty) {
        this.tacz$data.cacheProperty = cacheProperty;
    }

    public AttachmentCacheProperty getCacheProperty() {
        return this.tacz$data.cacheProperty;
    }

    public ShooterDataHolder getDataHolder() {
        return this.tacz$data;
    }

    public boolean nextBulletIsTracer(int tracerCountInterval) {
        this.tacz$data.shootCount++;
        if (tracerCountInterval == -1) {
            return false;
        }
        return this.tacz$data.shootCount % (tracerCountInterval + 1) == 0;
    }

    @Unique
    public void fireSelect() {
        this.tacz$fireSelect.fireSelect();
    }

    @Unique
    public void zoom() {
        this.tacz$aim.zoom();
    }

    /** TaCZ's server-side tick; the only change is the TaCZScope around the eight synced-data writes. */
    @Inject(method = "m_8119_", at = @At("RETURN"))
    private void onTickServerSide(CallbackInfo ci) {
        if (!this.m_9236_().m_5776_()) {
            ReloadState reloadState = this.tacz$reload.tickReloadState();
            this.tacz$aim.tickAimingProgress();
            this.tacz$aim.tickSprint();
            this.tacz$crawl.tickCrawling();
            this.tacz$bolt.tickBolt();
            this.tacz$melee.scheduleTickMelee();
            this.tacz$speed.updateSpeedModifier();
            this.tacz$heat.tickHeat();
            this.tacz$shooter.m_6858_(this.getProcessedSprintStatus(this.tacz$shooter.m_20142_()));
            TaCZScope.begin(this.tacz$shooter);
            try {
                ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.setValue(this.tacz$shooter, this.tacz$shoot.getShootCoolDown());
                ModSyncedEntityData.MELEE_COOL_DOWN_KEY.setValue(this.tacz$shooter, this.tacz$melee.getMeleeCoolDown());
                ModSyncedEntityData.DRAW_COOL_DOWN_KEY.setValue(this.tacz$shooter, this.tacz$draw.getDrawCoolDown());
                ModSyncedEntityData.IS_BOLTING_KEY.setValue(this.tacz$shooter, this.tacz$data.isBolting);
                ModSyncedEntityData.RELOAD_STATE_KEY.setValue(this.tacz$shooter, reloadState);
                ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(this.tacz$shooter, this.tacz$data.aimingProgress);
                ModSyncedEntityData.IS_AIMING_KEY.setValue(this.tacz$shooter, this.tacz$data.isAiming);
                ModSyncedEntityData.SPRINT_TIME_KEY.setValue(this.tacz$shooter, this.tacz$data.sprintTimeS);
            } finally {
                TaCZScope.end();
            }
        }
    }

    @Unique
    public void resetKnockBackStrength() {
        this.tacz$data.knockbackStrength = -1.0;
    }

    @Unique
    public double getKnockBackStrength() {
        return this.tacz$data.knockbackStrength;
    }

    @Unique
    public void setKnockBackStrength(double strength) {
        this.tacz$data.knockbackStrength = strength;
    }
}
