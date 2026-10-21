package dev.syrval.overgrownabyss.basilisk.entity;

import dev.syrval.overgrownabyss.OvergrownAbyss;
import dev.syrval.overgrownabyss.basilisk.BasiliskEntities;
import dev.syrval.overgrownabyss.basilisk.entity.ai.BasiliskMoveControl;
import dev.syrval.overgrownabyss.basilisk.entity.ai.BasiliskNavigation;
import dev.syrval.overgrownabyss.basilisk.entity.ai.BasiliskStalkGoal;
import dev.syrval.overgrownabyss.basilisk.entity.ai.BasiliskAttackGoal;
import dev.syrval.overgrownabyss.basilisk.entity.ai.TerrainProbe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Jungle ambush predator: chain-of-blocks body like the Twilight Forest naga, but with its own pathing
 * (leaf-chewing, lava-averse, drops and climbs) and a fast lunge-and-bite attack (see BasiliskAttackGoal).
 *
 * Loader-neutral: nothing here imports NeoForge or Fabric.
 * Lines tagged VERSION-SENSITIVE are the ones that changed between 1.21.1 and 1.21.5+ in the Twilight Forest
 * source (hurt -> hurtServer, doHurtTarget gaining a ServerLevel, and so on).
 */
public class Basilisk extends Monster {
    public static final int SEGMENT_COUNT = 10;
    /** How high the head is carried when fully reared, in blocks. Shared by renderer and head hitbox. */
    public static final float HEAD_RAISE = 1.55F;

    /** Patrol/idle movement_speed is 0.30 (see createAttributes). While it has a target this much is added: 0.45 total. */
    /** Poison I duration applied by a landed bite or lunge (5 s). */
    private static final int VENOM_TICKS = 100;
    private static final double ENGAGED_SPEED_BONUS = 0.15D;
    private static final ResourceLocation ENGAGED_SPEED_ID =
        ResourceLocation.fromNamespaceAndPath(OvergrownAbyss.MOD_ID, "basilisk_engaged_speed");

    private static final EntityDataAccessor<Integer> DATA_PHASE =
        SynchedEntityData.defineId(Basilisk.class, EntityDataSerializers.INT);

    private final BasiliskSegment[] hitParts = new BasiliskSegment[SEGMENT_COUNT + 1]; // slot 0 = raised-head hitbox
    private final BasiliskSegmentChain chain = new BasiliskSegmentChain(SEGMENT_COUNT);
    private boolean chainReady;

    private float rearAnim, rearAnimO;
    private float jawAnim, jawAnimO;

    public Basilisk(EntityType<? extends Basilisk> type, Level level) {
        super(type, level);
        this.xpReward = 30;
        this.noCulling = true; // the body extends well beyond the head's own bounding box
        this.moveControl = new BasiliskMoveControl(this);

        // Pathing costs. -1 = never path through; larger = avoid.
        this.setPathfindingMalus(PathType.LAVA, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 24.0F); // vanilla default 8: keep well away from lava edges
        this.refreshLeafMalus();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 80.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.30D)
            .add(Attributes.ATTACK_DAMAGE, 6.0D)
            .add(Attributes.FOLLOW_RANGE, 40.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
            .add(Attributes.STEP_HEIGHT, 2.0D)               // NAGA: same 2.0 ledge-stepping
            .add(Attributes.SAFE_FALL_DISTANCE, 24.0D)       // a snake lands softly: big drops are routine
            .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.25D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { // VERSION-SENSITIVE (builder form is 1.21+)
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, BasiliskPhase.IDLE.ordinal());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new BasiliskAttackGoal(this));
        this.goalSelector.addGoal(3, new BasiliskStalkGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new BasiliskNavigation(this, level);
    }

    /** Engaged = has a target: a transient speed bonus (never saved) makes it about 1.5x faster than on patrol. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (this.level().isClientSide) return;
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean boosted = speed.getModifier(ENGAGED_SPEED_ID) != null;
        if (target != null && !boosted) {
            speed.addTransientModifier(new AttributeModifier(ENGAGED_SPEED_ID, ENGAGED_SPEED_BONUS, AttributeModifier.Operation.ADD_VALUE));
        } else if (target == null && boosted) {
            speed.removeModifier(ENGAGED_SPEED_ID);
        }
    }

    // ---------------------------------------------------------------- phase + animation state

    public BasiliskPhase getPhase() {
        return BasiliskPhase.byId(this.entityData.get(DATA_PHASE));
    }

    public void setPhase(BasiliskPhase phase) {
        this.entityData.set(DATA_PHASE, phase.ordinal());
    }

    /** Raw 0..1 rear progress, interpolated for rendering. */
    public float getRearAnim(float partialTick) {
        return Mth.lerp(partialTick, rearAnimO, rearAnim);
    }

    public float getJawAnim(float partialTick) {
        return Mth.lerp(partialTick, jawAnimO, jawAnim);
    }

    /** The same smoothstep the chain uses, so head, neck and head-hitbox agree. */
    public static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    public BasiliskSegmentChain getChain() {
        return chain;
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        tickAnimations();
        updateChain();
        if (!this.level().isClientSide) ensureHitParts();
    }

    private void tickAnimations() {
        rearAnimO = rearAnim;
        jawAnimO = jawAnim;
        float rearDiff = getPhase().rearTarget() - rearAnim;
        rearAnim += rearDiff > 0 ? Math.min(rearDiff, 0.07F) : Math.max(rearDiff, -0.22F); // slow rise, fast slam down
        float jawDiff = (getPhase() == BasiliskPhase.STRIKE ? 1.0F : 0.0F) - jawAnim;
        jawAnim += Mth.clamp(jawDiff, -0.12F, 0.4F);
    }

    private void updateChain() {
        if (!chainReady) {
            chain.reset(getX(), getY(), getZ(), getYRot());
            chainReady = true;
        }
        chain.update(getX(), getY(), getZ(), getYRot(), rearAnim,
            (x, y, z) -> TerrainProbe.groundY(this.level(), x, y, z, 3));
    }

    // ---------------------------------------------------------------- hitbox parts (plain entities)

    /** Called by each part every tick, on both sides, so hitboxes always match the rendered body. */
    void placePart(BasiliskSegment part) {
        int index = part.getIndex();
        if (index < 0) {
            part.setPos(getX(), getY() + HEAD_RAISE * ease(rearAnim), getZ());
        } else if (index < SEGMENT_COUNT) {
            part.setPos(chain.x[index], chain.y[index], chain.z[index]);
        }
    }

    private void ensureHitParts() {
        if (this.tickCount % 20 != 1) return;
        for (int slot = 0; slot < hitParts.length; slot++) {
            BasiliskSegment part = hitParts[slot];
            if (part != null && !part.isRemoved()) continue;
            BasiliskSegment fresh = BasiliskEntities.SEGMENT.get().create(this.level());
            if (fresh == null) continue;
            fresh.bind(this, slot - 1); // slot 0 -> index -1 (head hitbox), slot n -> chain index n-1
            placePart(fresh);
            this.level().addFreshEntity(fresh);
            hitParts[slot] = fresh;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        for (BasiliskSegment part : hitParts) {
            if (part != null && !part.isRemoved()) part.discard();
        }
    }

    // ---------------------------------------------------------------- movement + terrain

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            clientEffects();
        } else {
            if (this.tickCount % 40 == 0) refreshLeafMalus();
            chewFoliage();
            if (this.tickCount % 14 == 0 && onGround() && getDeltaMovement().horizontalDistanceSqr() > 0.0009D) {
                this.playSound(BasiliskEntities.SND_SLITHER.get(), 0.5F, 0.9F + this.random.nextFloat() * 0.3F);
            }
        }
    }

    /** NAGA parity: the naga breaks leaves it touches. Here it is gated by mobGriefing and also priced into pathing. */
    private boolean canChew() {
        return this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    private void refreshLeafMalus() {
        // Chewing allowed: leaves are a small detour cost, so the path may cut through the canopy.
        // Chewing forbidden: leaves are walls, so the path routes around trunks and under the canopy instead.
        this.setPathfindingMalus(PathType.LEAVES, canChew() ? 2.0F : -1.0F);
    }

    private void chewFoliage() {
        if (this.tickCount % 3 != 0 || !canChew()) return;
        boolean lunging = getPhase() == BasiliskPhase.STRIKE;
        if (!lunging && this.getNavigation().isDone()) return;

        Vec3 ahead = Vec3.directionFromRotation(0.0F, getYRot()).scale(0.6D);
        AABB zone = getBoundingBox().inflate(0.4D, 0.0D, 0.4D).move(ahead.x, 0.1D, ahead.z);
        BlockPos min = BlockPos.containing(zone.minX, zone.minY, zone.minZ);
        BlockPos max = BlockPos.containing(zone.maxX, zone.maxY, zone.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = this.level().getBlockState(pos);
            if (state.is(BlockTags.LEAVES)) this.level().destroyBlock(pos, false);
        }
    }

    @Override
    public int getMaxFallDistance() {
        // The path planner reads this to decide how far down it may route a drop (and it rejects lava landings).
        return (int) getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
    }

    @Override
    public boolean onClimbable() {
        if (super.onClimbable()) return true; // vines, ladders
        // Wall-climb like a spider, but only when the movement target is above us and we are idle/stalking.
        return !this.level().isClientSide
            && getPhase() == BasiliskPhase.IDLE
            && this.horizontalCollision
            && this.getMoveControl().getWantedY() > getY() + 0.3D;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ---------------------------------------------------------------- combat

    /** The lunge's hit: a multiple of attack_damage plus horizontal knockback along the dash. False if blocked. */
    public boolean lungeHit(LivingEntity target, float damageMultiplier, double knockback, Vec3 direction) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * damageMultiplier;
        if (!target.hurt(this.damageSources().mobAttack(this), damage)) return false;
        target.knockback(knockback, -direction.x, -direction.z);
        applyVenom(target);
        return true;
    }

    /** Poison I for VENOM_TICKS as a secondary effect; never replaces a stronger or longer poison. */
    public void applyVenom(LivingEntity victim) {
        MobEffectInstance current = victim.getEffect(MobEffects.POISON);
        if (current != null && (current.getAmplifier() > 0 || current.getDuration() >= VENOM_TICKS)) return;
        victim.addEffect(new MobEffectInstance(MobEffects.POISON, VENOM_TICKS, 0), this);

        if (this.level() instanceof ServerLevel server) {
            Vec3 mouth = mouthPosition();
            server.sendParticles(ParticleTypes.ITEM_SLIME, mouth.x, mouth.y, mouth.z, 12, 0.25D, 0.2D, 0.25D, 0.05D);
            server.sendParticles(ParticleTypes.SNEEZE, victim.getX(), victim.getEyeY(), victim.getZ(), 6, 0.3D, 0.3D, 0.3D, 0.02D);
        }
    }

    public Vec3 mouthPosition() {
        Vec3 look = Vec3.directionFromRotation(0.0F, getYRot());
        return position().add(look.scale(0.7D)).add(0.0D, 0.55D + HEAD_RAISE * ease(rearAnim), 0.0D);
    }

    private void clientEffects() {
        BasiliskPhase phase = getPhase();
        if (phase.isTelegraph() && this.tickCount % 3 == 0) {
            Vec3 m = mouthPosition();
            this.level().addParticle(ParticleTypes.ITEM_SLIME, m.x, m.y - 0.1D, m.z, 0.0D, -0.02D, 0.0D); // venom drip
        } else if (phase == BasiliskPhase.STRIKE) {
            Vec3 m = mouthPosition();
            this.level().addParticle(ParticleTypes.SNEEZE, m.x, m.y, m.z, 0.0D, 0.02D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return BasiliskEntities.SND_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return BasiliskEntities.SND_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return BasiliskEntities.SND_DEATH.get();
    }
}
