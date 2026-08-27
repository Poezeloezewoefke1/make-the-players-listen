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
| `/kitgive <tier> [targets]` | Hands out worn armour, tools and a shield - see below. Without targets it gives it to you |
| `/kitgive random [targets]` | Same, but rolls the tier as well, separately for each player |

### Voice chat (Simple Voice Chat)

| Command | What it does |
|---|---|
| `/vcmute <targets> [duration] [reason]` | Mutes them in voice chat - they cannot talk and nobody hears them. No duration means until it is lifted |
| `/vcunmute <name>` | Unmutes, also works while the player is offline |
| `/vcunmute all` | Unmutes everybody |
| `/vcdeafen <targets> [duration] [reason]` | Deafens them - they hear nobody. No duration means until it is lifted |
| `/vcundeafen <name>` / `/vcundeafen all` | Lifts a deafen, or all of them |
| `/vclist` | Everyone muted or deafened, with the time left and the reason |
| `/vcinfo <name>` | The voice chat status of one player |
| `/vcstatus` | Whether Simple Voice Chat is installed and the plugin is enforcing |

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

`/kitgive <tier> [targets]` hands out gear that looks like it came off somebody who has been
playing on the server for a while, rather than a shiny box-fresh kit. Without targets it goes to
you; with them it goes to whoever the selector picks out - `/kitgive rich @a`, `/kitgive chungie
Steve Alex`, `/kitgive random @a`.

**A kit is gear and nothing else**: four pieces of armour, four tools and a shield. No food, no
blocks, no loot, no experience bottles - nine items, all of them wearable or swingable.

| Tier | Armour | Tools | Enchantments | Condition |
|---|---|---|---|---|
| `poor` | mismatched leather, copper and gold | stone, wood and gold | none | 60-95% worn |
| `copper` | copper | copper | none | 35-80% worn |
| `iron` | iron | iron | weak - main enchantment at level 1-2, maybe one side | 25-65% worn |
| `chungie` | iron and diamond, per slot | iron and diamond, per slot | iron parts weak, diamond parts middling | 20-58% worn |
| `diamond` | diamond | diamond | good - Prot III, Sharpness III, Efficiency IV, plus 1-3 sides | 15-50% worn |
| `rich` | diamond and netherite, per slot | diamond and netherite, per slot | diamond parts good, netherite parts maxed | 10-42% worn |
| `netherite` | netherite | netherite | the best - everything the item can carry, at max level | 5-35% worn |
| `random` | a tier rolled per player | | whatever that tier gives | |

`poor` is a grab bag on purpose: every slot rolls on its own out of leather, copper and gold, and
the gear is nearly gone, so it comes out as mismatched junk rather than a matching set.

The four straight tiers are exactly what they say: `netherite` is netherite from head to boots,
`diamond` is diamond, and neither ever slips a lower piece into the set.

`chungie` and `rich` are the half-geared look you actually see on an SMP - `chungie` is iron with
diamond mixed in, `rich` is diamond with netherite mixed in. Each slot rolls its own material, so
one player gets the diamond chestplate and the next gets diamond boots and a diamond sword. Both
are guaranteed at least one piece of each of their two materials, in the armour and in the tools,
so `rich` can never come out as plain diamond or as full netherite and be a different tier wearing
the wrong name.

### Enchantments

**Every enchanted piece gets its main enchantment**, and side enchantments go on top of it:

| Item | Main enchantment | Sides it can pick up |
|---|---|---|
| Helmet | Protection | Aqua Affinity, Respiration, Unbreaking, Mending |
| Chestplate | Protection | Unbreaking, Mending |
| Leggings | Protection | Swift Sneak, Unbreaking, Mending |
| Boots | Protection | Feather Falling, Depth Strider, Soul Speed, Frost Walker, Unbreaking, Mending |
| Sword | Sharpness | Looting, Fire Aspect, Knockback, Unbreaking, Mending |
| Axe | Sharpness | Efficiency, Fortune, Silk Touch, Unbreaking, Mending |
| Pickaxe, shovel | Efficiency | Fortune, Silk Touch, Unbreaking, Mending |
| Shield | Unbreaking | Mending |

So an enchanted chestplate always actually protects and an enchanted sword always actually hits
harder - the sides are extras, never a substitute. A diamond helmet reads as Protection III and
Aqua Affinity, not as whichever single enchantment a shuffle happened to land on.

**No Thorns**, anywhere, at any tier. Armour only ever rolls plain Protection and weapons only
ever roll Sharpness - Blast, Fire and Projectile Protection, Smite and Bane of Arthropods are all
left out, so a piece is never quietly worse than it looks.

How good the rolls are follows the piece. The tier sets a ceiling and the material decides the
rest, so on `chungie` the iron helmet rolls weak while the diamond chestplate rolls middling.
Leather, copper, stone, wood and gold are never enchanted whatever the tier says, and no piece can
go above its tier's ceiling - a diamond sword in an `iron` kit still only rolls weak.

| Power | Main enchantment | Sides | Levels |
|---|---|---|---|
| weak (iron) | level 1-2 | 0-1 | rolled |
| middling (diamond in a `chungie` kit) | level 2-3 | 1-2 | rolled |
| good (diamond) | at the cap - Prot III, Sharpness III, Efficiency IV | 1-3 | rolled, best of two |
| best (netherite) | at the cap - Prot IV, Sharpness V, Efficiency V | all of them | maximum |

**Netherite does not roll.** It gets its main enchantment and every side its item can carry, all
at maximum level, because it is the top of the ladder - so a netherite helmet is Protection IV,
Aqua Affinity, Respiration III, Unbreaking III and Mending, every time.

Enchantments that cannot sit together never do: a pickaxe gets Fortune or Silk Touch but never
both, and boots get Depth Strider or Frost Walker. At the top tier the preferred one wins rather
than a coin flip, so netherite boots get Depth Strider and a netherite pickaxe gets Fortune.

Items and enchantments are looked up by id (`minecraft:diamond_sword`, `minecraft:mending`), so
an id that does not exist in your version is skipped instead of breaking the command, and data
pack enchantments work too. The mod checks every id it could hand out when it registers the
commands and logs anything this version does not have, so a missing item shows up in the log
rather than as a kit quietly arriving a piece short.

## Voice chat

The `/vc` commands moderate [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat).
They are operators only, like the rest of the mod.

- **`/vcmute`** drops the player's microphone packets at the server, so nothing they say ever
  reaches anybody - it is not a client-side setting they can turn back off, and it is not a
  per-listener mute. Audio already on its way from them is dropped too.
- **`/vcdeafen`** drops every audio packet on its way to that player, so they hear nobody.

Both take the same durations as `/mute` (`30m`, `2h`, `7d`, `1h30m`, or nothing at all for
"until an operator lifts it"), both survive relogs and restarts, both expire on their own and
tell the player when they do, and both follow a name change.

Simple Voice Chat is a **soft dependency**. The mod does not require it, does not ship any of it,
and works exactly as before without it - the `/vc` commands still record punishments, which start
being enforced the moment the voice chat mod is added. `/vcstatus` says which situation you are
in. Only one class in this mod touches the voice chat API, and Simple Voice Chat is what loads
it, so a server without it never loads that class at all.

## Updating itself

The mod checks GitHub for a newer release every time the server starts. If there is one, it
downloads the jar, checks it really is a build of this mod, and puts it in the mods folder in
place of the running one. Fabric decides what to load before any mod code runs, so **the new
version starts on the next restart** - a server that reboots on a schedule keeps itself current
with nothing to do by hand.

The check runs on its own thread, so a slow or unreachable GitHub never delays startup, and any
failure is logged and otherwise ignored.

The one thing that must never happen is two jars with the same mod id in the folder, because
Fabric refuses to start at all in that state. So the old jar is only removed once the new one is
downloaded and verified, and if it cannot be removed the download is thrown away and the server
is left exactly as it was.

**This does mean the server runs whatever that repository publishes.** Anybody who can publish a
release there can put code on your server. It is your repository, which is the point, but it is
worth knowing. Three settings control it:

| Setting | Default | What it does |
|---|---|---|
| `autoUpdate` | `true` | Turn the whole thing off with `false` |
| `updateCheckOnly` | `false` | `true` logs that an update exists and downloads nothing |
| `updateRepository` | `poezeloezewoefke1/make-the-players-listen` | The only repository ever contacted |

Nothing outside that repository is fetched, only `https` is followed, the download is capped at
32 MB, and a file that is not a `freezemute` jar is deleted rather than installed.

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
