package com.paybot.gui.setup;

import com.paybot.PayBotPlugin;
import org.bukkit.entity.Player;

/**
 * SetupGuiManager — v7.0.0 (Part 161)
 * TUÂN THỦ RULE 17: Quản lý điều phối phiên làm việc GUI Setup của Admin.
 */
public class SetupGuiManager {

    private final PayBotPlugin plugin;

    public SetupGuiManager(PayBotPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player, SetupMenuType type) {
        if (player == null || type == null) return;

        switch (type) {
            case MAIN_HUB:
                new SetupMainHubGui(plugin).open(player);
                break;
            case SEPAY:
                new SetupSePayGui(plugin).open(player);
                break;
            case CARD_PROVIDER:
                new SetupCardProviderGui(plugin).open(player);
                break;
            case REWARD_COMMAND:
                new SetupRewardCommandGui(plugin).open(player);
                break;
            case MILESTONE_LIST:
                new SetupMilestoneListGui(plugin, false, 1).open(player);
                break;
            case MILESTONE_RESET:
                new SetupMilestoneResetGui(plugin, false).open(player);
                break;
            case DATABASE_SYSTEM:
                new SetupDatabaseSystemGui(plugin).open(player);
                break;
            case PLAYER_GUI_CUSTOMIZER:
                new SetupPlayerGuiCustomizer(plugin).open(player);
                break;
            case TRANSACTION_MANAGER:
                new SetupTransactionManagerGui(plugin, "ALL", 1).open(player);
                break;
            case SIMULATION_TEST:
                new SetupSimulationTestGui(plugin).open(player);
                break;
            case DISCORD:
                new SetupDiscordGui(plugin).open(player);
                break;
            default:
                new SetupMainHubGui(plugin).open(player);
                break;
        }
    }
}
