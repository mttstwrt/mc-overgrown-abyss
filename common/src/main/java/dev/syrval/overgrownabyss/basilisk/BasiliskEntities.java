package dev.syrval.overgrownabyss.basilisk;

import java.util.function.Supplier;

import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.syrval.overgrownabyss.OvergrownAbyss;
import dev.syrval.overgrownabyss.basilisk.entity.Basilisk;
import dev.syrval.overgrownabyss.basilisk.entity.BasiliskSegment;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entity types and sounds of the basilisk. Call {@link #register()} once from {@code OvergrownAbyss.init()}. */
public final class BasiliskEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(OvergrownAbyss.MOD_ID, Registries.ENTITY_TYPE);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(OvergrownAbyss.MOD_ID, Registries.SOUND_EVENT);

    // Head width 0.9 is deliberate: the pathfinder sizes its node footprint as floor(width + 1),
    // so anything under 1.0 needs only a one-block-wide gap. That is what lets it thread jungle trunks.
    public static final RegistrySupplier<EntityType<Basilisk>> BASILISK = ENTITIES.register("basilisk",
        () -> EntityType.Builder.<Basilisk>of(Basilisk::new, MobCategory.MONSTER)
            .sized(0.9F, 0.9F)
            .clientTrackingRange(10)
            .updateInterval(1)
            .build("basilisk"));

    // Segments are plain entities (hitboxes only), not NeoForge PartEntity, so Fabric works too.
    public static final RegistrySupplier<EntityType<BasiliskSegment>> SEGMENT = ENTITIES.register("basilisk_segment",
        () -> EntityType.Builder.<BasiliskSegment>of(BasiliskSegment::new, MobCategory.MISC)
            .sized(1.0F, 1.0F)
            .clientTrackingRange(10)
            .updateInterval(1)
            .noSave()
            .noSummon()
            .fireImmune()
            .build("basilisk_segment"));

    public static final Supplier<SoundEvent> SND_AMBIENT = sound("entity.basilisk.ambient");
    public static final Supplier<SoundEvent> SND_HURT = sound("entity.basilisk.hurt");
    public static final Supplier<SoundEvent> SND_DEATH = sound("entity.basilisk.death");
    public static final Supplier<SoundEvent> SND_REAR = sound("entity.basilisk.rear");
    public static final Supplier<SoundEvent> SND_STRIKE = sound("entity.basilisk.strike");
    public static final Supplier<SoundEvent> SND_SLITHER = sound("entity.basilisk.slither");

    private BasiliskEntities() {}

    private static Supplier<SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(OvergrownAbyss.id(id)));
    }

    public static void register() {
        ENTITIES.register();
        SOUNDS.register();
        EntityAttributeRegistry.register(BASILISK, Basilisk::createAttributes);
    }
}
