package com.naptien.compat.version;

import com.naptien.fabric.v1_14_x.FabricVersionAdapter1_14;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionAdapterFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-VersionFactory");
    private static VersionAdapter cachedAdapter = null;

    public static synchronized VersionAdapter getAdapter() {
        if (cachedAdapter != null) return cachedAdapter;
        cachedAdapter = new FabricVersionAdapter1_14();
        LOGGER.info("[PayBot] Loaded Native Adapter: FabricVersionAdapter1_14 for fabric-1.14.3");
        return cachedAdapter;
    }
}