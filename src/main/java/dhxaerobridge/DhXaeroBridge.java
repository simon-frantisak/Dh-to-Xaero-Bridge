package dhxaerobridge;

import dhxaerobridge.mc.Bridge;
import net.fabricmc.api.ClientModInitializer;

public class DhXaeroBridge implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Bridge.LOG.info("DH -> Xaero Bridge loaded. Distant Horizons LODs will be shown on Xaero's World Map "
                + "wherever no real chunk data exists. Natural map data is never replaced.");
    }
}
