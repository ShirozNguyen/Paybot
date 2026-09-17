package com.paybot.fabric;

import com.paybot.PayBotMod;
import net.fabricmc.api.ModInitializer;

public class PayBotFabricInit implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricDependencyValidator.validate();
        PayBotMod.init();
    }
}
