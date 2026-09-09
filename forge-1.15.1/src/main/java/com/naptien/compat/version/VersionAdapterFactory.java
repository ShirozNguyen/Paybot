package com.naptien.compat.version;

import com.naptien.forge.v1_15_x.ForgeVersionAdapter1_15;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionAdapterFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-VersionFactory");
    private static VersionAdapter cachedAdapter = null;

    public static synchronized VersionAdapter getAdapter() {
        if (cachedAdapter != null) return cachedAdapter;
        cachedAdapter = new ForgeVersionAdapter1_15();
        LOGGER.info("[PayBot] Loaded Native Adapter: ForgeVersionAdapter1_15 for forge-1.15.1");
        return cachedAdapter;
    }
}