package dev.syrval.overgrownabyss.compat;

import com.mojang.brigadier.CommandDispatcher;
import dev.syrval.overgrownabyss.ravine.ChasmStats;
import dev.syrval.overgrownabyss.ravine.RuinSite;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * {@code /abyss stats}: what the chasm the player is in holds (see {@link ChasmStats}). Runs on the server thread, reads the discs
 * the carve has already built for the cell, and reads each kind of piece's template once per command.
 */
public final class AbyssStatsCommand {
    private AbyssStatsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Operators only: it is a debugging aid and may build a cell's discs.
        dispatcher.register(Commands.literal("abyss").requires(source -> source.hasPermission(2))
                .then(Commands.literal("stats").executes(context -> run(context.getSource()))));
    }

    private static int run(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        Optional<ChasmStats> stats = RavineFootprintHolder.of(level).statsAt(at.getX(), at.getZ(), containersOf(level));
        if (stats.isEmpty()) {
            source.sendFailure(Component.literal("Not in a chasm"));
            return 0;
        }
        List<String> lines = stats.get().format();
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    // The template of a site is the element of its pool at the site's index, in the order the pieces were measured in.
    private static Function<RuinSite, ChasmStats.Containers> containersOf(ServerLevel level) {
        DiscRuinPieces pieces = new DiscRuinPieces(level.registryAccess(), id -> level.getServer().getStructureManager().get(id).map(t -> t.save(new CompoundTag())));
        Map<String, ChasmStats.Containers> byElement = new HashMap<>();
        return site -> byElement.computeIfAbsent(site.pool().location() + "#" + site.element(), key -> {
            Optional<StructureTemplatePool> pool = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).getOptional(site.pool());
            if (pool.isEmpty()) {
                return ChasmStats.Containers.NONE;
            }
            List<DiscRuinPieces.Weighted> elements = DiscRuinPieces.elementsOf(pool.get());
            if (site.element() >= elements.size()) {
                return ChasmStats.Containers.NONE;
            }
            StructurePoolElement element = elements.get(site.element()).element();
            return pieces.templateOf(element)
                    .flatMap(id -> level.getServer().getStructureManager().get(id))
                    .map(template -> ChasmStats.Containers.ofTemplate(template.save(new CompoundTag())))
                    .orElse(ChasmStats.Containers.NONE);
        });
    }
}
