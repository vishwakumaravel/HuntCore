# HuntCore

HuntCore is a simple, polished Paper minigame plugin for a hunt-style server. The current version focuses on a clean lobby flow, one runner versus one or more hunters, infinite hunter respawns, spectator support, runner head start, fresh temporary match worlds, portal-aware hunter compass tracking across the overworld, Nether, and End, and a clear post-match summary.

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
build/libs/HuntCore-2.0.0-SNAPSHOT.jar
```

If you already have Gradle installed, `gradle build` works too.

## Run On A Local Paper Server

1. Build the plugin jar.
2. Copy the jar from `build/libs/` into your Paper server's `plugins/` folder.
3. Start the server once so `plugins/HuntCore/config.yml` is created.
4. Adjust `config.yml` values if you want a custom lobby spawn, different countdowns, or a different temporary match world prefix.
5. Restart the server.

To use a downloaded parkour world as the waiting lobby:

1. Run `/installlobbymap <zip-path> [world-name]`
2. Let HuntCore import the map as a separate lobby world
3. Use `/setlobby` if you want a more precise spawn point than the imported world spawn

## Open In VS Code

1. Open the `HuntCore` folder in VS Code.
2. Install the Java Extension Pack.
3. Install the Gradle for Java extension if you want task buttons inside the editor.
4. Make sure VS Code points at a Java 21+ JDK.
5. Import the Gradle project when prompted.

## Commands

- `/runner` selects the runner role
- `/hunter` selects the hunter role
- `/spectate` toggles spectator mode and spectators do not count toward ready checks
- `/ready` marks the player ready
- `/unready` removes ready status
- `/hunterkeepinventory <on|off|toggle|status>` changes whether hunters keep items and XP on death
- `/setlobby` saves your current location as the waiting lobby spawn
- `/installlobbymap [zip-path] [world-name]` imports a world zip as a dedicated waiting lobby world

## Config Notes

The default config includes:

- Lobby world and spawn settings
- Optional lobby zip import path and world-name defaults
- The lobby can be a separate sky platform or prebuilt parkour area
- Temporary match world prefix
- Match start countdown length
- Runner head start length
- Random spawn radius and attempt count
- Structure hint search radius
- Disconnect grace time for reconnects
- Hunter compass update interval
- Return-to-lobby delay after the match ends

## Current Version Notes

- Matches use one runner and one or more hunters
- If the runner dies, the match ends immediately and hunters win
- The runner wins by killing the Ender Dragon in a fresh match End
- Hunters respawn infinitely and stay in the same match after death
- Matches create fresh temporary overworld, Nether, and End worlds for each round
- Match participants get a reconnect grace period and are restored into the round if they return in time
- The dragon-kill advancement is reset for match participants at round start so repeated matches can still end correctly
- Spectators are separate from active roles and do not affect ready checks or win conditions
- The lobby stays in Adventure mode
- The waiting lobby can be moved in game with `/setlobby`, which is useful for sky parkour hubs
- Downloaded parkour maps can be imported as a separate lobby world with `/installlobbymap`
- Hunter compasses track directly in the same dimension and fall back to recent runner portal locations across dimensions
- The structure hint is a rough direction toward a nearby village, ruined portal, or pillager outpost

## Current Limitations / TODO

- The current match flow supports exactly one runner
