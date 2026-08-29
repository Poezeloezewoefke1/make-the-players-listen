# Make The Players Listen

A **server-side only** Fabric mod for **Minecraft 1.21.11**:

* **Freeze** a player so they cannot move *at all* - no walking, sprinting, jumping, swimming,
  flying, elytra, riding, ender pearls, and not even turning their head.
* **Mute** a player so nothing they type reaches anybody, permanently or for a set time.
* **Mute or deafen** a player in Simple Voice Chat.
* **Hand out worn kits** of armour, tools and a shield in eight tiers.
* **Hold a launch** in a lobby dimension with a first-come-first-served queue, a player cap,
  crash grace windows and parkour courses with leaderboards.

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

### Lobby and queue

| Command | What it does |
|---|---|
| `/lobby` | Sends you to the lobby |
| `/lobby leave` | Puts you back where you were standing when you went in |
| `/lobby <targets>` | Sends those players to the lobby and puts them in the queue |
| `/lobby all` | The panic button: everybody who is not staff comes back, and the queue closes |
| `/lobby enable` / `/lobby disable` | Turns the routing on and off. Off by default |
| `/lobby setspawn` | Sets the lobby spawn to where you stand |
| `/lobby status` | Whether the dimension exists, whether routing is on, the spawn, how many are waiting |
| `/queue` / `/queue status` | Line length, slots used, cap |
| `/queue list` | Who is in and who is waiting, in order, with the grace windows still running |
| `/queue cap [slots]` | Shows or sets how many players may be in the world. `0` means no cap |
| `/queue open` / `/queue close` | Whether anybody is let through |
| `/queue end` | Session over: everybody to the lobby, the line cleared, the queue closed |
| `/queue bypass <targets>` | Lets those players straight in, ignoring the cap and the order |
| `/queue early add <targets>` / `/queue early remove <name>` / `/queue early list` | The list of people who never see the queue |

### Parkour

| Command | What it does |
|---|---|
| `/lobby course create <name>` | Starts a course with the start pad where you stand |
| `/lobby course checkpoint <name>` | Adds a checkpoint where you stand, at the end of the course |
| `/lobby course undo <name>` | Removes the last checkpoint |
| `/lobby course start <name>` / `/lobby course finish <name>` | Moves the start or sets the finish to where you stand |
| `/lobby course tp <name>` | Teleports you to a course start |
| `/lobby course top <name>` | The ten fastest times, and your own best |
| `/lobby course list` / `/lobby course delete <name>` | Lists courses, or deletes one and its times |

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
`freezemute.list`, `freezemute.kitgive`, `freezemute.vcmute`, `freezemute.vcunmute`,
`freezemute.vcdeafen`, `freezemute.vcundeafen`, `freezemute.vclist`, `freezemute.queue`,
`freezemute.lobby`, `freezemute.lobby.course`, and `freezemute.staff` for the notifications below.

One node is held by a player rather than checked on a command: **`freezemute.lobby.early`** skips
the queue entirely. It is the node to give the camera and the crew. Unlike the others it does
*not* fall back to operator status, because without a permission mod nobody could be granted it
selectively - use `/queue early add` instead, which does the same thing and needs no extra mod.

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
| `lobbyInstallDimension` | `true` | Write the `astra:lobby` data pack into the world folder on every start |
| `lobbySpawnPlatform` | `true` | Lay a stone platform under the lobby spawn the first time the dimension is empty |
| `lobbyPlatformRadius` | `12` | How wide that platform is, measured from the middle |
| `lobbyIsolateMembers` | `true` | Hide lobby members from each other. Staff always see everybody |
| `lobbyQueueGraceSeconds` | `300` | How long a queued player keeps their place after dropping out |
| `lobbySlotGraceSeconds` | `300` | How long an admitted player keeps their slot after dropping out |
| `lobbyAdmitPerSecond` | `1` | How many players may be let through per second |
| `lobbyVoidCatchY` | `-5` | Anything below this height in the lobby is put back on its last checkpoint |
| `lobbyCheckpointRadius` | `1.5` | How close you have to be to trigger a checkpoint |

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

**A kit is gear and nothing else**: four pieces of armour, five tools (sword, spear, pickaxe,
axe, shovel) and a shield. No food, no
blocks, no loot, no experience bottles - ten items, all of them wearable or swingable.

| Tier | Armour | Tools | Enchantments | Condition |
|---|---|---|---|---|
| `poor` | mismatched leather, copper and gold | stone, wood and gold | none | 60-95% worn |
| `copper` | copper | copper | none | 35-80% worn |
| `iron` | iron | iron | weak - main enchantment at level 1-2, maybe one side | 25-65% worn |
| `chungie` | iron and diamond, per slot | iron and diamond, per slot | iron parts weak, diamond parts middling | 20-58% worn |
| `diamond` | diamond | diamond | good - Prot III, Sharpness III, Efficiency IV, plus 1-3 sides. No Mending | 15-50% worn |
| `rich` | diamond and netherite, per slot | diamond and netherite, per slot | diamond parts good, netherite parts maxed | 10-42% worn |
| `netherite` | netherite | netherite | the best - everything the item can carry, at max level | 5-35% worn |
| `diamondprot4nomendingunbreaking3` | diamond | diamond | Protection IV, Unbreaking III, Sharpness V, Efficiency V - the top rolls, but **no Mending, Swift Sneak or Soul Speed** | 12-45% worn |
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
| Helmet | Protection | Aqua Affinity, Respiration, Unbreaking, Mending\* |
| Chestplate | Protection | Unbreaking, Mending\* |
| Leggings | Protection | Swift Sneak\*, Unbreaking, Mending\* |
| Boots | Protection | Feather Falling, Depth Strider, Soul Speed, Unbreaking, Mending |
| Sword, spear | Sharpness | Looting, Fire Aspect, Unbreaking, Mending\* |
| Axe | Sharpness | Efficiency, Fortune, Silk Touch, Unbreaking, Mending\* |
| Pickaxe, shovel | Efficiency | Fortune, Silk Touch, Unbreaking, Mending\* |
| Shield | Unbreaking | Mending\* |

So an enchanted chestplate always actually protects and an enchanted sword always actually hits
harder - the sides are extras, never a substitute. A diamond helmet reads as Protection III and
Aqua Affinity, not as whichever single enchantment a shuffle happened to land on.

\* **Mending, Swift Sneak and Soul Speed are netherite only.** They need the top tier, which
only netherite pieces reach - so the `netherite` tier gets them, the netherite half of a `rich`
kit gets them, and nothing else does. A diamond kit never repairs itself, and neither does the
diamond half of a mixed one. (The shield is made of nothing in particular, so it counts as the
weakest piece the kit can contain - which means a `rich` shield is diamond-grade and does not
repair itself either. Only an all-netherite kit has a shield with Mending.)

**No Thorns and no Knockback**, anywhere, at any tier. Armour only ever rolls plain Protection and weapons only
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

`diamondprot4nomendingunbreaking3` is diamond gear carrying what netherite normally gets, with
three left out. Diamond usually caps below netherite however good the tier is; this one ignores
that cap on purpose, which is how diamond armour ends up with Protection IV. **Mending, Swift
Sneak and Soul Speed are never handed out on it** - nothing repairs itself and the leggings and
boots skip the movement extras. It does not roll either: every piece comes out the same, which is
the point of naming a tier after its enchantments.

| Piece | What it gets |
|---|---|
| Helmet | Protection IV, Aqua Affinity, Respiration III, Unbreaking III |
| Chestplate | Protection IV, Unbreaking III |
| Leggings | Protection IV, Unbreaking III |
| Boots | Protection IV, Feather Falling IV, Depth Strider III, Unbreaking III |
| Sword, spear | Sharpness V, Looting III, Fire Aspect II, Unbreaking III |
| Axe | Sharpness V, Efficiency V, Fortune III, Unbreaking III |
| Pickaxe, shovel | Efficiency V, Fortune III, Unbreaking III |
| Shield | Unbreaking III |

The ban is per tier, so `netherite` and `rich` still hand out all three.

**Netherite does not roll.** It gets its main enchantment and every side its item can carry, all
at maximum level, because it is the top of the ladder - so a netherite helmet is Protection IV,
Aqua Affinity, Respiration III, Unbreaking III and Mending, every time.

Enchantments that cannot sit together never do: a pickaxe gets Fortune or Silk Touch but never
both. At the top tier the preferred one wins rather than a coin flip, so a netherite pickaxe
gets Fortune.

No kit ever rolls **Thorns**, **Knockback** or **Frost Walker**, and armour only gets plain
Protection - never the blast, fire or projectile variants. Weapons only get plain Sharpness,
never Smite or Bane of Arthropods.

Items and enchantments are looked up by id (`minecraft:diamond_sword`, `minecraft:mending`), so
an id that does not exist in your version is skipped instead of breaking the command, and data
pack enchantments work too. The mod checks every id it could hand out when it registers the
commands and logs anything this version does not have, so a missing item shows up in the log
rather than as a kit quietly arriving a piece short.

## The lobby and the queue

Off by default. Turn it on with `/lobby enable` and everybody who is not staff and has no early
access lands in `astra:lobby` when they join, in adventure mode, and takes a place in line.

**The dimension.** The mod writes a small data pack into the world folder (`<world>/datapacks/
astra_lobby/`) during initialisation, which happens before the server loads the level - so the
dimension is normally there on the very start that installs the mod. It is a void world with
permanent noon, no mobs and a stone platform under the spawn, which is the right canvas to build a
lobby on. If `/lobby enable` says the dimension is not there, restart once; that is the whole fix.

**What members may do.** Walk, run, jump, and run the parkour. Nothing else: no breaking, placing,
hitting, dropping, inventory, item use, signs, books, chat or voice. That is not a new set of
rules - it is the freeze, with the movement handlers left out. The same mixins that hold a frozen
player hold a lobby member, so anything that works for one works for the other.

**Staff are never members.** They see everybody, keep chat and voice, keep every interaction, are
never routed to the lobby and never take up a slot. Members can still hear staff over voice chat,
which is the point of being able to talk to a room full of people you are holding.

**Isolation.** Members are hidden from each other: the spawn packet that would introduce one member
to another is refused on the way out, so the client is never told they exist, and their sounds go
the same way. They also share a team with collisions and name tags switched off, so nobody gets
shoved off a jump by somebody they cannot see. Staff receive every packet as usual.

**Set a cap first.** The queue exists to hold people back from a cap, so with `/queue cap 0` -
the default - there is nothing to wait for and nobody is held: a player who joins when there is
room and no line simply goes straight in, without a trip through the lobby. Set `/queue cap 8`
before `/lobby enable`, or use `/queue close` to hold everybody while the doors are shut.

**The line.** Strictly first come, first served. Position and total sit on a boss bar, where they
can change every second without pushing anything out of chat. The title and the sound cue fire when
a slot actually opens - not when somebody reaches the front, because being first still means
waiting. `lobbyAdmitPerSecond` keeps a rush from letting thirty people in at once.

**Grace windows.** Two of them, and they mean different things. A queued player who drops out keeps
their **place in line**; an admitted player who drops out keeps their **slot**, so nobody else
takes it. Both last five minutes by default. Because the worst crash is the one that takes the
server with it, the whole queue is written to disk, so a restart does not cost anybody their place.

There is no AFK handling on purpose - staff decide who is idle. A **kick voids the grace**
immediately, for both windows, otherwise kicking somebody who has gone quiet would hold their slot
for another five minutes and achieve nothing. A timeout or a closed window is not a kick and keeps
the grace, which is exactly the distinction the windows exist for.

**Getting people back.** `/lobby all` pulls everybody who is not staff back and closes the queue,
keeping the line in the order people were let in so the session can resume fairly. `/queue end`
does the same and clears the line as well. Being let back in puts a player exactly where they were
standing when the lobby took them, in the game mode they were in - a builder in creative comes back
in creative.

**Parkour.** Build a course by standing where each marker goes. The clock starts the moment you
step off the start pad, checkpoints have to be taken in order, and a fall puts you back on the last
one you reached rather than killing you. Only a player's best time per course is kept, so the board
is a leaderboard and not a log. Courses and times live in `config/freezemute/lobby.json` alongside
the queue.

**One server, two dimensions.** This is a lobby *in* the SMP, not a separate proxy behind it. A
crash that takes the server down takes both, which is what the grace windows are for; they cannot
help with a crash that is still happening.

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

Simple Voice Chat for 1.21.11 is `1.21.11-2.6.22`, and **it needs Fabric Loader 0.18.1 or
later** - that is its requirement, not this mod's, but a server running an older loader will
refuse to start once the voice chat mod is dropped in. This mod itself is happy with 0.16.0 and
up either way.

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
* Lobby members are hidden from each other in the world, not in the player list. The tab list is
  how staff and players alike see who is online, so it is left alone.
* Weather is a property of the save rather than of a dimension, so rain in the overworld is rain
  in the lobby. With permanent noon and no mobs it does not amount to much.
* Changing `lobbyIsolateMembers` takes effect for people who enter the lobby afterwards; anybody
  already standing in it stays as they were until they leave and come back.
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
