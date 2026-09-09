package com.naptien.forge;

import com.naptien.PayBotMod;
import net.neoforged.fml.common.Mod;

@Mod("paybot")
public class PayBotForgeInit {
    public PayBotForgeInit() {
        ForgeDependencyValidator.validate();
        PayBotMod.init();
    }
}
