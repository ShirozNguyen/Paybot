package com.naptien.compat.version;

import com.naptien.fabric.v1_15_x.FabricVersionAdapter1_15;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionAdapterFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-VersionFactory");
    private static VersionAdapter cachedAdapter = null;

    public static synchronized VersionAdapter getAdapter() {
        if (cachedAdapter != null) return cachedAdapter;
        cachedAdapter = new FabricVersionAdapter1_15();
        LOGGER.info("[PayBot] Loaded Native Adapter: FabricVersionAdapter1_15 for fabric-1.15.2");
        return cachedAdapter;
    }
}