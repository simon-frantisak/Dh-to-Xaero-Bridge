package dhxaerobridge.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataRepo;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import dhxaerobridge.core.ColumnPoint;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything that talks to Distant Horizons lives in this one file.
 * It only READS from Distant Horizons. It never writes anything.
 */
public final class DhAccess {
    private static final Object LOCK = new Object();
    private static final long CACHE_MAX_AGE_MS = 5000;
    private static final int CACHE_MAX_CALLS = 1024;

    private static final Map<IDhApiLevelWrapper, IDhApiTerrainDataCache> CACHES = new IdentityHashMap<>();
    private static final Map<IDhApiLevelWrapper, long[]> CACHE_STATS = new IdentityHashMap<>(); // [calls, lastClearMs]

    private DhAccess() {}

    public static boolean isReady() {
        try {
            IDhApiWorldProxy proxy = DhApi.Delayed.worldProxy;
            return proxy != null && DhApi.Delayed.terrainRepo != null && proxy.worldLoaded();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Finds Distant Horizons' wrapper for a given Minecraft world.
     *
     * @param mcLevel  the ServerWorld object
     * @param fullId   e.g. "minecraft:overworld"
     * @param path     e.g. "overworld"
     */
    public static IDhApiLevelWrapper findLevel(Object mcLevel, String fullId, String path) {
        try {
            if (!isReady()) return null;
            Iterable<IDhApiLevelWrapper> all = DhApi.Delayed.worldProxy.getAllLoadedLevelWrappers();
            IDhApiLevelWrapper byName = null;
            for (IDhApiLevelWrapper w : all) {
                if (w.getWrappedMcObject() == mcLevel) return w;
                String n = w.getDimensionName();
                if (byName == null && n != null && (n.equals(fullId) || n.equals(path) || n.endsWith(":" + path))) {
                    byName = w;
                }
            }
            return byName;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Reads one chunk's worth of columns. Returns [x][z][points], or null when
     * Distant Horizons has nothing at all for that chunk.
     */
    public static ColumnPoint[][][] fetchChunk(IDhApiLevelWrapper level, int chunkX, int chunkZ) {
        synchronized (LOCK) {
            IDhApiTerrainDataRepo repo = DhApi.Delayed.terrainRepo;
            if (repo == null) return null;

            long now = System.currentTimeMillis();
            IDhApiTerrainDataCache cache = CACHES.get(level);
            long[] stats = CACHE_STATS.get(level);
            if (cache == null) {
                cache = repo.createSoftCache();
                stats = new long[]{0, now};
                CACHES.put(level, cache);
                CACHE_STATS.put(level, stats);
            } else if (++stats[0] >= CACHE_MAX_CALLS || now - stats[1] > CACHE_MAX_AGE_MS) {
                cache.clear();
                stats[0] = 0;
                stats[1] = now;
            }

            DhApiResult<DhApiTerrainDataPoint[][][]> result =
                    repo.getAllTerrainDataAtChunkPos(level, chunkX, chunkZ, cache);
            if (result == null || !result.success || result.payload == null) return null;

            DhApiTerrainDataPoint[][][] raw = result.payload;
            ColumnPoint[][][] out = new ColumnPoint[16][16][];
            boolean any = false;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    DhApiTerrainDataPoint[] pts = (x < raw.length && raw[x] != null && z < raw[x].length) ? raw[x][z] : null;
                    if (pts == null || pts.length == 0) {
                        out[x][z] = new ColumnPoint[0];
                        continue;
                    }
                    ColumnPoint[] conv = new ColumnPoint[pts.length];
                    for (int i = 0; i < pts.length; i++) {
                        DhApiTerrainDataPoint p = pts[i];
                        if (p == null) continue;
                        Object state = null;
                        IDhApiBlockStateWrapper bs = p.blockStateWrapper;
                        if (bs != null && !bs.isAir()) {
                            state = bs.getWrappedMcObject();
                        }
                        String biome = p.biomeWrapper != null ? normalizeBiome(p.biomeWrapper.getName()) : null;
                        conv[i] = new ColumnPoint(p.bottomYBlockPos, p.topYBlockPos, state, biome);
                        any = true;
                    }
                    out[x][z] = conv;
                }
            }
            return any ? out : null;
        }
    }

    private static String normalizeBiome(String name) {
        if (name == null || name.isEmpty()) return null;
        return name.indexOf(':') >= 0 ? name : "minecraft:" + name;
    }

    /**
     * Lists the 512x512-block regions (same size as Minecraft's region files) that
     * Distant Horizons has stored data for. Returns packed triples: {x0, z0, pieces0, x1, z1, pieces1, ...}.
     * Uses reflection into DH internals because the public API has no "list" call.
     * Returns null if that is not possible with this DH version.
     */
    public static int[] listRegions(IDhApiLevelWrapper level) {
        try {
            Class<?> shared = Class.forName("com.seibel.distanthorizons.core.api.internal.SharedApi");
            Object world = shared.getMethod("getAbstractDhWorld").invoke(null);
            if (world == null) return null;
            Object dhLevel = null;
            for (Method m : world.getClass().getMethods()) {
                if (m.getName().equals("getLevel") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(level)) {
                    m.setAccessible(true);
                    dhLevel = m.invoke(world, level);
                    break;
                }
            }
            if (dhLevel == null) return null;
            Method getProvider = dhLevel.getClass().getMethod("getFullDataProvider");
            getProvider.setAccessible(true);
            Object provider = getProvider.invoke(dhLevel);
            Object repo = provider.getClass().getField("repo").get(provider);
            Method getAll = repo.getClass().getMethod("getAllPositions");
            getAll.setAccessible(true);
            List<?> positions = (List<?>) getAll.invoke(repo);

            java.util.LinkedHashMap<Long, Integer> seen = new java.util.LinkedHashMap<>();
            for (Object o : positions) {
                long pos = (Long) o;
                int detail = (int) (pos & 0x7FL);
                if (detail != 6) continue; // 6 = the finest data DH stores (64x64 blocks per piece)
                int x = (int) ((pos >> 8) & 0x0FFFFFFFL);
                x = x << 4 >> 4;
                int z = (int) ((pos >> 36) & 0x0FFFFFFFL);
                z = z << 4 >> 4;
                int rx = Math.floorDiv(x, 8);
                int rz = Math.floorDiv(z, 8);
                seen.merge(((long) rx << 32) | (rz & 0xFFFFFFFFL), 1, Integer::sum);
            }
            int[] out = new int[seen.size() * 3];
            int i = 0;
            for (java.util.Map.Entry<Long, Integer> e : seen.entrySet()) {
                long k = e.getKey();
                out[i++] = (int) (k >> 32);
                out[i++] = (int) k;
                out[i++] = e.getValue(); // how many 64x64 pieces DH has in this region (grows as DH generates)
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }
}
