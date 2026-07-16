package com.eyeofthestorm.storm;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.List;

/** Overworld stronghold ring positions for path-preview coverage. */
public final class StormStrongholds {
    private StormStrongholds() {}

    public record Pos(double x, double z) {}

    /**
     * All stronghold locate positions for this overworld (typically 128 on Java).
     * Uses concentric-ring placement math — does not generate structures.
     */
    public static List<Pos> locateAll(ServerLevel level) {
        List<Pos> out = new ArrayList<>(128);
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        state.ensureStructuresGenerated();

        Holder.Reference<Structure> stronghold = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getHolderOrThrow(BuiltinStructures.STRONGHOLD);

        for (StructurePlacement placement : state.getPlacementsForStructure(stronghold)) {
            if (!(placement instanceof ConcentricRingsStructurePlacement rings)) {
                continue;
            }
            List<ChunkPos> chunks = state.getRingPositionsFor(rings);
            if (chunks == null) {
                continue;
            }
            for (ChunkPos chunk : chunks) {
                var block = placement.getLocatePos(chunk);
                out.add(new Pos(block.getX() + 0.5, block.getZ() + 0.5));
            }
        }
        return out;
    }

    /** Pack XZ into parallel arrays for network send. */
    public static double[][] toArrays(List<Pos> positions) {
        double[] xs = new double[positions.size()];
        double[] zs = new double[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            xs[i] = positions.get(i).x();
            zs[i] = positions.get(i).z();
        }
        return new double[][] { xs, zs };
    }
}
