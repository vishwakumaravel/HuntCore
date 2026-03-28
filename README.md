# HuntCore

HuntCore is a simple, polished Paper minigame plugin for a hunt-style server. Version 1 focuses on a clean lobby flow, role selection, match start validation, runner head start, and same-dimension hunter compass tracking.

## Requirements

- Java 21+
- A local Paper server for Minecraft 1.21.x

Paper's current project setup guide shows Paper API with Gradle Kotlin DSL and Java 21 toolchains:
https://docs.papermc.io/paper/dev/project-setup

## Project Layout

- `src/main/java/com/huntcore` contains the plugin code
- `src/main/resources/plugin.yml` registers the plugin and commands
- `src/main/resources/config.yml` contains tweakable values for the lobby, countdowns, and match spawn settings

## Build The Jar

From the project root:

```powershell
.\gradlew.bat build
```

The output jar will be created in:

```text
build/libs/HuntCore-1.0.0-SNAPSHOT.jar
```

If you already have Gradle installed, `gradle build` works too.

## Run On A Local Paper Server

1. Build the plugin jar.
2. Copy the jar from `build/libs/` into your Paper server's `plugins/` folder.
3. Start the server once so `plugins/HuntCore/config.yml` is created.
4. Adjust `config.yml` values if you want a custom lobby spawn, different countdowns, or a different match world.
5. Restart the server.

## Open In VS Code

1. Open the `HuntCore` folder in VS Code.
2. Install the Java Extension Pack.
3. Install the Gradle for Java extension if you want task buttons inside the editor.
4. Make sure VS Code points at a Java 21+ JDK.
5. Import the Gradle project when prompted.

## Commands

- `/runner` selects the runner role
- `/hunter` selects the hunter role
- `/ready` marks the player ready
- `/unready` removes ready status

## Config Notes

The default config includes:

- Lobby world and spawn settings
- Match start countdown length
- Runner head start length
- Random spawn radius and attempt count
- Structure hint search radius
- Hunter compass update interval
- Return-to-lobby delay after the match ends

## v1 Notes

- v1 supports one runner and one or more hunters
- Hunter compasses only track in the same dimension
- The structure hint is a rough direction toward a nearby village, ruined portal, or pillager outpost
