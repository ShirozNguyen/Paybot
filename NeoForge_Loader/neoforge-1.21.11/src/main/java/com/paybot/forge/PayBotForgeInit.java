package com.paybot.forge;

import com.paybot.PayBotMod;
import net.neoforged.fml.common.Mod;

@Mod("paybot")
public class PayBotForgeInit {
    public PayBotForgeInit() {
        ForgeDependencyValidator.validate();
        PayBotMod.init();
    }
}
