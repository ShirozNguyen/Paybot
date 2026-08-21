package com.naptien.fabric;

import com.naptien.PayBotMod;
import net.fabricmc.api.ModInitializer;

public class PayBotFabricInit implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricDependencyValidator.validate();
        PayBotMod.init();
    }
}
