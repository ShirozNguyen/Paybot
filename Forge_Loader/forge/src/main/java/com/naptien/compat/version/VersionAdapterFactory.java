package com.naptien.compat.version;

import com.naptien.compat.McVersionHelper;
import com.naptien.forge.v1_14_x.ForgeVersionAdapter1_14;
import com.naptien.forge.v1_15_x.ForgeVersionAdapter1_15;
import com.naptien.forge.v1_16_x.ForgeVersionAdapter1_16;
import com.naptien.forge.v1_17_x.ForgeVersionAdapter1_17;
import com.naptien.forge.v1_18_x.ForgeVersionAdapter1_18;
import com.naptien.forge.v1_19_x.ForgeVersionAdapter1_19;
import com.naptien.forge.v1_20_x.ForgeVersionAdapter1_20;
import com.naptien.forge.v1_21_x.ForgeVersionAdapter1_21;

public class VersionAdapterFactory {
    private static VersionAdapter adapter;

    public static synchronized VersionAdapter getAdapter() {
        if (adapter != null) return adapter;
        String ver = McVersionHelper.getMinecraftVersion();
        if (ver.startsWith("1.21")) adapter = new ForgeVersionAdapter1_21();
        else if (ver.startsWith("1.20")) adapter = new ForgeVersionAdapter1_20();
        else if (ver.startsWith("1.19")) adapter = new ForgeVersionAdapter1_19();
        else if (ver.startsWith("1.18")) adapter = new ForgeVersionAdapter1_18();
        else if (ver.startsWith("1.17")) adapter = new ForgeVersionAdapter1_17();
        else if (ver.startsWith("1.16")) adapter = new ForgeVersionAdapter1_16();
        else if (ver.startsWith("1.15")) adapter = new ForgeVersionAdapter1_15();
        else adapter = new ForgeVersionAdapter1_14();
        return adapter;
    }
}
