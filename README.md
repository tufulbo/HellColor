# HellColor

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Paper](https://img.shields.io/badge/Paper-1.20.6-1976D2?style=flat-square)](https://papermc.io/)
[![Adventure](https://img.shields.io/badge/Kyori%20Adventure-4.25.0-6C5CE7?style=flat-square)](https://github.com/KyoriPowered/adventure)
[![Build](https://img.shields.io/badge/Build-Passing-2ECC71?style=flat-square)](#)
[![Version](https://img.shields.io/badge/Version-1.0.0-gray?style=flat-square)](#)

HellColor is a lightweight, unified message formatting and dispatch library for Minecraft (Paper) plugins. It provides seamless cross-compatibility between Kyori Adventure MiniMessage, Legacy Minecraft color codes (`&` / `§`), RGB hexadecimal formats, and specialized delivery channels such as Titles, Action Bars, Boss Bars, and Toast Notifications via uniform string prefixes.

---

## Features

- **Multi-Format Parsing**: Automatically parses Legacy codes (`&c`, `§c`), Hex formats (`&#RRGGBB`), and modern MiniMessage formatting (`<gradient>`, `<bold>`, `<red>`).
- **Channel Prefixes**: Dispatches messages to Titles, Action Bars, Boss Bars, Advancement Toasts, and Sound Effects directly from configuration strings without custom code branching.
- **Sound Effects**: Plays Adventure/Bukkit sounds via `<sound:SOUND:VOLUME:PITCH:SOURCE>` inline or as standalone prefix.
- **Italic Reset**: Automatically disables default italic formatting across parsed components for consistent chat and GUI presentation.
- **Asynchronous Scheduling**: Handles temporary Boss Bar visibility and Toast advancement lifecycle management internally.
- **Audience Integration**: Native support for Kyori `Audience` and Paper `Player` instances.

---

## Installation

### Maven

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.helltown</groupId>
    <artifactId>HellColor</artifactId>
    <version>1.0.0</version>
    <scope>compile</scope>
</dependency>
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'org.helltown:HellColor:1.0.0'
}
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("org.helltown:HellColor:1.0.0")
}
```

---

## String Prefix Syntax

Messages passed to `ColorUtil.send()` can include special prefixes that dictate how and where the content is rendered.

### 1. Chat & General Components
Standard text with no prefix renders directly to the audience chat.

```text
<gradient:#ff5555:#55ff55>Welcome to the server!</gradient>
&#ffaa00Gold text with &lbold support
```

### 2. Action Bar
Displays text in the player's action bar. Supports optional timing (`<actionbar:duration>` or `<actionbar:fadeIn:stay:fadeOut>`).

```text
<actionbar><yellow>You found an item!
<actionbar:10:40:10><yellow>You found an item!
```

### 3. Titles and Subtitles
Displays title and subtitle on screen. Subtitles are delimited by `;;`, `\n`, `<nl>`, or `<newline>`.

```text
<title:fadeIn:stay:fadeOut>Title Text;;Subtitle Text
```

- **fadeIn / stay / fadeOut**: Durations in ticks (defaults to `20:20:20`).

Example:
```text
<title:10:40:10><bold><red>DEFEAT</red></bold>;;<gray>Better luck next time!
```

### 4. Boss Bar
Displays a temporary or persistent Adventure BossBar.

```text
<bossbar:COLOR:DURATION:OVERLAY>Message
```

- **COLOR**: `PINK`, `BLUE`, `RED`, `GREEN`, `YELLOW`, `PURPLE` (default), `WHITE`.
- **DURATION**: Duration in seconds before automatic removal (defaults to `20`). Use `0` for persistent display.
- **OVERLAY**: `PROGRESS` (default), `NOTCHED_6`, `NOTCHED_10`, `NOTCHED_12`, `NOTCHED_20`.

Example:
```text
<bossbar:RED:15:NOTCHED_6><bold><red>WORLD BOSS SPAWNED</red></bold>
```

### 5. Toast Notifications
Displays a popup advancement notification in the top-right corner.

```text
<toast:MATERIAL:FRAME>Title;;Description
```

- **MATERIAL**: Valid Bukkit `Material` identifier (defaults to `PAPER`).
- **FRAME**: Advancement frame type: `task`, `goal`, or `challenge` (defaults to `goal`).

Example:
```text
<toast:DIAMOND_SWORD:challenge><gold>Achievement Unlocked;;<yellow>Defeated 100 Players
```

### 6. Sound Effects
Plays sound effect to audience. Can be standalone or prefixed/combined with any message or channel.

```text
<sound:SOUND:VOLUME:PITCH:SOURCE>
```

- **SOUND**: Bukkit `Sound` enum name (e.g. `ENTITY_PLAYER_LEVELUP`) or Minecraft / Adventure sound key (e.g. `entity.player.levelup`, `minecraft:block.note_block.pling`, `custom:sfx`).
- **VOLUME**: Sound volume (float, defaults to `1.0`).
- **PITCH**: Sound pitch (float, defaults to `1.0`).
- **SOURCE**: Sound source channel: `MASTER` (default), `MUSIC`, `RECORD`, `WEATHER`, `BLOCK`, `HOSTILE`, `NEUTRAL`, `PLAYER`, `AMBIENT`, `VOICE`.

Examples:
```text
<sound:entity.player.levelup:1.0:1.0><title:10:40:10><gold>Level Up!;;<gray>You reached level 50
<sound:BLOCK_NOTE_BLOCK_PLING:1.0:2.0><actionbar><green>+100 Coins
<sound:ENTITY_EXPERIENCE_ORB_PICKUP:1.0:1.0>
```

---

## Java API Usage

### Sending Formatted Messages

```java
import org.helltown.color.ColorUtil;
import org.bukkit.entity.Player;

// Direct dispatch to player or console (Audience)
ColorUtil.send(player, "<gradient:#00c6ff:#0072ff>System initialized successfully.</gradient>");

// Sending with prefixes
ColorUtil.send(player, "<actionbar><green>Balance updated: +$500");
ColorUtil.send(player, "<title:10:30:10><gold>Level Up!;;<gray>You reached level 50");
ColorUtil.send(player, "<bossbar:YELLOW:10:PROGRESS><yellow>Event ending soon...");
ColorUtil.send(player, "<toast:NETHER_STAR:challenge><aqua>Quest Complete!;;<gray>Collected all relics");
ColorUtil.send(player, "<sound:entity.player.levelup:1.0:1.0>&aLevel Up!");

// Bulk sending
List<String> broadcastLines = List.of(
    "&7=============================",
    "<sound:ui.button.click:1.0:1.0><gold><bold>SERVER EVENT STARTED",
    "&7============================="
);
ColorUtil.send(player, broadcastLines);
```

### Parsing Components

```java
import net.kyori.adventure.text.Component;
import org.helltown.color.ColorUtil;

// Parse string to Adventure Component (prefixes stripped automatically)
Component component = ColorUtil.parse("&aHello &#3498dbWorld!");

// Parse list of strings
List<Component> lore = ColorUtil.parse(List.of(
    "&7Item Description",
    "<gradient:#ff9900:#ff5500>Special Lore</gradient>"
));
```

### Serialization & Access

```java
// Serialize Adventure Component back to MiniMessage string
String serialized = ColorUtil.serialize(component);

// Access underlying MiniMessage instance
MiniMessage miniMessage = ColorUtil.getMiniMessage();
```

---

## Requirements

- **Java**: 21 or higher
- **Server**: Paper 1.20.6+ (or any server platform implementing Kyori Adventure)

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
