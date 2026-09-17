package com.paybot.forge;

import com.paybot.PayBotMod;
import net.minecraftforge.fml.common.Mod;

@Mod("paybot")
public class PayBotForgeInit {
    public PayBotForgeInit() {
        ForgeDependencyValidator.validate();
        PayBotMod.init();
    }
}
