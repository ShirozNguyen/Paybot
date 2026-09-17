package com.naptien.compat.version;

import com.naptien.fabric.v1_17_x.FabricVersionAdapter1_17;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionAdapterFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-VersionFactory");
    private static VersionAdapter cachedAdapter = null;

    public static synchronized VersionAdapter getAdapter() {
        if (cachedAdapter != null) return cachedAdapter;
        cachedAdapter = new FabricVersionAdapter1_17();
        LOGGER.info("[PayBot] Loaded Native Adapter: FabricVersionAdapter1_17 for fabric-1.17.1");
        return cachedAdapter;
    }
}