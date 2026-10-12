package dhxaerobridge.mc;

import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import dhxaerobridge.dh.DhAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import xaero.map.MapProcessor;
import xaero.map.file.RegionDetection;
import xaero.map.region.MapRegion;
import xaero.map.world.MapDimension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Tells Xaero's World Map which regions exist only as Distant Horizons LODs.
 * Runs in a background thread; re-checks once a minute so LODs that Distant Horizons
 * generates while you play show up too.
 */
public final class RegionScanner {
    private static volatile int generation = 0;
    private static Path placeholder;

    private RegionScanner() {}

    private static synchronized Path placeholder() {
        if (placeholder == null) {
            try {
                Path dir = FabricLoader.getInstance().getConfigDir().resolve("dh-xaero-bridge");
                Files.createDirectories(dir);
                Path p = dir.resolve("placeholder.bin");
                if (!Files.exists(p) || Files.size(p) < 16384) {
                    Files.write(p, new byte[16384]);
                }
                placeholder = p;
            } catch (IOException e) {
                Bridge.LOG.error("Could not create placeholder file", e);
            }
        }
        return placeholder;
    }

    /** Called (on Xaero's thread) right after Xaero has finished looking for regions. */
    public static void start(MapProcessor proc) {
        try {
            MapDimension dim = proc.getMapWorld().getCurrentDimension();
            if (dim == null || !dim.isUsingWorldSave()) return;
            Path worldDir = proc.getWorldDataHandler().getWorldDir();
            if (worldDir == null) return;
            final String worldId = proc.getCurrentWorldId();
            final String dimId = proc.getCurrentDimId();
            final String mwId = proc.getCurrentMWId();
            if (worldId == null || dimId == null) return;
            final Path regionDir = worldDir.resolve("region");
            final Path stub = placeholder();
            if (stub == null) return;
            final int myGeneration = ++generation;
            final RegistryKey<World> dimKey = proc.getMapWorld().getCurrentDimensionId();

            Thread t = new Thread(() -> run(proc, dim, worldId, dimId, mwId, regionDir, stub, dimKey, myGeneration),
                    "dh-xaero-bridge-scanner");
            t.setDaemon(true);
            t.start();
        } catch (Throwable t) {
            Bridge.LOG.error("Could not start the region scanner", t);
        }
    }

    /**
     * Marks an already-drawn region as outdated; Xaero then redraws it by itself the next time
     * it is on screen in the map (the same thing Xaero does after its own updates).
     */
    private static boolean refreshRegion(MapProcessor proc, int rx, int rz) {
        try {
            MapRegion region = proc.getMapWorld().getCurrentDimension().getLayeredMapRegions()
                    .getLeaf(proc.getCurrentCaveLayer(), rx, rz);
            if (region == null) return false;
            synchronized (region) {
                if (region.getLoadState() != 2) return false; // not drawn yet: it will load fresh anyway
                region.setVersion(region.getVersion() - 1);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void run(MapProcessor proc, MapDimension dim, String worldId, String dimId, String mwId,
                            Path regionDir, Path stub, RegistryKey<World> dimKey, int myGeneration) {
        Map<Long, Integer> known = new HashMap<>(); // region -> number of DH pieces last time we looked
        int withRealFile = 0;
        int withEmptyFile = 0;
        int withoutFile = 0;
        boolean announced = false;
        try {
            while (myGeneration == generation) {
                MinecraftServer server = MinecraftClient.getInstance().getServer();
                if (server == null) return; // left the world
                ServerWorld world = server.getWorld(dimKey);
                IDhApiLevelWrapper level = world == null ? null : Bridge.levelFor(world);
                if (level == null) {
                    Thread.sleep(3000);
                    continue;
                }
                int[] regions = DhAccess.listRegions(level);
                if (regions == null) {
                    Bridge.LOG.warn("Could not list Distant Horizons regions (DH internals differ from what this mod expects). "
                            + "Regions without any Minecraft region file will not be shown.");
                    return;
                }
                int added = 0;
                int refreshed = 0;
                for (int i = 0; i < regions.length; i += 3) {
                    int rx = regions[i];
                    int rz = regions[i + 1];
                    int pieces = regions[i + 2];
                    long key = ((long) rx << 32) | (rz & 0xFFFFFFFFL);
                    Integer before = known.put(key, pieces);
                    if (before != null) {
                        // Seen before. If Distant Horizons has generated more of it since, redraw it.
                        if (pieces > before && refreshRegion(proc, rx, rz)) refreshed++;
                        continue;
                    }
                    File real = regionDir.resolve("r." + rx + "." + rz + ".mca").toFile();
                    long size = real.exists() ? real.length() : -1L;
                    // A region file of 8192 bytes or less holds no chunks at all (just the empty header).
                    // Xaero ignores those, so we treat them like "no file".
                    if (size > 8192L) {
                        withRealFile++;
                        continue; // Xaero already knows this one; chunks are filled in separately
                    }
                    if (size >= 0) withEmptyFile++; else withoutFile++;
                    File fake = new FakeRegionFile(real, stub);
                    dim.addWorldSaveRegionDetection(
                            new RegionDetection(worldId, dimId, mwId, rx, rz, fake, proc.getGlobalVersion(), true));
                    added++;
                }
                if (added > 0 || !announced) {
                    Bridge.LOG.info("Distant Horizons has data in {} regions. Real region files: {}, empty region files: {}, no file: {}. "
                                    + "{} regions added to the Xaero map as LOD-only regions this round.",
                            regions.length / 3, withRealFile, withEmptyFile, withoutFile, added);
                    announced = true;
                }
                if (refreshed > 0) {
                    Bridge.LOG.info("{} regions got more Distant Horizons data and were queued for a redraw.", refreshed);
                }
                // wait a minute (in small steps so we stop quickly when the world changes)
                for (int s = 0; s < 60 && myGeneration == generation; s++) Thread.sleep(1000);
            }
        } catch (InterruptedException ignored) {
        } catch (Throwable t) {
            Bridge.LOG.error("Region scanner stopped because of an error", t);
        }
    }
}
