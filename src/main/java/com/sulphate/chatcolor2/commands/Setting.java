package com.sulphate.chatcolor2.commands;

public enum Setting {

    AUTO_SAVE(SettingDataType.BOOLEAN),
    SAVE_INTERVAL(SettingDataType.INTEGER),
    COLOR_OVERRIDE(SettingDataType.BOOLEAN),
    NOTIFY_OTHERS(SettingDataType.BOOLEAN),
    JOIN_MESSAGE(SettingDataType.BOOLEAN),
    CONFIRM_TIMEOUT(SettingDataType.INTEGER),
    DEFAULT_COLOR(SettingDataType.COLOUR_STRING),
    COMMAND_NAME(SettingDataType.COMMAND_NAME),
    FORCE_GROUP_COLORS(SettingDataType.BOOLEAN),
    DEFAULT_COLOR_ENABLED(SettingDataType.BOOLEAN),
    COMMAND_OPENS_GUI(SettingDataType.BOOLEAN),
    IGNORE_SYMBOL_PREFIXES(SettingDataType.BOOLEAN),
    REMOVE_INACCESSIBLE_COLORS(SettingDataType.BOOLEAN),
    HIDE_CUSTOM_COLOR_PREFIX(SettingDataType.BOOLEAN),
    RESET(SettingDataType.NONE);

    private final SettingDataType dataType;
    private final String configPath;

    Setting(SettingDataType dataType) {
        this.dataType = dataType;
        configPath = "settings." + getName();
    }

    public SettingDataType getDataType() {
        return dataType;
    }

    public String getConfigPath() {
        return configPath;
    }

    public String getName() {
        return name().replaceAll("_", "-").toLowerCase();
    }

    public static Setting getSetting(String name) {
        name = name.replaceAll("-", "_").toUpperCase();
        return Setting.valueOf(name);
    }

}
