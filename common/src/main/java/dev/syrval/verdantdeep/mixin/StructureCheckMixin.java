package dev.syrval.verdantdeep.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.syrval.verdantdeep.compat.RavineCityStructure;
import dev.syrval.verdantdeep.compat.RavineFootprintHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Looking for the city. {@code /locate} asks this class about one chunk of every cell out to a hundred cells from the player,
 * and for each it asks the chunk storage before it asks the structure anything. A level that has no hole in any of them (the
 * {@code holes} dial at 0, or one whose noise settings are not carved) kept the server's thread on that for over a minute,
 * which a dedicated server's watchdog takes for a hang and ends the server. The level knows which cells hold a hole, so for
 * a cell that holds none the answer is given here and the storage is not asked.
 */
@Mixin(StructureCheck.class)
abstract class StructureCheckMixin {
    @Shadow
    @Final
    private RandomState randomState;

    // The chunk asked about is the one a cell's centre is in, so its middle is in the hole if the cell has one.
    @WrapOperation(
            method = "checkStart",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/StructureCheck;tryLoadFromStorage(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/levelgen/structure/Structure;ZJ)Lnet/minecraft/world/level/levelgen/structure/StructureCheckResult;"))
    private StructureCheckResult verdantDeep$noCityWhereNoHole(
            StructureCheck check, ChunkPos chunk, Structure structure, boolean skipKnown, long packedChunk, Operation<StructureCheckResult> original) {
        if (structure instanceof RavineCityStructure
                && !((RavineFootprintHolder) (Object) randomState).verdantDeep$footprint().contains(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ())) {
            return StructureCheckResult.START_NOT_PRESENT;
        }
        return original.call(check, chunk, structure, skipKnown, packedChunk);
    }
}
