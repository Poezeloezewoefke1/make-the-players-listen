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

1. Put the jar in your server's `mods/` folder. Use `make-the-players-listen-1.0.0.jar`; a
   file ending in `-sources.jar` is source code for an IDE, not a mod, and Fabric will fail to
   start if you put one in `mods/`.
2. Restart the server.
3. On startup the log shows
   `Ready - operators can use /freeze, /unfreeze, /mute and /unmute`.

## Commands

All commands require operator rights (the console, RCON and command blocks may use them too).

| Command | What it does |
|---|---|
| `/freeze <targets>` | Freezes one or more online players until you lift it. Accepts selectors: `/freeze @a` |
| `/freeze <targets> <duration>` | Freezes them for a while, e.g. `/freeze Steve 10m` |
| `/freeze <targets> <duration> <reason...>` | Same, with a reason the player is shown |
| `/unfreeze <name>` | Unfreezes a player - works while they are offline, tab-completes frozen names |
| `/freezelist` | Lists everyone who is frozen |
| `/unfreezeall` | Unfreezes everybody |
| `/mute <targets>` | Mutes one or more online players permanently |
| `/mute <targets> <duration>` | Mutes them for a while, e.g. `/mute Steve 2h` |
| `/mute <targets> <duration> <reason...>` | Same, with a reason the player is shown |
| `/unmute <name>` | Unmutes a player - works while they are offline, tab-completes muted names |
| `/mutelist` | Lists everyone who is muted, with the time left |
| `/unmuteall` | Unmutes everybody |
| `/kitgive <tier> [targets]` | Hands out a used-looking kit - see below. Without targets it gives it to you |

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

Freezes and mutes survive relogs and server restarts, and timed ones expire on their own - the
player is told when that happens. They are stored in `config/freezemute/moderation.json`, which is
rewritten every time something changes.

## Permissions

Operators, the console, RCON and command blocks can always use these commands. If
[fabric-permissions-api](https://github.com/lucko/fabric-permissions-api) is installed - LuckPerms,
`player_roles` and similar expose their permissions through it - these nodes are honoured too, so a
moderator rank can use the commands without being a full operator:

`freezemute.freeze`, `freezemute.unfreeze`, `freezemute.mute`, `freezemute.unmute`,
`freezemute.list`, `freezemute.kitgive`, and `freezemute.staff` for the notifications below.

The mod does not depend on that API: without it, it just checks operator status. The log line at
startup says which mode is in use.

## Settings

`config/freezemute/config.json` is written with the defaults on first run:

| Setting | Default | What it does |
|---|---|---|
| `freezeBlocksInteractions` | `true` | Frozen players cannot break or place blocks, hit anything, drop items or move things around their inventory |
| `freezeProtectsFromDamage` | `true` | Frozen players cannot be hurt, so a mob or a fall cannot kill somebody you are holding for questioning |
| `muteBlocksSignsAndBooks` | `true` | Muted players cannot write signs or books either, which is the usual way around a mute |
| `notifyStaff` | `true` | Staff are told when a muted player tries to talk (including what they tried to say) or a frozen player tries to run |
| `staffNotifyCooldownSeconds` | `10` | How long before the same player is reported again |

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
* With the default settings a frozen player also cannot break or place blocks, attack, drop items
  or rearrange their inventory, and cannot be hurt while frozen. Both are switches in the config.
* A frozen player can still be moved by an operator: `/tp` works, and the freeze simply continues
  at the new spot.
* Whatever invulnerability the player had before the freeze is restored when it is lifted, so
  turning `freezeProtectsFromDamage` on does not leave survival players immortal afterwards.

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
* Attempted messages are still written to the server log, and staff online at the time get a
  short notice with what was said, so you do not have to go digging.
* Timed mutes expire on their own; the player is not notified, their next message simply goes
  through.

## Kits

`/kitgive <tier> [targets]` gives out a loadout that looks like it came off somebody who has been
playing on the server for a while, rather than a shiny box-fresh kit: the gear is worn, some
pieces are missing on the poorer tiers, and the better tiers carry a few random enchantments.

| Tier | Gear | Enchantments | Condition |
|---|---|---|---|
| `poor` | leather armour, stone tools, bread, cobble, torches, junk | none | 45-90% worn |
| `copper` | chainmail, stone/iron mix, copper ingots and blocks | none | 35-80% worn |
| `iron` | full iron, bow and arrows, bucket, shield | weak - level 1-2, e.g. Sharpness I, Efficiency II | 25-65% worn |
| `diamond` | full diamond, golden apples, obsidian, pearls | mixed - up to Sharpness III, Efficiency IV, Protection III | 15-50% worn |
| `netherite` | full netherite, notch apple, totem, pearls, crossbow | good - Sharpness IV-V, Protection IV, Mending, Fortune III | 5-35% worn |

Every count is rolled per kit, so two `/kitgive iron` never come out the same. Which enchantments
land on which piece is random too, taken from a pool that suits the item: swords get Sharpness,
Looting and Fire Aspect, pickaxes get Efficiency and Fortune, boots get Feather Falling and Depth
Strider.

Minecraft 1.21.11 has no copper tools or armour, so the `copper` tier is built from chainmail and
a stone/iron mix with copper ingots and blocks as the loot, which is the closest thing to a
copper-age loadout the version has.

Items are looked up by id (`minecraft:diamond_sword`), so an id that does not exist in your
version is skipped instead of breaking the command.

## Notes and limits

* You can only *apply* a freeze or a mute to a player who is online (the server needs their UUID).
  *Removing* one works offline, by name.
* The mod is pinned to Minecraft 1.21.11. To try it on another 1.21.x build, change
  `minecraft_version` / `yarn_mappings` in `gradle.properties` and the `minecraft` entry in
  `src/main/resources/fabric.mod.json`, then rebuild.
* `yarn_mappings` is pinned to a concrete Yarn build. Loom does not accept a wildcard here.
  Which Yarn build you compile against does not change the resulting mod: Yarn only renames
  things for humans, while the names the mod is remapped onto at runtime (intermediary) are the
  same for every build of a given Minecraft version.
* Every push is verified by GitHub Actions (`.github/workflows/build.yml`): the mod is compiled
  and remapped, the unit tests run, and then a real Fabric server for this Minecraft version is
  installed and started with the mod in it. The workflow fails unless the server reaches "Done",
  the mixins applied without error, and `/freezelist` and `/mutelist` answer from the server
  console. The built jar is attached to each run as an artifact, so you can download it from the
  Actions tab instead of building locally.

## License

MIT - see [LICENSE](LICENSE).
