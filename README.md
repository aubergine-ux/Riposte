<div align="center">

<img src="docs/assets/logo.png" alt="Riposte logo" width="128" height="128">

# Riposte

**PvP practice for Paper servers.** Duels, kits, ranked queues, auto-resetting arenas and stats, ready in one command.

[![Build](https://img.shields.io/github/actions/workflow/status/aubergine-ux/Riposte/build.yml?branch=main&style=flat-square&label=build&logo=github)](https://github.com/aubergine-ux/Riposte/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/aubergine-ux/Riposte?style=flat-square&color=ff4f64&label=release)](https://github.com/aubergine-ux/Riposte/releases/latest)
[![Paper](https://img.shields.io/badge/Paper-26.3-ffb547?style=flat-square)](https://papermc.io/downloads/paper)
[![Java](https://img.shields.io/badge/Java-25-ff7a52?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net)
[![License](https://img.shields.io/github/license/aubergine-ux/Riposte?style=flat-square&color=4ade80)](LICENSE)

[Download](https://github.com/aubergine-ux/Riposte/releases/latest) · [Quick setup](#quick-setup) · [Commands](#commands-and-permissions) · [Configuration](#configuration)

</div>

---

Riposte turns a fresh Paper server into a full practice server. Players queue for 1v1s in ten kits, fight in arenas
that reset themselves, climb an ELO ladder and track their stats. Everything runs inside the plugin: no database to
install, no hologram or scoreboard plugins, no API keys.

## Features

- ⚔️ **10 built-in kits**: NoDebuff, Sword, Axe, Crystal, UHC, BuildUHC, Sumo, Boxing, Bridge and Mace, each with its own rules. Admins edit loadouts in game.
- 🎒 **Kit editor**: Players rearrange their hotbar per kit. Layouts are saved and survive later changes to the kit.
- 🕐 **Queues**: Unranked and ranked queues per kit from a clean menu, with live queue and fight counts and your wait time.
- 🤺 **Duels**: `/duel <player>` with kit and arena pickers, clickable accept and deny buttons, and a rematch button.
- 🏟️ **Self-resetting arenas**: Build with a wand or generate one. Copies are pasted into a void world so many matches run at once, and every placed, broken, burnt or exploded block is restored afterwards.
- 🏆 **Ranked**: ELO per kit with provisional K, named and colored divisions, seasons with soft or hard resets, and leaderboards in a menu, in chat and as holograms.
- 📊 **Match experience**: Countdown and titles, a sidebar with opponent health and ping, combo counter, and an end-of-match summary with clickable inventory previews.
- 👁️ **Spectating**: `/spectate` any live match, flying and invisible, or pick one from a menu.
- 🎉 **Parties and events**: Party split, FFA and party vs party fights, plus hosted tournaments and sumo events with automatic brackets.
- 📈 **Stats**: Wins, losses, K/D, streaks and an ELO history sparkline per kit in `/stats`.
- ⚙️ **Player settings**: Toggle duel requests, the scoreboard, spectators and your time of day.
- 🎨 **Fully configurable**: Every message, menu, hotbar item, color and sidebar line is MiniMessage in YAML.
- 💾 **Zero-setup storage**: SQLite out of the box, MySQL or MariaDB for networks. All queries run off the main thread.

Riposte is a fair-play plugin: there is nothing in it that gives one player an edge over another.

## Quick setup

1. **Install.** Drop `Riposte-x.y.z.jar` from the [latest release](https://github.com/aubergine-ux/Riposte/releases/latest) into `plugins/` on a Paper 26.3 server (Java 25) and start it.
2. **Set up.** Join, stand where the lobby should be and run `/riposte setup`. You get a lobby, three generated arenas (Classic, Sumo Ring and The Bridge) and their copies.
3. **Play.** Right click the swords in your hotbar to queue, or `/duel` a friend.

Want your own arenas? Build one anywhere, then:

```text
/arena wand                     left and right click two corners
/arena create <name>            uses your selection
/arena setspawn <name> 1        stand where team one starts, then the same with 2
/arena copies <name> 4          how many matches can run in it at once
/arena enable <name>
```

Leaderboard hologram: `/riposte hologram create top global` (or a kit name instead of `global`).

## Commands and permissions

| Command | What it does | Permission |
|---|---|---|
| `/queue [unranked\|ranked] [kit]` | Open the queue menu or join a queue (`/unranked`, `/ranked`) | everyone |
| `/duel <player> [kit] [arena]` | Challenge a player; party leaders challenge parties | everyone |
| `/duel accept\|deny <player>` | Answer a duel request | everyone |
| `/rematch` | Rematch your last opponent | everyone |
| `/leave` | Leave a queue, spectating or an event | everyone |
| `/spectate [player]` | Watch a live match, or open the match list | `riposte.spectate` |
| `/party create\|invite\|accept\|leave\|kick\|disband\|info\|chat\|ffa\|split` | Parties and party fights | `riposte.party` |
| `/event join\|leave` | Join or leave the hosted event | everyone |
| `/event host <tournament\|sumo> [kit]`, `/event start\|cancel` | Host an event | `riposte.event.host` |
| `/stats [player]` | Stats and ELO history | everyone |
| `/leaderboard [kit\|global]` | Leaderboard menu, or a board in chat (`/lb`) | everyone |
| `/kiteditor [kit]` | Rearrange a kit's hotbar | everyone |
| `/settings` | Duel requests, scoreboard, spectators, time of day | everyone |
| `/riposte setup\|setlobby\|reload\|build\|status` | Server setup and tools | `riposte.admin` |
| `/riposte season [new <soft\|hard> <name>]` | Show or start a ranked season | `riposte.admin` |
| `/riposte hologram create\|delete\|list` | Leaderboard holograms | `riposte.admin` |
| `/arena …` | Create, copy, tag, enable and generate arenas | `riposte.admin` |
| `/kit list\|give\|setinv\|seticon\|create\|delete\|enable\|disable\|restore` | Edit kits in game | `riposte.admin` |

Extra permissions: `riposte.spectate.bypass` (spectate players who turned spectators off), `riposte.duel.bypass`
(duel players who turned requests off), `riposte.ranked.bypass` (skip the unranked wins requirement) and
`riposte.lobby.fly`. `riposte.admin` includes all of them and is given to operators by default.

## Configuration

All files live in `plugins/Riposte/`. Run `/riposte reload` after editing.

| File | Contents |
|---|---|
| `config.yml` | Storage, match timings, ranked range, ELO, duels, parties, events, arenas, lobby |
| `messages.yml` | Every chat line, title and the color palette |
| `menus.yml` | Menu titles, items and the hotbar for each state |
| `scoreboard.yml` | Sidebar layouts for the lobby, queue, matches, spectating and events |
| `kits.yml` | Kit rules (items are edited in game) |
| `divisions.yml` | Ranked division names, colors and ELO bands |
| `arenas.yml` | Arena definitions (edited through `/arena`) |

<details>
<summary><b>Use MySQL or MariaDB</b></summary>

```yaml
storage:
  type: mysql
  table-prefix: riposte_
  mysql:
    host: db.example.net
    port: 3306
    database: riposte
    username: riposte
    password: change-me
    pool-size: 6
```

Every server on the network can point at the same database to share ratings, stats and layouts.

</details>

<details>
<summary><b>Change the color palette</b></summary>

`messages.yml` defines six colors that become tags usable everywhere, including menus and the sidebar:

```yaml
colors:
  primary: '#ff4f64'
  accent: '#ffb547'
  text: '#e6e8ec'
  muted: '#8b93a1'
  success: '#4ade80'
  error: '#f87171'
```

```yaml
queue:
  joined: '<prefix><text>Queued for <primary>unranked</primary> <kit><text>.'
```

</details>

<details>
<summary><b>Tune a kit's rules</b></summary>

```yaml
kits:
  nodebuff:
    display-name: '<#ff5d73>NoDebuff'
    icon: SPLASH_POTION
    rules:
      win-condition: ELIMINATION   # ELIMINATION, SUMO, BOXING or BRIDGE
      rounds-to-win: 1
      build: false
      hunger: true
      natural-regen: true
      fall-damage: true
      ranked: true
      pearl-cooldown-seconds: 15
      hit-delay-ticks: 20
      arena-tag: standard           # only arenas with this tag are used
```

Change the items by loading the kit with `/kit give nodebuff`, editing your inventory and running `/kit setinv nodebuff`.

</details>

<details>
<summary><b>Edit the sidebar</b></summary>

```yaml
boards:
  match:
    - '<text>Opponent <error><opponent_name>'
    - '<text> Health <error><opponent_health>❤'
    - '<text> Ping <muted><opponent_ping>ms'
    - ''
    - '<text>Hits <success><hits> <muted>vs <error><opponent_hits>'
    - '<text>Combo <accent><combo>'
```

`scoreboard.yml` lists every placeholder at the top of the file.

</details>

<details>
<summary><b>Ranked queue and ELO</b></summary>

```yaml
queue:
  ranked-range:        # the ELO gap a player accepts grows while they wait
    start: 50
    growth: 25
    interval-seconds: 5
    max: 500

ranked:
  starting-elo: 1000
  k-factor: 32
  provisional-k-factor: 48   # used for a player's first games in a kit
  provisional-games: 10
  elo-floor: 100
  season-soft-reset-factor: 0.5
```

Two players are only matched when the gap fits inside both of their current ranges.

</details>

<details>
<summary><b>Divisions</b></summary>

```yaml
divisions:
  gold:
    name: Gold
    min-elo: 1100
    color: '<#ffd166>'
    icon: GOLD_INGOT
    sub-divisions: 3     # Gold III, Gold II, Gold I
```

</details>

## Performance

Riposte is built to keep tick times low with many matches running:

- Database work runs on its own thread; nothing waits on SQL during gameplay.
- Arena resets only restore the blocks that changed, and block work is time-boxed per tick.
- Arena chunks stay loaded, so starting a match never loads chunks on the main thread.
- Match starts from the queue are spread across ticks, and sidebar lines are cached.

On a local test with 38 bot players in 19 simultaneous NoDebuff matches, the average tick stayed between 4 and 9 ms.

## Building from source

```bash
git clone https://github.com/aubergine-ux/Riposte.git
cd Riposte
./gradlew build
```

The jar is written to `build/libs/`. You need JDK 25 or newer. See [CONTRIBUTING.md](CONTRIBUTING.md) for the test
harness and pull request guidelines.

## License

[MIT](LICENSE)
