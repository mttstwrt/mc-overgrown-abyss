package dev.syrval.overgrownabyss.basilisk.entity;

import dev.syrval.overgrownabyss.OvergrownAbyss;
import dev.syrval.overgrownabyss.basilisk.BasiliskEntities;
import dev.syrval.overgrownabyss.basilisk.entity.ai.BasiliskMoveControl;
import dev.syrval.overgrownabyss.basilisk.entity.ai.BasiliskNavigation;
import dev.syrval.overgrownabyss.basilisk.entity.ai.BasiliskStalkGoal;
import dev.syrval.overgrownabyss.basilisk.entity.ai.BasiliskAttackGoal;
import dev.syrval.overgrownabyss.basilisk.entity.ai.TerrainProbe;
import dev.syrval.overgrownabyss.compat.RavineFootprintHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
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
import net.minecraft.world.level.ServerLevelAccessor;
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
    /** Upper bound of the body length (per-individual count: {@link BasiliskSize#segmentCount}). */
    public static final int SEGMENT_COUNT = BasiliskSize.MAX_SEGMENTS;
    /** Depth-share margin used when rolling the size at a natural spawn (same idea as BasiliskSpawning). */
    private static final double SIZE_DEPTH_MARGIN = 4.0D;
    /** How high the head is carried when fully reared, in blocks. Shared by renderer and head hitbox. */
    public static final float HEAD_RAISE = 1.55F;
    /** How high the resting head sits at the middle of the sleeping coil, in blocks. */
    public static final float COIL_HEAD_RAISE = 0.4F;

    /** Patrol/idle movement_speed is 0.30 (see createAttributes). While it has a target this much is added: 0.45 total. */
    /** Poison I duration applied by a landed bite or lunge (5 s). */
    private static final int VENOM_TICKS = 100;
    private static final double ENGAGED_SPEED_BONUS = 0.15D;
    private static final ResourceLocation ENGAGED_SPEED_ID =
        ResourceLocation.fromNamespaceAndPath(OvergrownAbyss.MOD_ID, "basilisk_engaged_speed");

    // --- sleep / patrol (first-guess numbers)
    private static final int WAKE_CHECK_TICKS = 5;
    private static final double WAKE_RADIUS = 10.0D;
    private static final double WAKE_RADIUS_SNEAK = 4.0D;
    private static final double WAKE_RADIUS_SPRINT = 16.0D;
    private static final int WAKE_GROGGY_TICKS = 15;
    private static final int IDLE_SLEEP_MIN = 600;
    private static final int IDLE_SLEEP_MAX = 1200;
    private static final double PATROL_SPEED = 0.6D;
    private static final double SPAWN_SLEEP_CHANCE = 0.6D;
    /** Sleeping or engaged basilisks only despawn beyond this distance (blocks). */
    private static final double KEEP_LOADED_DISTANCE = 128.0D;
    // --- climbing
    private static final double CLIMB_WANTED_Y = 0.3D;   // wanted position this far above us
    private static final double CLIMB_TARGET_Y = 0.5D;   // chased target this far above us
    private static final int CLIMB_REQUEST_TICKS = 40;

    private static final EntityDataAccessor<Integer> DATA_PHASE =
        SynchedEntityData.defineId(Basilisk.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SLEEPING =
        SynchedEntityData.defineId(Basilisk.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SCALE =
        SynchedEntityData.defineId(Basilisk.class, EntityDataSerializers.FLOAT);

    private final BasiliskSegment[] hitParts = new BasiliskSegment[SEGMENT_COUNT + 1]; // slot 0 = raised-head hitbox
    private final BasiliskSegmentChain chain = new BasiliskSegmentChain(SEGMENT_COUNT);
    private boolean chainReady;

    private float rearAnim, rearAnimO;
    private float jawAnim, jawAnimO;

    private int climbUntil;   // not saved; tick until which a stuck-triggered climb stays on
    private int groggyTicks;  // not saved
    private int idleTicks = -1; // not saved; counts down while awake without a target, -1 = roll a new duration

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    public Basilisk(EntityType<? extends Basilisk> type, Level level) {
        super(type, level);
        this.xpReward = BasiliskSize.MIN_XP;
        this.noCulling = true; // the body extends well beyond the head's own bounding box
        this.moveControl = new BasiliskMoveControl(this);

        // Pathing costs. -1 = never path through; larger = avoid.
        this.setPathfindingMalus(PathType.LAVA, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 24.0F); // vanilla default 8: keep well away from lava edges
        this.refreshLeafMalus();
        applyScale(BasiliskSize.roll(this.random.nextDouble(), 0.0D), true); // old saves and summons keep this roll
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, BasiliskSize.MIN_HEALTH)
            .add(Attributes.MOVEMENT_SPEED, 0.30D)
            .add(Attributes.ATTACK_DAMAGE, BasiliskSize.BASE_DAMAGE)
            .add(Attributes.SCALE, 1.0D)
            .add(Attributes.FOLLOW_RANGE, 40.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
            .add(Attributes.STEP_HEIGHT, BasiliskSize.stepHeight(BasiliskSize.MIN_SCALE)) // raised per size in applyScale; walls are climbed, never broken
            .add(Attributes.SAFE_FALL_DISTANCE, 24.0D)       // a snake lands softly: big drops are routine
            .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.25D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { // VERSION-SENSITIVE (builder form is 1.21+)
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, BasiliskPhase.IDLE.ordinal());
        builder.define(DATA_SLEEPING, false);
        builder.define(DATA_SCALE, BasiliskSize.MIN_SCALE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new BasiliskAttackGoal(this));
        this.goalSelector.addGoal(3, new BasiliskStalkGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, PATROL_SPEED));
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
        if (target != null && isAsleep()) return; // a sleeper acquires nothing; wake() sets the target itself
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

    // ---------------------------------------------------------------- size

    /** This individual's size, BasiliskSize.MIN_SCALE..MAX_SCALE. */
    public float getBasiliskScale() {
        return this.entityData.get(DATA_SCALE);
    }

    /**
     * Set the size and everything derived from it: hitbox (SCALE attribute), health, damage, xp, body length.
     * Runs on both sides; the client gets the value through the synched float.
     */
    private void applyScale(float scale, boolean heal) {
        this.entityData.set(DATA_SCALE, scale);
        setBase(Attributes.SCALE, scale);
        setBase(Attributes.MAX_HEALTH, BasiliskSize.maxHealth(scale));
        setBase(Attributes.ATTACK_DAMAGE, BasiliskSize.attackDamage(scale));
        // Pathfinder (WalkNodeEvaluator) and entity movement both read maxUpStep(), i.e. this attribute.
        setBase(Attributes.STEP_HEIGHT, BasiliskSize.stepHeight(scale));
        this.xpReward = BasiliskSize.xpReward(scale);
        int count = BasiliskSize.segmentCount(scale);
        if (chain.count != count || chain.scale != scale) {
            chain.configure(scale, count);
            chainReady = false;
        }
        refreshDimensions();
        if (heal && !this.level().isClientSide) this.setHealth(this.getMaxHealth());
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != value) instance.setBaseValue(value);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_SCALE.equals(accessor) && this.level().isClientSide) applyScale(getBasiliskScale(), false);
    }

    // ---------------------------------------------------------------- sleep

    /** Named isAsleep because LivingEntity.isSleeping() is the bed pose. */
    public boolean isAsleep() {
        return this.entityData.get(DATA_SLEEPING);
    }

    public void setAsleep(boolean asleep) {
        this.entityData.set(DATA_SLEEPING, asleep);
    }

    /** True for a moment after waking: the attack goal waits. */
    public boolean isGroggy() {
        return groggyTicks > 0;
    }

    /** A sleeper skips all AI and movement input (LivingEntity.aiStep checks this before serverAiStep). */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isAsleep();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Sleeping", isAsleep());
        tag.putFloat("Scale", getBasiliskScale());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setAsleep(tag.getBoolean("Sleeping"));
        // Saved attribute bases were just restored; re-derive them from the size. A save without "Scale"
        // keeps the roll made at construction and starts at full health.
        boolean saved = tag.contains("Scale");
        applyScale(saved ? Mth.clamp(tag.getFloat("Scale"), BasiliskSize.MIN_SCALE, BasiliskSize.MAX_SCALE) : getBasiliskScale(), !saved);
    }

    /** Natural spawns (worldgen, spawner, structures) may start asleep; commands, eggs and the like stay awake. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        boolean natural = spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION
            || spawnType == MobSpawnType.SPAWNER || spawnType == MobSpawnType.STRUCTURE;
        if (natural && this.random.nextDouble() < SPAWN_SLEEP_CHANCE) setAsleep(true);
        double depth = natural
            ? RavineFootprintHolder.of(level.getLevel()).depthShareIn(getBlockX(), getBlockY(), getBlockZ(), SIZE_DEPTH_MARGIN).orElse(0.0D)
            : 0.0D;
        applyScale(BasiliskSize.roll(this.random.nextDouble(), depth), true);
        return data;
    }

    /** Patrolling (awake, no target) basilisks despawn normally; sleeping or engaged ones are kept until far away. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        if (!isAsleep() && getTarget() == null) return true;
        return distanceToClosestPlayer > KEEP_LOADED_DISTANCE * KEEP_LOADED_DISTANCE;
    }

    private void fallAsleep() {
        setAsleep(true);
        idleTicks = -1;
        this.getNavigation().stop();
        this.goalSelector.getAvailableGoals().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
        this.setZza(0.0F);
        this.setXxa(0.0F);
    }

    /** @param cause the player who triggered it (proximity) or the attacker; becomes the target */
    private void wake(@Nullable LivingEntity cause) {
        setAsleep(false);
        groggyTicks = WAKE_GROGGY_TICKS;
        idleTicks = -1;
        this.playSound(BasiliskEntities.SND_REAR.get(), 1.4F, 0.9F + this.random.nextFloat() * 0.2F);
        if (cause != null) setTarget(cause);
    }

    private void tickSleep() {
        if (groggyTicks > 0) groggyTicks--;
        if (isAsleep()) {
            Vec3 v = getDeltaMovement();
            setDeltaMovement(0.0D, v.y, 0.0D);
            if (this.tickCount % WAKE_CHECK_TICKS == 0) {
                Player p = this.level().getNearestPlayer(getX(), getY(), getZ(), WAKE_RADIUS_SPRINT, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
                if (p != null && distanceTo(p) <= wakeRadius(p)) wake(p);
            }
            return;
        }
        if (getTarget() != null || getPhase() != BasiliskPhase.IDLE) {
            idleTicks = -1;
        } else if (onGround() && !isInWaterOrBubble() && !isInLava()) {
            if (idleTicks < 0) idleTicks = IDLE_SLEEP_MIN + this.random.nextInt(IDLE_SLEEP_MAX - IDLE_SLEEP_MIN + 1);
            if (--idleTicks == 0) fallAsleep();
        }
    }

    private double wakeRadius(Player p) {
        double radius = p.isCrouching() ? WAKE_RADIUS_SNEAK : WAKE_RADIUS;
        if (p.isSprinting()) radius = WAKE_RADIUS_SPRINT;
        if (!hasLineOfSight(p)) radius *= 0.5D;
        return radius;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) { // VERSION-SENSITIVE (1.21.5+: hurtServer)
        boolean wasAsleep = isAsleep();
        boolean hurt = super.hurt(source, amount);
        if (hurt && wasAsleep && !this.level().isClientSide) {
            wake(source.getEntity() instanceof LivingEntity attacker ? attacker : null);
        }
        return hurt;
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

    /** Extra head height from the sleeping coil, interpolated for rendering. */
    public float coilRaise(float partialTick) {
        return COIL_HEAD_RAISE * ease(Mth.lerp(partialTick, chain.pcoil, chain.coil));
    }

    public BasiliskSegmentChain getChain() {
        return chain;
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) tickSleep();
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
            if (isAsleep()) chain.resetCoiled(getX(), getY(), getZ(), getYRot());
            else chain.reset(getX(), getY(), getZ(), getYRot());
            chainReady = true;
        }
        chain.update(getX(), getY(), getZ(), getYRot(), rearAnim, isAsleep(),
            (x, y, z) -> TerrainProbe.groundY(this.level(), x, y, z, (int) Math.ceil(3.0D * getScale())));
    }

    // ---------------------------------------------------------------- hitbox parts (plain entities)

    /** Called by each part every tick, on both sides, so hitboxes always match the rendered body. */
    void placePart(BasiliskSegment part) {
        int index = part.getIndex();
        if (index < 0) {
            part.setPos(getX(), getY() + (HEAD_RAISE * ease(rearAnim) + coilRaise(1.0F)) * getScale(), getZ());
        } else if (index < chain.count) {
            part.setPos(chain.x[index], chain.y[index], chain.z[index]);
        }
    }

    private void ensureHitParts() {
        if (this.tickCount % 20 != 1) return;
        for (int slot = 0; slot < hitParts.length; slot++) {
            BasiliskSegment part = hitParts[slot];
            if (slot > chain.count) { // shorter body than the slot array: no hitbox here
                if (part != null && !part.isRemoved()) part.discard();
                hitParts[slot] = null;
                continue;
            }
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
        // Wall-climb like a spider (any wall, including the chasm's tilted ones) while pressed against it and
        // wanting to go up: the move target is above, the chased target is above, or the stuck fallback asked.
        // IDLE also covers stalking; attack telegraphs and the lunge never climb.
        if (this.level().isClientSide || isAsleep() || !this.horizontalCollision) return false;
        BasiliskPhase phase = getPhase();
        if (phase != BasiliskPhase.IDLE && phase != BasiliskPhase.RECOVER) return false;
        LivingEntity target = getTarget();
        return this.tickCount < climbUntil
            || this.getMoveControl().getWantedY() > getY() + CLIMB_WANTED_Y
            || (target != null && target.getY() > getY() + CLIMB_TARGET_Y);
    }

    /** Called by the stuck fallback: climb the wall in front for a moment even if the target is not above. */
    public void requestClimb() {
        climbUntil = this.tickCount + CLIMB_REQUEST_TICKS;
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
        return position().add(look.scale(0.7D * getScale())).add(0.0D, (0.55D + HEAD_RAISE * ease(rearAnim)) * getScale(), 0.0D);
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
        if (isAsleep()) return null;
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
