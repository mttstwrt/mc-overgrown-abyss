package dev.syrval.overgrownabyss.basilisk.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Ground navigation with spider-style fallback for cliffs, plus a bigger search budget.
 *
 * NAGA: uses plain GroundPathNavigation (vanilla) and leans on its home-area leash and arena. Nothing in the
 * naga's pathing copes with jungle, lava or vertical gaps, so the terrain logic here is new work.
 *
 * What does what:
 *  - WallClimberNavigation: when no ground path exists (target up a cliff), it steers straight at the target
 *    and lets Basilisk.onClimbable() do the climbing. BasiliskMoveControl vetoes lava/abyss steps.
 *  - Double node budget: jungle canopies and trunks inflate the number of nodes the search visits, and the
 *    default budget (followRange * 16) can run out before it finds a route.
 *  - Drops: planned by the vanilla evaluator up to Basilisk.getMaxFallDistance(), and it rejects lava landings.
 *  - Leaves/lava costs: set on the mob via setPathfindingMalus.
 */
public class BasiliskNavigation extends WallClimberNavigation {
    public BasiliskNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        // Mirrors GroundPathNavigation.createPathFinder, with a larger budget.
        this.nodeEvaluator = new WalkNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes * 2);
    }
}
