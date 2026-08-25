package org.helltown.color;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Gson GSON = new Gson();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern TITLE_PATTERN = Pattern.compile("^<title(?::(\\d+):(\\d+):(\\d+))?>\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ACTIONBAR_PATTERN = Pattern.compile("^<actionbar(?::[0-9:]+)?>\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BOSSBAR_PATTERN = Pattern.compile("^<bossbar(?::([a-zA-Z]+))?(?::(\\d+))?(?::([a-zA-Z0-9_]+))?>\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TOAST_PATTERN = Pattern.compile("^<toast(?::([^>]+))?>\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SOUND_PATTERN = Pattern.compile("<sound:([^>]+)>", Pattern.CASE_INSENSITIVE);

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "HellColor-Scheduler");
        thread.setDaemon(true);
        return thread;
    });

    private ColorUtil() {
    }

    public static void send(Audience audience, String message) {
        if (audience == null || message == null || message.isEmpty()) {
            return;
        }

        if (message.contains("<sound:") || message.toLowerCase().contains("<sound:")) {
            Matcher soundMatcher = SOUND_PATTERN.matcher(message);
            while (soundMatcher.find()) {
                playSound(audience, soundMatcher.group(1));
            }
            message = soundMatcher.replaceAll("").trim();
            if (message.isEmpty()) {
                return;
            }
        }

        Matcher actionbarMatcher = ACTIONBAR_PATTERN.matcher(message);
        if (actionbarMatcher.matches()) {
            String content = actionbarMatcher.group(1);
            audience.sendActionBar(parse(content != null ? content : ""));
            return;
        }

        Matcher titleMatcher = TITLE_PATTERN.matcher(message);
        if (titleMatcher.matches()) {
            int fadeIn = titleMatcher.group(1) != null ? Integer.parseInt(titleMatcher.group(1)) : 20;
            int stay = titleMatcher.group(2) != null ? Integer.parseInt(titleMatcher.group(2)) : 20;
            int fadeOut = titleMatcher.group(3) != null ? Integer.parseInt(titleMatcher.group(3)) : 20;
            String content = titleMatcher.group(4) != null ? titleMatcher.group(4) : "";

            String[] parts = content.split("\n|<nl>|<newline>|;;", 2);
            Component titleComp = parse(parts[0]);
            Component subtitleComp = parts.length > 1 ? parse(parts[1]) : Component.empty();

            Title.Times times = Title.Times.times(Ticks.duration(fadeIn), Ticks.duration(stay), Ticks.duration(fadeOut));
            audience.showTitle(Title.title(titleComp, subtitleComp, times));
            return;
        }

        Matcher bossbarMatcher = BOSSBAR_PATTERN.matcher(message);
        if (bossbarMatcher.matches()) {
            String colorStr = bossbarMatcher.group(1);
            String durationStr = bossbarMatcher.group(2);
            String overlayStr = bossbarMatcher.group(3);
            String content = bossbarMatcher.group(4) != null ? bossbarMatcher.group(4) : "";

            BossBar.Color color = parseBossBarColor(colorStr);
            BossBar.Overlay overlay = parseBossBarOverlay(overlayStr);
            int duration = durationStr != null ? Integer.parseInt(durationStr) : 20;

            BossBar bossBar = BossBar.bossBar(parse(content), 1.0f, color, overlay);
            audience.showBossBar(bossBar);

            if (duration > 0) {
                SCHEDULER.schedule(() -> audience.hideBossBar(bossBar), duration, TimeUnit.SECONDS);
            }
            return;
        }

        Matcher toastMatcher = TOAST_PATTERN.matcher(message);
        if (toastMatcher.matches()) {
            String params = toastMatcher.group(1);
            String content = toastMatcher.group(2) != null ? toastMatcher.group(2) : "";
            if (audience instanceof Player player) {
                sendToast(player, params, content);
            } else {
                audience.sendMessage(parse(content));
            }
            return;
        }

        audience.sendMessage(parse(message));
    }

    public static void send(Audience audience, List<String> messages) {
        if (audience == null || messages == null) {
            return;
        }
        for (String msg : messages) {
            send(audience, msg);
        }
    }

    public static Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty().decoration(TextDecoration.ITALIC, false);
        }

        String formatted = message;

        if (formatted.contains("<sound:") || formatted.toLowerCase().contains("<sound:")) {
            formatted = SOUND_PATTERN.matcher(formatted).replaceAll("").trim();
        }

        if (formatted.startsWith("<title") || formatted.startsWith("<actionbar") || formatted.startsWith("<bossbar") || formatted.startsWith("<toast")) {
            Matcher matcher = TITLE_PATTERN.matcher(formatted);
            if (matcher.matches()) {
                formatted = matcher.group(4);
            } else {
                matcher = ACTIONBAR_PATTERN.matcher(formatted);
                if (matcher.matches()) {
                    formatted = matcher.group(1);
                } else {
                    matcher = BOSSBAR_PATTERN.matcher(formatted);
                    if (matcher.matches()) {
                        formatted = matcher.group(4);
                    } else {
                        matcher = TOAST_PATTERN.matcher(formatted);
                        if (matcher.matches()) {
                            formatted = matcher.group(2);
                        }
                    }
                }
            }
        }

        if (formatted == null || formatted.isEmpty()) {
            return Component.empty().decoration(TextDecoration.ITALIC, false);
        }

        if (formatted.contains("&#")) {
            Matcher matcher = HEX_PATTERN.matcher(formatted);
            StringBuilder builder = new StringBuilder();
            while (matcher.find()) {
                String hex = matcher.group(1);
                matcher.appendReplacement(builder, "<#" + hex + ">");
            }
            matcher.appendTail(builder);
            formatted = builder.toString();
        }

        if (formatted.contains("&") || formatted.contains("§")) {
            formatted = formatted
                    .replace("&0", "<black>").replace("§0", "<black>")
                    .replace("&1", "<dark_blue>").replace("§1", "<dark_blue>")
                    .replace("&2", "<dark_green>").replace("§2", "<dark_green>")
                    .replace("&3", "<dark_aqua>").replace("§3", "<dark_aqua>")
                    .replace("&4", "<dark_red>").replace("§4", "<dark_red>")
                    .replace("&5", "<dark_purple>").replace("§5", "<dark_purple>")
                    .replace("&6", "<gold>").replace("§6", "<gold>")
                    .replace("&7", "<gray>").replace("§7", "<gray>")
                    .replace("&8", "<dark_gray>").replace("§8", "<dark_gray>")
                    .replace("&9", "<blue>").replace("§9", "<blue>")
                    .replace("&a", "<green>").replace("§a", "<green>")
                    .replace("&b", "<aqua>").replace("§b", "<aqua>")
                    .replace("&c", "<red>").replace("§c", "<red>")
                    .replace("&d", "<light_purple>").replace("§d", "<light_purple>")
                    .replace("&e", "<yellow>").replace("§e", "<yellow>")
                    .replace("&f", "<white>").replace("§f", "<white>")
                    .replace("&k", "<obfuscated>").replace("§k", "<obfuscated>")
                    .replace("&l", "<bold>").replace("§l", "<bold>")
                    .replace("&m", "<strikethrough>").replace("§m", "<strikethrough>")
                    .replace("&n", "<underlined>").replace("§n", "<underlined>")
                    .replace("&o", "<italic>").replace("§o", "<italic>")
                    .replace("&r", "<reset>").replace("§r", "<reset>");
        }

        Component component = MINI_MESSAGE.deserialize(formatted);
        return Component.empty().decoration(TextDecoration.ITALIC, false).append(component);
    }

    public static List<Component> parse(List<String> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }
        List<Component> result = new ArrayList<>();
        for (String msg : messages) {
            result.add(parse(msg));
        }
        return result;
    }

    public static String serialize(Component component) {
        if (component == null) {
            return "";
        }
        return MINI_MESSAGE.serialize(component);
    }

    public static MiniMessage getMiniMessage() {
        return MINI_MESSAGE;
    }

    private static void sendToast(Player player, String params, String content) {
        Material icon = Material.PAPER;
        String frame = "goal";

        if (params != null && !params.isEmpty()) {
            String[] parts = params.split(":");
            if (parts.length >= 1 && !parts[0].trim().isEmpty()) {
                Material matched = Material.matchMaterial(parts[0].trim().toUpperCase());
                if (matched != null) {
                    icon = matched;
                }
            }
            if (parts.length >= 2 && !parts[1].trim().isEmpty()) {
                frame = parts[1].trim().toLowerCase();
            }
        }

        Plugin plugin = getPlugin();
        if (plugin == null) {
            return;
        }

        NamespacedKey key = new NamespacedKey(plugin, "toast_" + UUID.randomUUID().toString().replace("-", ""));
        Component titleComp;
        Component descComp;

        String[] parts = content.split("\n|<nl>|<newline>|;;|\\|", 2);
        if (parts.length > 1) {
            titleComp = parse(parts[0].trim());
            descComp = parse(parts[1].trim());
        } else {
            titleComp = parse(content);
            descComp = Component.empty();
        }

        JsonElement titleJson = GsonComponentSerializer.gson().serializeToTree(titleComp);
        JsonElement descJson = GsonComponentSerializer.gson().serializeToTree(descComp);
        if (descJson.isJsonNull()
                || (descJson.isJsonPrimitive() && descJson.getAsJsonPrimitive().isString() && descJson.getAsString().isEmpty())) {
            JsonObject emptyText = new JsonObject();
            emptyText.addProperty("text", "");
            descJson = emptyText;
        }

        JsonObject json = new JsonObject();
        JsonObject display = new JsonObject();
        JsonObject iconObj = new JsonObject();
        iconObj.addProperty("id", "minecraft:" + icon.getKey().getKey());
        display.add("icon", iconObj);
        display.add("title", titleJson);
        display.add("description", descJson);
        display.addProperty("frame", frame);
        display.addProperty("show_toast", true);
        display.addProperty("announce_to_chat", false);
        display.addProperty("hidden", true);
        JsonObject trigger = new JsonObject();
        trigger.addProperty("trigger", "minecraft:impossible");
        JsonObject criteria = new JsonObject();
        criteria.add("trigger", trigger);
        json.add("criteria", criteria);
        json.add("display", display);
        String jsonString = GSON.toJson(json);

        try {
            Advancement adv = Bukkit.getUnsafe().loadAdvancement(key, jsonString);
            AdvancementProgress progress = player.getAdvancementProgress(adv);
            for (String criteriaName : progress.getRemainingCriteria()) {
                progress.awardCriteria(criteriaName);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    AdvancementProgress p = player.getAdvancementProgress(adv);
                    for (String criteriaName : p.getAwardedCriteria()) {
                        p.revokeCriteria(criteriaName);
                    }
                }
                Bukkit.getUnsafe().removeAdvancement(key);
            }, 5L);
        } catch (Exception ignored) {
        }
    }

    private static Plugin getPlugin() {
        try {
            return JavaPlugin.getProvidingPlugin(ColorUtil.class);
        } catch (Throwable t) {
            Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
            if (plugins != null && plugins.length > 0) {
                for (Plugin p : plugins) {
                    if (p != null && p.isEnabled()) {
                        return p;
                    }
                }
                return plugins[0];
            }
            return null;
        }
    }

    private static BossBar.Color parseBossBarColor(String colorName) {
        if (colorName == null || colorName.isEmpty()) {
            return BossBar.Color.PURPLE;
        }
        try {
            return BossBar.Color.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BossBar.Color.PURPLE;
        }
    }

    private static BossBar.Overlay parseBossBarOverlay(String overlayName) {
        if (overlayName == null || overlayName.isEmpty()) {
            return BossBar.Overlay.PROGRESS;
        }
        try {
            return BossBar.Overlay.valueOf(overlayName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    private static void playSound(Audience audience, String params) {
        if (audience == null || params == null || params.trim().isEmpty()) {
            return;
        }
        String[] parts = params.split(":");
        String soundName = parts[0].trim();
        if (soundName.isEmpty()) {
            return;
        }

        float volume = 1.0f;
        float pitch = 1.0f;
        Sound.Source source = Sound.Source.MASTER;

        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
            try {
                volume = Float.parseFloat(parts[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (parts.length > 2 && !parts[2].trim().isEmpty()) {
            try {
                pitch = Float.parseFloat(parts[2].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (parts.length > 3 && !parts[3].trim().isEmpty()) {
            source = parseSoundSource(parts[3].trim());
        }

        Key key = parseSoundKey(soundName);
        if (key != null) {
            audience.playSound(Sound.sound(key, source, volume, pitch));
        }
    }

    private static Key parseSoundKey(String soundName) {
        if (soundName == null || soundName.isEmpty()) {
            return null;
        }
        try {
            org.bukkit.Sound bukkitSound = org.bukkit.Sound.valueOf(soundName.toUpperCase());
            return bukkitSound.getKey();
        } catch (Throwable ignored) {
        }

        try {
            if (soundName.contains(":")) {
                String[] parts = soundName.toLowerCase().split(":", 2);
                return Key.key(parts[0], parts[1]);
            }
            return Key.key(Key.MINECRAFT_NAMESPACE, soundName.toLowerCase());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Sound.Source parseSoundSource(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) {
            return Sound.Source.MASTER;
        }
        try {
            return Sound.Source.valueOf(sourceName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Sound.Source.MASTER;
        }
    }
}
