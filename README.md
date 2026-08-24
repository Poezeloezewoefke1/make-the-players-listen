# Make The Players Listen

A **server-side only** Fabric mod for **Minecraft 1.21.11** that gives operators two moderation
tools:

* **Freeze** a player so they cannot move *at all* - no walking, sprinting, jumping, swimming,
  flying, elytra, riding, ender pearls, and not even turning their head.
* **Mute** a player so nothing they type reaches anybody, permanently or for a set time.

Nothing has to be installed on the client. Vanilla clients are affected exactly like modded
ones, because everything is enforced on the server.

Only **Fabric Loader** is required - the mod does **not** need Fabric API, so the single jar you
build is the only file you drop into `mods/`.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.11 (exactly - the mod refuses to load on other versions) |
| Loader | Fabric Loader 0.16.0 or newer |
| Java | 21 or newer |
| Fabric API | not needed |
| Client mods | none |

## Building

```bash
./gradlew build
```

The finished mod is `build/libs/make-the-players-listen-1.0.0.jar` (ignore the `-sources` jar).
The first build downloads Minecraft, the mappings and Fabric Loader, so it needs internet
access.

## Installing

1. Put the jar in your server's `mods/` folder.
2. Restart the server.
3. On startup the log shows
   `Ready - operators can use /freeze, /unfreeze, /mute and /unmute`.

## Commands

All commands require operator rights (the console, RCON and command blocks may use them too).

| Command | What it does |
|---|---|
| `/freeze <targets>` | Freezes one or more online players. Accepts selectors: `/freeze @a` |
| `/unfreeze <name>` | Unfreezes a player - works while they are offline, tab-completes frozen names |
| `/freezelist` | Lists everyone who is frozen |
| `/unfreezeall` | Unfreezes everybody |
| `/mute <targets>` | Mutes one or more online players permanently |
| `/mute <targets> <duration>` | Mutes them for a while, e.g. `/mute Steve 2h` |
| `/mute <targets> <duration> <reason...>` | Same, with a reason the player is shown |
| `/unmute <name>` | Unmutes a player - works while they are offline, tab-completes muted names |
| `/mutelist` | Lists everyone who is muted, with the time left |
| `/unmuteall` | Unmutes everybody |

### Durations

`30s`, `10m`, `2h`, `7d`, `1w`, combinations such as `1h30m`, a bare number (`30` = 30 minutes),
or `perm` / `permanent` / `forever` for a mute that never expires. Leaving the duration out mutes
permanently.

```
/mute Steve 2h spamming in chat
/mute @a 30m server event
/freeze Steve
/unfreeze Steve
```

Freezes and mutes survive relogs and server restarts. They are stored in
`config/freezemute/moderation.json`, which is rewritten every time something changes.

## What "frozen" means exactly

Player movement in Minecraft is client driven: the client moves and *tells* the server where it
went. For a frozen player every one of those packets is thrown away, so the server never accepts
a new position, and the client is teleported back to the position the server still holds. In
practice:

* Walking, sprinting, sneaking, jumping, swimming, climbing, creative flight, elytra - all dead.
* Boats, horses and minecarts: the player is dismounted and cannot re-mount.
* **Looking around**: the view is pushed back to the angle the server has, so the player cannot
  turn. Because the client draws its own view before the correction arrives, they will see a
  short wiggle while they fight it - that is unavoidable without a client-side mod.
* Ender pearls and chorus fruit teleport a player on the server side, so **using items is
  blocked while frozen** (this is what stops a frozen player from pearling away).
* A frozen player can still be moved by an operator: `/tp` works, and the freeze simply continues
  at the new spot.
* Frozen players can still be hurt and can die. If you want them safe, put them in a protected
  spot first.

## What "muted" means exactly

The mute is applied where chat is handed to players, not where it arrives from one. That detail
matters: since Minecraft 1.19 every chat message is part of a cryptographic chain, and a server
that silently drops such a packet makes the next message fail validation, which kicks the player
with *"Chat validation failed"*. This mod lets the server verify the message as usual and then
refuses to deliver it, so:

* Nobody receives the message - not other players, and not the sender.
* It covers plain chat as well as `/msg`, `/tell`, `/w`, `/me`, `/say` and `/teammsg`.
* The muted player gets a short reminder of why their message disappeared, including the time
  left on the mute.
* Attempted messages are still written to the server log, which is handy when you want to know
  what somebody was trying to say.
* Timed mutes expire on their own; the player is not notified, their next message simply goes
  through.

## Notes and limits

* You can only *apply* a freeze or a mute to a player who is online (the server needs their UUID).
  *Removing* one works offline, by name.
* The mod is pinned to Minecraft 1.21.11. To try it on another 1.21.x build, change
  `minecraft_version` / `yarn_mappings` in `gradle.properties` and the `minecraft` entry in
  `src/main/resources/fabric.mod.json`, then rebuild.
* `yarn_mappings` uses `1.21.11+build.+`, which always picks the newest mappings build for
  1.21.11. Pin it to a concrete build (for example `1.21.11+build.4`) if you want reproducible
  builds.

## License

MIT - see [LICENSE](LICENSE).
