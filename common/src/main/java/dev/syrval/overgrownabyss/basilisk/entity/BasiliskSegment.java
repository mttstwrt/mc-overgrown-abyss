package dev.syrval.overgrownabyss.basilisk.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Invisible hitbox for one body segment (or, at index -1, for the raised head). It draws nothing: the
 * parent's renderer draws the whole body from the chain. It only exists so players and arrows can hit
 * the body, and it forwards that damage to the parent.
 *
 * Naga difference: TF uses NeoForge's PartEntity plus a custom sync packet. This is an ordinary entity,
 * positioned each tick from the parent's chain on BOTH sides (the chain is deterministic), so there is no
 * loader API and no packet. It is never saved; the parent re-creates missing parts.
 */
public class BasiliskSegment extends Entity {
    private static final EntityDataAccessor<Integer> DATA_PARENT =
        SynchedEntityData.defineId(BasiliskSegment.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_INDEX =
        SynchedEntityData.defineId(BasiliskSegment.class, EntityDataSerializers.INT);

    /** Body segments take 2/3 of the damage, like the naga's; the raised head takes full damage. */
    private static final float BODY_DAMAGE_SHARE = 2.0F / 3.0F;

    private Basilisk parent;

    public BasiliskSegment(EntityType<? extends BasiliskSegment> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public void bind(Basilisk parent, int index) {
        this.parent = parent;
        this.entityData.set(DATA_PARENT, parent.getId());
        this.entityData.set(DATA_INDEX, index);
    }

    public int getIndex() {
        return this.entityData.get(DATA_INDEX);
    }

    private Basilisk getParent() {
        if (parent != null && !parent.isRemoved()) return parent;
        int id = this.entityData.get(DATA_PARENT);
        if (id < 0) return null;
        Entity found = this.level().getEntity(id); // client: resolved lazily, the parent may arrive after us
        parent = found instanceof Basilisk b ? b : null;
        return parent;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { // VERSION-SENSITIVE
        builder.define(DATA_PARENT, -1);
        builder.define(DATA_INDEX, 0);
    }

    @Override
    public void tick() {
        super.tick();
        Basilisk p = getParent();
        if (p == null) {
            if (!this.level().isClientSide && this.tickCount > 20) this.discard();
            return;
        }
        p.placePart(this);
    }

    /** The raised-head box is only hittable while the head is actually up there. */
    @Override
    public boolean isPickable() {
        Basilisk p = getParent();
        if (p == null || isRemoved()) return false;
        return getIndex() >= 0 || p.getRearAnim(1.0F) > 0.3F;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) { // VERSION-SENSITIVE (1.21.5+: hurtServer)
        Basilisk p = getParent();
        if (p == null || this.level().isClientSide) return false;
        return p.hurt(source, getIndex() < 0 ? amount : amount * BODY_DAMAGE_SHARE);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        Basilisk p = getParent();
        return p != null ? p.interact(player, hand) : InteractionResult.PASS;
    }

    @Override
    public boolean is(Entity other) {
        return other == this || other == getParent();
    }

    /** VERSION-SENSITIVE (1.21.2+ replaced lerpTo with an interpolation handler). Ignore server position packets:
     *  both sides compute the same chain, and applying packets on top would make the hitboxes jitter. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // intentionally empty
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean force) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
