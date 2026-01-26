package com.sulphate.chatcolor2.schedulers;

import com.sulphate.chatcolor2.commands.Setting;
import com.sulphate.chatcolor2.managers.ConfigsManager;
import com.sulphate.chatcolor2.managers.ConfirmationsManager;
import com.sulphate.chatcolor2.utils.Config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.sulphate.chatcolor2.main.ChatColor;
import com.sulphate.chatcolor2.utils.Messages;

public class ConfirmScheduler {

    private final Messages M;
    private final ConfirmationsManager confirmationsManager;
    private final YamlConfiguration mainConfig;

    private final Player player;
    private SchedulerAdapter.TaskWrapper task;
    private final Setting setting;
    private final Object value;

    public ConfirmScheduler(Messages M, ConfirmationsManager confirmationsManager, ConfigsManager configsManager, Player player, Setting setting, Object value) {
        this.M = M;
        this.confirmationsManager = confirmationsManager;

        this.player = player;
        this.setting = setting;
        this.value = value;

        mainConfig = configsManager.getConfig(Config.MAIN_CONFIG);
        run();
    }

    private void run() {
        long delayTicks = mainConfig.getInt(Setting.CONFIRM_TIMEOUT.getConfigPath()) * 20L;
        task = SchedulerAdapter.runForEntity(ChatColor.getPlugin(), player, () -> {
            player.sendMessage(M.PREFIX + M.DID_NOT_CONFIRM);
            confirmationsManager.removeConfirmingPlayer(player);
        }, delayTicks);
    }

    public void cancelScheduler() {
        task.cancel();
        confirmationsManager.removeConfirmingPlayer(player);
    }

    public Setting getSetting() {
        return setting;
    }

    public Object getValue() {
        return value;
    }

}
