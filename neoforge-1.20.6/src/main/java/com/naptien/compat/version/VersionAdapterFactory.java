package com.naptien.compat.version;

import com.naptien.forge.v_modern.NeoForgeVersionAdapterModern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionAdapterFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger("PayBot-VersionFactory");
    private static VersionAdapter cachedAdapter = null;

    public static synchronized VersionAdapter getAdapter() {
        if (cachedAdapter != null) return cachedAdapter;
        cachedAdapter = new NeoForgeVersionAdapterModern();
        LOGGER.info("[PayBot] Loaded Native Adapter: NeoForgeVersionAdapterModern for neoforge-1.20.6");
        return cachedAdapter;
    }
}