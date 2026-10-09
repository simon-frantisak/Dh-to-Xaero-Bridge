package dhxaerobridge.mixin;

import dhxaerobridge.mc.Bridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.MapProcessor;
import xaero.map.file.worldsave.WorldDataReader;
import xaero.map.region.MapTileChunk;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Hooks into the place where Xaero asks Minecraft for the saved data of each chunk.
 * If Minecraft says "this chunk was never saved" (the answer is empty), we ask Distant Horizons.
 * If Minecraft DOES have the chunk, we change nothing at all.
 */
@Mixin(value = WorldDataReader.class, remap = false)
public abstract class WorldDataReaderMixin {
    @Shadow(remap = false)
    private MapProcessor mapProcessor;

    @Shadow(remap = false)
    private CompletableFuture<Optional<NbtCompound>>[] chunkNBTCompounds;

    @Inject(method = "readChunkNBTCompounds", at = @At("RETURN"), remap = false)
    private void dhxb$fillMissingChunks(@Coerce Object chunkStorage, MapTileChunk tileChunk, CallbackInfo ci) {
        CompletableFuture<Optional<NbtCompound>>[] futures = this.chunkNBTCompounds;
        if (futures == null || this.mapProcessor == null) return;

        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server == null) return;
        ServerWorld world = server.getWorld(this.mapProcessor.getMapWorld().getCurrentDimensionId());
        if (world == null) return;

        for (int zl = 0; zl < 4; zl++) {
            for (int xl = 0; xl < 4; xl++) {
                final int index = (zl << 2) | xl;
                CompletableFuture<Optional<NbtCompound>> original = futures[index];
                if (original == null) continue;
                final int chunkX = tileChunk.getX() * 4 + xl;
                final int chunkZ = tileChunk.getZ() * 4 + zl;
                futures[index] = original.thenApplyAsync(existing -> {
                    if (existing != null && existing.isPresent()) {
                        return existing; // the real, naturally generated chunk always wins
                    }
                    return Optional.ofNullable(Bridge.chunkNbt(world, chunkX, chunkZ));
                }, Bridge.EXEC);
            }
        }
    }
}
