package com.naptien.compat.version;

import com.naptien.compat.McVersionHelper;
import com.naptien.fabric.v1_14_x.FabricVersionAdapter1_14;
import com.naptien.fabric.v1_15_x.FabricVersionAdapter1_15;
import com.naptien.fabric.v1_16_x.FabricVersionAdapter1_16;
import com.naptien.fabric.v1_17_x.FabricVersionAdapter1_17;
import com.naptien.fabric.v1_18_x.FabricVersionAdapter1_18;
import com.naptien.fabric.v1_19_x.FabricVersionAdapter1_19;
import com.naptien.fabric.v1_20_x.FabricVersionAdapter1_20;
import com.naptien.fabric.v1_21_x.FabricVersionAdapter1_21;

public class VersionAdapterFactory {
    private static VersionAdapter adapter;

    public static synchronized VersionAdapter getAdapter() {
        if (adapter != null) return adapter;
        String ver = McVersionHelper.getMinecraftVersion();
        if (ver.startsWith("1.21")) adapter = new FabricVersionAdapter1_21();
        else if (ver.startsWith("1.20")) adapter = new FabricVersionAdapter1_20();
        else if (ver.startsWith("1.19")) adapter = new FabricVersionAdapter1_19();
        else if (ver.startsWith("1.18")) adapter = new FabricVersionAdapter1_18();
        else if (ver.startsWith("1.17")) adapter = new FabricVersionAdapter1_17();
        else if (ver.startsWith("1.16")) adapter = new FabricVersionAdapter1_16();
        else if (ver.startsWith("1.15")) adapter = new FabricVersionAdapter1_15();
        else adapter = new FabricVersionAdapter1_14();
        return adapter;
    }
}
