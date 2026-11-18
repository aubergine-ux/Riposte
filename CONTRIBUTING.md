# Contributing to Riposte

Thanks for helping out! Bug reports, ideas and pull requests are all welcome.

## Reporting bugs and requesting features

Use the [issue templates](https://github.com/aubergine-ux/Riposte/issues/new/choose). For bugs, include your Riposte
and Paper versions, the storage type and any console errors. Setup questions are better asked in
[Discussions](https://github.com/aubergine-ux/Riposte/discussions).

Riposte is a fair-play plugin. Requests for anything that gives one player an advantage over another
(reach, autoclickers, hit-delay changes for a single player and similar) will be closed.

## Building

You need JDK 25 or newer.

```bash
./gradlew build
```

The plugin jar ends up in `build/libs/`. Unit tests run as part of the build; run them alone with `./gradlew test`.

## Testing on a server

1. Download [Paper](https://papermc.io/downloads/paper) for the Minecraft version in `build.gradle.kts`.
2. Drop `build/libs/Riposte-*.jar` into the server's `plugins/` folder and start it.
3. Join, run `/riposte setup` and queue up. Two accounts (or a friend) make testing duels much easier.

`tools/bots/` contains a small [mineflayer](https://github.com/PrismarineJS/mineflayer) harness that connects test
clients and drives them from a text file. It is handy for multi-player scenarios like parties, events and load tests:

```bash
cd tools/bots && npm install
MC_PORT=25565 node harness.js ctl.txt bots.log Alice Bob
echo "Alice chat /duel Bob sword" >> ctl.txt
```

## Pull requests

- Keep pull requests focused on one change.
- Use short [conventional commit](https://www.conventionalcommits.org/) messages: `feat:`, `fix:`, `docs:`, `perf:`, `test:`, `refactor:`.
- Player-facing text belongs in `messages.yml` or `menus.yml`, never hard-coded.
- Keep the main thread fast: database work goes through `Database#async`, and large block operations
  go through the arena job queue.
- Add unit tests for pure logic (ratings, matchmaking, brackets, block tracking).
- Make sure `./gradlew build` passes before opening the pull request.

## Code style

Four-space indentation, no wildcard imports, and comments only where the reason for something is not obvious from the code.
