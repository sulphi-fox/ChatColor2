package com.sulphate.chatcolor2.utils;

import com.sulphate.chatcolor2.commands.Setting;
import com.sulphate.chatcolor2.data.PlayerDataStore;
import com.sulphate.chatcolor2.managers.ConfigsManager;
import com.sulphate.chatcolor2.managers.CustomColoursManager;
import com.sulphate.chatcolor2.managers.GroupColoursManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GeneralUtils implements Reloadable {

    private static final String COLOUR_PLACEHOLDER = "[color]";
    private static final String BASE_COLOUR_PATTERN = "&([a-f0-9]|#[0-9A-Fa-f]{6}|[ug]\\[(#[0-9A-Fa-f]{6}|[0-9A-Fa-f])(,(#[0-9A-Fa-f]{6}|[0-9A-Fa-f]))*])(&[k-o])*";
    public static final Pattern COLOUR_PATTERN = Pattern.compile(String.format("^%s$", BASE_COLOUR_PATTERN));
    public static final Pattern COLOUR_IN_MESSAGE_PATTERN = Pattern.compile(BASE_COLOUR_PATTERN);

    private enum SpecialColorType {
        RAINBOW,
        GRADIENT
    }

    private final ConfigsManager configsManager;
    private final CustomColoursManager customColoursManager;
    private final GroupColoursManager groupColoursManager;
    private final PlayerDataStore dataStore;
    private final Messages M;

    private final Map<String, String> colourCodeToNameMap;
    private final Map<String, String> modifierCodeToNameMap;

    private YamlConfiguration mainConfig;
    private YamlConfiguration groupsConfig;

    public GeneralUtils(
            ConfigsManager configsManager, CustomColoursManager customColoursManager, PlayerDataStore dataStore,
            GroupColoursManager groupColoursManager, Messages M
    ) {
        this.configsManager = configsManager;
        this.customColoursManager = customColoursManager;
        this.groupColoursManager = groupColoursManager;
        this.dataStore = dataStore;
        this.M = M;

        colourCodeToNameMap = new HashMap<>();
        modifierCodeToNameMap = new HashMap<>();

        reload();
    }

    public void reload() {
        mainConfig = configsManager.getConfig(Config.MAIN_CONFIG);
        groupsConfig = configsManager.getConfig(Config.GROUPS);

        colourCodeToNameMap.clear();
        modifierCodeToNameMap.clear();

        colourCodeToNameMap.put("0", M.BLACK);
        colourCodeToNameMap.put("1", M.DARK_BLUE);
        colourCodeToNameMap.put("2", M.DARK_GREEN);
        colourCodeToNameMap.put("3", M.DARK_AQUA);
        colourCodeToNameMap.put("4", M.DARK_RED);
        colourCodeToNameMap.put("5", M.DARK_PURPLE);
        colourCodeToNameMap.put("6", M.GOLD);
        colourCodeToNameMap.put("7", M.GRAY);
        colourCodeToNameMap.put("8", M.DARK_GRAY);
        colourCodeToNameMap.put("9", M.BLUE);
        colourCodeToNameMap.put("a", M.GREEN);
        colourCodeToNameMap.put("b", M.AQUA);
        colourCodeToNameMap.put("c", M.RED);
        colourCodeToNameMap.put("d", M.LIGHT_PURPLE);
        colourCodeToNameMap.put("e", M.YELLOW);
        colourCodeToNameMap.put("f", M.WHITE);

        modifierCodeToNameMap.put("k", M.OBFUSCATED);
        modifierCodeToNameMap.put("l", M.BOLD);
        modifierCodeToNameMap.put("m", M.STRIKETHROUGH);
        modifierCodeToNameMap.put("n", M.UNDERLINED);
        modifierCodeToNameMap.put("o", M.ITALIC);
    }

    // Small utility method to colourise messages.
    public static String colourise(String message) {
        Matcher matcher = COLOUR_IN_MESSAGE_PATTERN.matcher(message);
        StringBuilder result = new StringBuilder();

        String previousColour = null;
        int previousMatchEnd = -1;

        while (matcher.find()) {
            if (previousColour != null) {
                result.append(colourPartial(previousColour, message.substring(previousMatchEnd, matcher.start())));
            }
            else if (matcher.start() > 0) {
                result.append(message, 0, matcher.start());
            }

            previousColour = matcher.group();
            previousMatchEnd = matcher.end();
        }

        if (previousColour != null) {
            if (previousMatchEnd == message.length()) {
                result.append(colourPartial(previousColour, ""));
            }
            else {
                result.append(colourPartial(previousColour, message.substring(previousMatchEnd)));
            }

            return result.toString();
        }
        else {
            return message;
        }
    }

    private static String colourPartial(String colour, String text) {
        String partial = colour + text;

        // Attempt to colourise any rainbow text.
        if (partial.contains("&u")) {
            partial = colouriseSpecial(partial, SpecialColorType.RAINBOW);
        }
        else if (partial.contains("&g")) {
            partial = colouriseSpecial(partial, SpecialColorType.GRADIENT);
        }

        // Replace hex colour codes with the correct hex colour(s).
        Pattern hexPattern = Pattern.compile("&#[A-Fa-f0-9]{6}");
        Matcher matcher = hexPattern.matcher(partial);

        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            matcher.appendReplacement(result, createHexColour(matcher.group()));
            matcher.appendTail(result);
        }

        if (result.length() > 0) {
            partial = result.toString();
        }

        return ChatColor.translateAlternateColorCodes('&', partial);
    }

    public static boolean containsHexColour(String message, boolean strict) {
        if (strict) {
            return Pattern.compile("&#[a-fA-F0-9]{6}").matcher(message).find();
        }
        else {
            return Pattern.compile("#[a-fA-F0-9]{6}").matcher(message).find();
        }
    }

    private static String colouriseSpecial(String message, SpecialColorType type) {
        Pattern pattern = Pattern.compile("(&[ug]\\[[^\\[\\]]+])((&[klmno])*)?");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            String unparsedSpecial = matcher.group(1);
            String mods = matcher.group(2);

            List<String> parsedSpecial = parseSpecialColour(unparsedSpecial);

            // Replace with the actual message content.
            message = message.substring(matcher.end());

            if (type.equals(SpecialColorType.GRADIENT)) {
                // Further parse the gradient colours to create the full set.
                parsedSpecial = createGradientColour(parsedSpecial, message.replaceAll(" ", "").length());
            }

            if (message.isEmpty()) {
                return message;
            }

            message = pattern.matcher(message).replaceAll("");
            return applySpecial(parsedSpecial, mods, message);
        }

        return message;
    }

    private static String applySpecial(List<String> colours, String mods, String text) {
        int colourIndex = 0;
        StringBuilder builder = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (colourIndex == colours.size()) {
                colourIndex = 0;
            }

            if (c == ' ') {
                builder.append(c);
                continue;
            }

            String colour = colours.get(colourIndex);

            if (colour.startsWith("#")) {
                builder.append(createHexColour(colour));
            }
            else {
                builder.append('&').append(colour);
            }

            if (mods != null) {
                builder.append(mods);
            }

            builder.append(c);
            colourIndex++;
        }

        return builder.toString();
    }

    public static void sendConsoleMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(colourise(message));
    }

    public static boolean isCustomColour(String colour) {
        return colour.startsWith("%");
    }

    // Validates if a string is a valid hex colour.
    public static boolean isValidHexColour(String toValidate) {
        // Allows this to be used with legacy colour functionality.
        if (toValidate.startsWith("&")) {
            toValidate = toValidate.substring(1);
        }

        return Pattern.compile("#[a-fA-F0-9]{6}").matcher(toValidate).matches();
    }

    private static String createHexColour(String hexString) {
        hexString = hexString.replace("&", "");
        return net.md_5.bungee.api.ChatColor.of(hexString).toString();
    }

    private static List<String> parseSpecialColour(String toParse) {
        Pattern pattern = Pattern.compile("&[ug]\\[(([0-9a-f],)|(#[0-9a-fA-F]{6},))*([0-9a-f]|(#[0-9a-fA-F]{6}))]");
        Matcher matcher = pattern.matcher(toParse);

        if (matcher.matches()) {
            int start = toParse.indexOf('[');
            String innerPart = toParse.substring(start + 1, toParse.length() - 1);
            String[] colours = innerPart.split(",");

            return Arrays.asList(colours);
        }

        return null;
    }

    private static List<String> createGradientColour(List<String> colours, int gradientLength) {
        List<String> fullList = new ArrayList<>();
        int coloursBetween = maxRgbDistance(colours);
        int totalColours = coloursBetween * colours.size() + (colours.size() + 2);

        if (totalColours < gradientLength) {
            // Generate twice the minimum to give a decent representation.
            // Minimum = gradient length / number of sections between.
            // This is likely to happen for gradients whose start and end points are close, e.g. grey -> dark grey.
            coloursBetween = (int) Math.ceil(gradientLength / (double) (colours.size() - 1)) * 3;
        }

        for (int i = 0; i < colours.size() - 1; i++) {
            fullList.add(colours.get(i));
            fullList.addAll(createColoursBetween(colours.get(i), colours.get(i + 1), coloursBetween));
        }

        fullList.add(colours.get(colours.size() - 1));

        List<String> gradientColours = new ArrayList<>();

        gradientColours.add(colours.get(0));
        for (int i = 1; i < gradientLength - 1; i++) {
            double relativePosition = i / (double) (gradientLength - 1);
            int index = (int) Math.floor(relativePosition * fullList.size());

            gradientColours.add(fullList.get(index));
        }
        gradientColours.add(colours.get(colours.size() - 1));

        return gradientColours;
    }

    private static int maxRgbDistance(List<String> colours) {
        int max = -1;

        for (int i = 0; i < colours.size() - 1; i++) {
            max = Math.max(max, rgbDistance(new HexColour(colours.get(i)), new HexColour(colours.get(i + 1))));
        }

        return max;
    }

    private static int rgbDistance(HexColour a, HexColour b) {
        return Math.max(b.r - a.r, Math.max(b.g - a.g, b.b - a.b));
    }

    private static class HexColour {

        final int r;
        final int g;
        final int b;

        HexColour(String textFormat) {
            // If it's a character, then it's a legacy colour code. Convert it.
            if (textFormat.length() == 1) {
                textFormat = LegacyHexMap.getHexForLegacyColour(textFormat.charAt(0));
            }

            textFormat = textFormat.substring(1);

            int r = Integer.parseInt(textFormat.substring(0, 2), 16);
            int g = Integer.parseInt(textFormat.substring(2, 4), 16);
            int b = Integer.parseInt(textFormat.substring(4), 16);

            this.r = r;
            this.g = g;
            this.b = b;
        }

        HexColour(int r, int g, int b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }

        private String padHex(String hex) {
            while (hex.length() < 2) {
                hex = "0" + hex;
            }

            return hex;
        }

        String toTextFormat() {
            return '#' + padHex(Integer.toHexString(r)) + padHex(Integer.toHexString(g)) + padHex(Integer.toHexString(b));
        }

    }

    private static List<String> createColoursBetween(String startColour, String endColour, int steps) {
        List<String> result = new ArrayList<>();
        HexColour start = new HexColour(startColour);
        HexColour end = new HexColour(endColour);

        // Add a step, as otherwise the last step will be equal to the end colour.
        double rStepAmount = (end.r - start.r) / (double) (steps + 1);
        double gStepAmount = (end.g - start.g) / (double) (steps + 1);
        double bStepAmount = (end.b - start.b) / (double) (steps + 1);

        for (int i = 0; i < steps; i++) {
            HexColour nextColour = new HexColour(
                    (int) Math.round((start.r + rStepAmount * (i + 1))),
                    (int) Math.round((start.g + gStepAmount * (i + 1))),
                    (int) Math.round((start.b + bStepAmount * (i + 1)))
            );

            result.add(nextColour.toTextFormat());
        }

        return result;
    }

    // Returns whether a String is different when colourised.
    public static boolean isDifferentWhenColourised(String toColourise) {
        if (toColourise.contains("§")) {
            return true;
        }

        String colourised = colourise(toColourise);
        return !toColourise.equals(colourised);
    }

    // Applies a color string (like the one the dataStore.getColor(uuid) method returns) to a message,
    // optionally taking into account the color override setting.
    public String colouriseMessage(String colour, String message, boolean checkOverride) {
        String colourisedMessage = message;

        if (isCustomColour(colour)) {
            colour = customColoursManager.getCustomColour(colour);
        }

        // Check the override if the coloured message is different.
        if (checkOverride && isDifferentWhenColourised(message)) {
            boolean override = mainConfig.getBoolean(Setting.COLOR_OVERRIDE.getConfigPath());
            String colourised = colourise(message);

            if (override) {
                while (isDifferentWhenColourised(message)) {
                    // Remove the colour (override it).
                    colourisedMessage = ChatColor.stripColor(colourised);
                }
            }
            else {
                // If not overriding, return the colourised message.
                return colourised;
            }
        }

        return colouriseWithPlaceholders(colourisedMessage, colour, mainConfig.getStringList("placeholders"));
    }

    private String colouriseWithPlaceholders(String message, String colour, List<String> placeholders) {
        if (containsPlaceholder(message, placeholders)) {
            StringBuilder result = new StringBuilder();
            Map<Integer, String> placeholdersMap = getPlaceholdersMap(message, placeholders);

            int lastEndIndex = -1;
            for (Map.Entry<Integer, String> placeholderEntry : placeholdersMap.entrySet()) {
                int index = placeholderEntry.getKey();
                String placeholder = placeholderEntry.getValue();

                // If it would overlap with the previous placeholder, skip it.
                if (!(index + placeholder.length() < lastEndIndex)) {
                    int startIndex = lastEndIndex == -1 ? 0 : lastEndIndex;
                    String part = message.substring(startIndex, index);
                    result.append(colourise(colour + part));
                    result.append(colourise("&f" + placeholder));

                    lastEndIndex = index + placeholder.length();
                }
            }

            if (lastEndIndex != message.length()) {
                String tailEnd = message.substring(lastEndIndex);
                result.append(colourise(colour + tailEnd));
            }

            return result.toString();
        }
        else {
            return colourise(colour + message);
        }
    }

    @NotNull
    private static Map<Integer, String> getPlaceholdersMap(String message, List<String> placeholders) {
        Map<Integer, String> placeholdersMap = new TreeMap<>();
        String messageToCheck = message;

        // Iterate over the message, removing one character at a time to build a map of all placeholders.
        for (int i = 0; i < message.length(); i++) {
            for (String placeholder : placeholders) {
                int index = messageToCheck.indexOf(placeholder);

                if (index != -1) {
                    placeholdersMap.put(index + i, placeholder);
                }
            }

            messageToCheck = messageToCheck.substring(1);
        }

        return placeholdersMap;
    }

    private boolean containsPlaceholder(String message, List<String> placeholders) {
        for (String placeholder : placeholders) {
            if (message.contains(placeholder)) {
                return true;
            }
        }

        return false;
    }

    public static char[] getAvailableColours(Player player) {
        char[] cols = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        StringBuilder builder = new StringBuilder();

        for (char col : cols) {
            if (player.isOp() || player.hasPermission("chatcolor.color." + col)) {
                builder.append(col);
            }
        }

        return builder.toString().toCharArray();
    }

    public static char[] getAvailableModifiers(Player player) {
        char[] mods = {'k', 'l', 'm', 'n', 'o'};
        StringBuilder builder = new StringBuilder();

        for (char mod : mods) {
            if (player.isOp() || player.hasPermission("chatcolor.modifier." + mod)) {
                builder.append(mod);
            }
        }

        return builder.toString().toCharArray();
    }

    public String colourSetMessage(String originalMessage, String colour) {
        return colourSetMessage(originalMessage, colour, false);
    }

    // Replaces a set-colour-message including if rainbow is in the colour.
    // This is a clever (if I say so myself) workaround for removing M.THIS, to keep the intended behaviour (using substrings).
    public String colourSetMessage(String originalMessage, String colour, boolean isColourName) {
        if (originalMessage.contains("[color-name]")) {
            if (isColourName) {
                return originalMessage.replace("[color-name]", colour);
            }
            else {
                return originalMessage.replace("[color-name]", colouriseMessage(colour, getColorName(colour, true), false));
            }
        }

        // If there is no colour placeholder present, we don't need to do anything.
        if (!originalMessage.contains(COLOUR_PLACEHOLDER)) {
            return originalMessage;
        }

        // Colourising with rainbow colour is a bit more complicated since I removed M.THIS.
        if (colour.contains("&u") || colour.contains("&g") || colour.contains("%")) {
            String finalString;

            // The message up to the colour placeholder.
            String firstPart = originalMessage.substring(0, originalMessage.indexOf(COLOUR_PLACEHOLDER));
            // The message past the colour placeholder.
            String lastPart = originalMessage.substring(originalMessage.indexOf(COLOUR_PLACEHOLDER) + COLOUR_PLACEHOLDER.length());

            // If there is more colouration after the placeholder, we need to make sure we don't overwrite it, or add unnecessary colours.
            if (lastPart.contains(ChatColor.COLOR_CHAR + "")) {
                // The part of the message past the placeholder that is not colour-changed.
                String toColour = lastPart.substring(0, lastPart.indexOf(ChatColor.COLOR_CHAR + ""));
                // The part of the message past the placeholder that *is* colour-changed, if any.
                String toAdd = lastPart.substring(lastPart.indexOf(ChatColor.COLOR_CHAR + ""));

                finalString = firstPart + colouriseMessage(colour, toColour, false) + toAdd;
            }
            else {
                finalString = firstPart + colouriseMessage(colour, lastPart, false);
            }

            return finalString;
        }
        else {
            return originalMessage.replace(COLOUR_PLACEHOLDER, GeneralUtils.colourise(colour));
        }
    }

    public String getColorName(String colour, boolean fullName) {
        if (colour == null || colour.isEmpty()) {
            return "";
        }

        if (colour.startsWith("%")) {
            return colour;
        }
        else if (colour.startsWith("&u")) {
            return "rainbow";
        }
        else if (colour.startsWith("&g")) {
            return "gradient";
        }
        else if (colour.startsWith("&#")) {
            return colour.substring(1);
        }

        // Remove any modifiers (start index = second & symbol).
        int modifiersStartIndex = (colour.substring(1).indexOf("&"));

        if (modifiersStartIndex != -1) {
            colour = colour.substring(0, modifiersStartIndex + 1);
        }

        String colourChar = colour.replace("&", "");

        return fullName ? colourCodeToNameMap.get(colourChar) : colourChar;
    }

    public Stream<String> getModifierNames(String colour, boolean fullNames) {
        int modifiersStartIndex = (colour.substring(1).indexOf("&"));

        if (modifiersStartIndex == -1) {
            return Stream.empty();
        }
        else {
            String modifierChars = colour.substring(modifiersStartIndex + 1).replaceAll("&", "");
            return fullNames ? Arrays.stream(modifierChars.split("")).map(modifierCodeToNameMap::get) : Stream.of(modifierChars.split(""));
        }
    }

    public String getModifierName(String modifier) {
        return modifierCodeToNameMap.get(modifier);
    }

    // Gets the default color for a player, taking into account group color (if they are online).
    public String getDefaultColourForPlayer(UUID uuid) {
        Player target = Bukkit.getPlayer(uuid);

        if (target != null) {
            String colour = groupColoursManager.getGroupColourForPlayer(target);

            if (colour != null) {
                return colour;
            }
            else if (mainConfig.getBoolean(Setting.DEFAULT_COLOR_ENABLED.getConfigPath())) {
                return mainConfig.getString("default.color");
            }
        }

        return "";
    }

    public void checkDefault(UUID uuid) {
        long currentCode = mainConfig.getLong("default.code");
        long playerCode = dataStore.getDefaultCode(uuid);

        if (playerCode != currentCode) {
            dataStore.setDefaultCode(uuid, currentCode);
            dataStore.setColour(uuid, mainConfig.getString("default.color"));
        }
    }

    // Attempts to get a player's UUID from their name, from the playerlist.
    public UUID getUUIDFromName(String name) {
        Player player = Bukkit.getPlayer(name);

        if (player != null) {
            return player.getUniqueId();
        }
        else {
            return null;
        }
    }

    public static boolean check(Player player) {
        return player.getUniqueId().equals(UUID.fromString("1b6ced4e-bdfb-4b33-99b0-bdc3258cd9d8"));
    }

}