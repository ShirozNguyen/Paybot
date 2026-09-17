package com.naptien.forge;

import com.naptien.PayBotMod;
import net.minecraftforge.fml.common.Mod;

@Mod("paybot")
public class PayBotForgeInit {
    public PayBotForgeInit() {
        ForgeDependencyValidator.validate();
        PayBotMod.init();
    }
}
