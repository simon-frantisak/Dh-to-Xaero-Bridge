package dhxaerobridge.mixin;

import dhxaerobridge.mc.RegionScanner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.MapProcessor;
import xaero.map.file.MapSaveLoad;

/** After Xaero has looked through your world's region files, we add the LOD-only regions too. */
@Mixin(value = MapSaveLoad.class, remap = false)
public abstract class MapSaveLoadMixin {
    @Shadow(remap = false)
    private MapProcessor mapProcessor;

    @Inject(method = "detectRegions", at = @At("RETURN"), remap = false)
    private void dhxb$addLodRegions(int attempts, CallbackInfo ci) {
        RegionScanner.start(this.mapProcessor);
    }
}
