# HuntCore

HuntCore is a Paper plugin for a polished manhunt server loop: one runner, one or more hunters, fresh temporary match worlds, portal-aware tracking, persistent pause/resume (saves even when server closed), a configurable parkour waiting lobby, and a separate PvP arena side mode.

The project is now in a stable gameplay state with prescouted match caching, in-game status tools, recent match history, and clean lobby-to-match flow.

## Requirements

- Java 21+
- Paper for Minecraft 1.21.x

Paper setup guide:
https://docs.papermc.io/paper/dev/project-setup

## Highlights

- One runner versus one or more hunters
- Infinite hunter respawns
- Fresh temporary overworld, Nether, and End per round
- Runner wins by killing the Ender Dragon in the fresh match End
- Cross-dimension hunter compass tracking with portal memory
- Spectator mode that stays out of ready checks and win conditions
- Persistent `/pause` and `/unpause`, including clean server restart resume
- Prescouted match-world cache with strong nearby POI selection
- Importable waiting-lobby maps and PvP arena maps from `.zip` worlds
- In-game `/huntstatus` and `/matchstats`

## Project Layout

- `src/main/java/com/huntcore` contains the plugin code
- `src/main/resources/plugin.yml` registers commands and permissions
- `src/main/resources/config.yml` contains default lobby, PvP, countdown, tracking, and match settings

## Build

From the project root:

```powershell
.\gradlew.bat build
```

Output jar:

```text
build/libs/HuntCore-2.0.0-SNAPSHOT.jar
```

If you already have Gradle installed, `gradle build` works too.

## Local Server Setup

1. Build the jar.
2. Copy `build/libs/HuntCore-2.0.0-SNAPSHOT.jar` into your Paper server `plugins/` folder.
3. Start the server once so `plugins/HuntCore/config.yml` is generated.
4. Adjust config values if needed.
5. Restart the server.

## Lobby And PvP World Import

Waiting lobby:

1. Run `/installlobbymap <zip-path> [world-name]`
2. Run `/setlobby` at the exact lobby spawn you want

PvP arena:

1. Run `/installpvpmap <zip-path> [world-name]`
2. Enter with `/pvp`
3. Run `/setpvpspawn` at the exact arena spawn you want

Imported lobby and PvP worlds stay persistent. Match worlds do not.

## Commands

Public commands:

- `/runner` select the runner role
- `/hunter` select the hunter role
- `/spectate` toggle spectator mode
- `/ready` mark yourself ready
- `/unready` remove your ready status
- `/reset` return to the waiting lobby spawn
- `/pvp` enter the PvP arena
- `/pvpleave` leave the PvP arena and restore your previous state
- `/huntstatus` show current lobby, cache, and match status
- `/matchstats [1-10]` show recent recorded match results

Admin commands:

- `/hunterkeepinventory <on|off|toggle|status>` toggle hunter keep-inventory behavior
- `/setlobby` save your current location as the waiting lobby spawn
- `/setpvpspawn` save your current location as the PvP arena spawn
- `/pause` pause the current match
- `/unpause` resume a paused match when the runner and at least one hunter are online
- `/installlobbymap [zip-path] [world-name]` import a dedicated waiting-lobby world
- `/installpvpmap [zip-path] [world-name]` import a dedicated PvP arena world

## Match Flow

- Only queued runners and hunters count toward match start checks
- Spectators and unassigned players do not block the countdown
- Matches begin from a prescouted cached world when possible
- The runner receives a nearby POI scout note with direction and yaw guidance
- Hunters are released after the configured head start
- If the runner dies, hunters win immediately
- If the runner kills the Ender Dragon, the runner wins immediately

## Pause And Reconnect

- `/pause` suspends the round and disables disconnect-loss timers
- `/unpause` only resumes if the runner and at least one hunter are online
- Paused matches survive a clean Paper restart
- Runner and hunter disconnect grace still applies during normal live rounds

## Lobby And PvP Notes

- The lobby stays persistent and can be any imported world, including parkour maps
- The PvP arena is a separate persistent world with a fixed combat loadout and respawn loop
- Leaving PvP restores your saved inventory, XP, location, role, and other player state
- Hunger behavior in lobby and PvP is aligned with the normal survival match setup as closely as possible from plugin-side state

## Status And History

- `/huntstatus` shows:
  - current game state
  - prescouted cache size
  - scouting status
  - active runner/hunter summary
  - best cached POI
  - latest recorded match result

- `/matchstats` shows recent matches with:
  - winner
  - duration
  - runner
  - hunter count
  - POI
  - end reason
  - per-player kill counts

Match history is stored in:

```text
plugins/HuntCore/match-history.yml
```

Prepared match cache is stored in:

```text
plugins/HuntCore/prepared-matches.yml
```

Paused match persistence is stored in:

```text
plugins/HuntCore/paused-match.yml
```

## Config Notes

The default config includes:

- lobby world/spawn settings
- PvP world/spawn settings
- zip import defaults for lobby and PvP maps
- match world prefix
- match start countdown length
- hunter head start length
- spawn radius and spawn-attempt count
- disconnect grace length
- hunter keep-inventory toggle
- compass update rate
- portal memory time
- return-to-lobby delay

## Current Limitations

- The current match flow supports exactly one runner
- Match stats are file-backed YAML, not database-backed yet
- The plugin is optimized for local/small-server play rather than multi-server network sync
