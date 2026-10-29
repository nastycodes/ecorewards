# EcoRewards

[🇩🇪 Deutsche Version](README.de.md)

Fabric mod for **Minecraft 1.21.11** (Fabric Loader 0.20.1+): players earn money through the
[EcoBal Economy API](https://modrinth.com/mod/ecobal) for **every block they mine** and **every mob they kill**. A level
that rises with their progress multiplies the reward. Rewards per block/mob, the XP curve and the level multiplier are
fully configurable – in the game or in the config file.

Server-side only – players don't need the mod. In singleplayer it works too (install it with EcoBal on the client).

## Installation (server)

Into the `mods` folder:

| Mod | Version |
| --- | --- |
| `ecorewards-1.0.0.jar` | 1.0.0 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 0.141.6+1.21.11 or newer |
| [EcoBal Economy API](https://modrinth.com/mod/ecobal) | 1.0.1 for 1.21.11 |
| [fabric-permissions-api](https://modrinth.com/mod/fabric-permissions-api) | 0.6.1 (required by EcoBal) |
| [Mod Menu](https://modrinth.com/mod/modmenu) | optional, client only: in-game settings screen |

## How it works

- **Reward** = base value of the block/mob × level multiplier, rounded to 2 decimals.
- **XP** (progress points, not vanilla experience) comes with every block/mob as well; enough XP ⇒ level up.
- After every reward the action bar shows e.g. `+$2.50 | Level 7 (x1.3) | 120/159 XP`; a level up sends a chat
  message with a sound.
- The money is collected and paid out to EcoBal every `payoutIntervalSeconds` seconds (immediately on logout and
  server stop). Reason: EcoBal writes its whole balance file to disk on every single deposit.
- Mobs count when a player deals the killing blow **or** hit them shortly before (pushed into lava, killed by a tamed
  wolf …).

### Protection against money farms

Blocks that did not generate with the world pay nothing (`antiExploit.ignoreArtificialBlocks`):

- blocks placed by players (place → mine → place …),
- cobblestone/stone/obsidian/basalt generators (lava + water),
- blocks moved by pistons, falling sand/gravel, ice farms, snow golem trails, blocks from other mods.

Exceptions:

- **Natural growth** counts as natural: trees, sugar cane, cactus, bamboo, melons, pumpkins, amethyst …
  (list `antiExploit.naturalGrowthBlocks`).
- **Crops** (wheat, carrots, nether wart, cocoa, berries …) pay even when planted by players – but only fully grown
  (`blocks.matureCropsOnly`).
- **Commands** (`/setblock`, `/fill`, functions, command blocks) place natural blocks – handy for admin mines that are
  reset with `/fill`.
- A placed block stays artificial when it changes (stripping a log, tilling dirt, oxidizing copper); mining it clears
  the marker.

Mobs from monster spawners only give `mobs.spawnerMultiplier` (default 25 %) of money and XP.

## Commands

| Command | Permission | Effect |
| --- | --- | --- |
| `/ecorewards` or `/lvl` | everyone | your own level, XP and multiplier |
| `/ecorewards info <player>` | OP 2 | level of another player |
| `/ecorewards setlevel <player> <level>` | OP 2 | set the level (XP back to 0) |
| `/ecorewards addxp <player> <xp>` | OP 2 | give XP (with level ups) |
| `/ecorewards reset <player>` | OP 2 | back to level 1 |
| `/ecorewards check <x y z>` | OP 2 | is the block natural or artificial? |
| `/ecorewards natural <from> <to>` | OP 2 | mark an area as natural (e.g. a hand-built mine, at most 256 chunks) |
| `/ecorewards reload` | OP 2 | reload the config |

## Languages

All texts exist in **English** and **German**. Players who have the mod installed see their own game language.
For everybody else (the usual case on a server) the option `language` decides: `"en_us"` (default) or `"de_de"`.
Messages you have not changed switch along with it; edited messages stay as they are.

## Settings

**In the game:** with [Mod Menu](https://modrinth.com/mod/modmenu) open **Mods → EcoRewards → settings button**. Every
option has a tooltip and a reset button, input is checked before saving, and changes apply immediately. Lists and
reward tables are edited as text, one entry per line (`minecraft:zombie = 2.5, 3`). The screen edits the config of the
game you are in – your own singleplayer/LAN world; on a dedicated server edit the server's file.

**Config file:** `config/ecorewards.json`, created with the defaults on first start. After changes run
`/ecorewards reload`.

### General

| Key | Default | Meaning |
| --- | --- | --- |
| `rewardInCreative` | `false` | also reward players in creative mode |
| `payoutIntervalSeconds` | `3` | payout interval to EcoBal (`0` = immediately) |
| `language` | `"en_us"` | server language for players without the mod: `"en_us"` or `"de_de"` |

### Levels (`levels`)

XP from level N to N+1 = `xpBase × xpGrowth^(N−1) + xpIncrement × (N−1)`

| Key | Default | Meaning |
| --- | --- | --- |
| `maxLevel` | `50` | highest level |
| `xpBase` | `100` | XP for level 1 → 2 |
| `xpGrowth` | `1.08` | factor per level (`1.0` = always the same) |
| `xpIncrement` | `0` | extra fixed XP per level |
| `xpOverrides` | `{}` | fixed values, e.g. `{"10": 5000}` = level 10 → 11 needs 5000 XP |

With the defaults: level 2 after 100 XP, level 10 after ~1,250 XP, level 50 after ~53,000 XP.

### Level multiplier (`multiplier`)

Multiplier = `base + perLevel × (level − 1)`, at most `max`

| Key | Default | Meaning |
| --- | --- | --- |
| `base` | `1.0` | multiplier at level 1 |
| `perLevel` | `0.05` | increase per level (+5 %) → level 50 = ×3.45 |
| `max` | `0` | upper limit (`0` = none) |
| `overrides` | `{}` | fixed values per level, e.g. `{"25": 2.0, "50": 5.0}` |

### Blocks (`blocks`) and mobs (`mobs`)

```json
"rewards": {
  "default":                  { "money": 0.05, "xp": 1 },
  "minecraft:ancient_debris": { "money": 50.0, "xp": 40 },
  "#minecraft:diamond_ores":  { "money": 25.0, "xp": 20 },
  "minecraft:villager":       { "money": 0.0,  "xp": 0 }
}
```

- Key = block/entity id (`minecraft:zombie`) or a tag with `#` (`#minecraft:logs`, `#minecraft:undead`). Blocks and
  mobs of other mods work the same way.
- Order: exact id → first matching tag (top to bottom) → `default`.
- `default` applies to **all** other blocks/mobs. Remove it ⇒ only listed ones count. `{ "money": 0, "xp": 0 }`
  excludes something.
- Unknown ids (typos) are reported in the log when the server starts.

| Key | Default | Meaning |
| --- | --- | --- |
| `blocks.enabled` / `mobs.enabled` | `true` | switch on/off |
| `blocks.requireCorrectTool` | `true` | ores/stone only with the right tool |
| `blocks.matureCropsOnly` | `true` | crops only when fully grown |
| `blocks.defaultSkipsInstantBreak` | `true` | `default` does not apply to blocks that break instantly (grass, flowers, torches); listed ones still count |
| `mobs.spawnerMultiplier` | `0.25` | factor for mobs from monster spawners |

### Anti-exploit (`antiExploit`)

| Key | Default | Meaning |
| --- | --- | --- |
| `ignoreArtificialBlocks` | `true` | artificial blocks pay nothing |
| `naturalGrowthBlocks` | trees, plants … | blocks (ids/tags) that count as natural when they grow |

### Messages (`messages`)

Color codes `&a`, `&l`, … and hex colors `&#FF8800`. An empty text = no message.
Placeholders: `{player}`, `{money}`, `{gainedXp}`, `{level}`, `{multiplier}`, `{xp}`, `{xpNeeded}`, `{progress}`.

| Key | Meaning |
| --- | --- |
| `showRewardActionBar` | action bar after every reward |
| `reward` / `rewardXpOnly` | action bar with money / XP only |
| `progress` / `progressMaxLevel` | content of `{progress}` |
| `levelUp`, `levelUpSound` | level-up message and sound |
| `broadcastLevelUp`, `levelUpBroadcast` | announce level ups to all players |
| `info` / `infoOther` | output of `/ecorewards` |

## Development

Needs a JDK 21+.

```
gradlew build              # builds build/libs/ecorewards-1.0.0.jar, runs the server game tests and the translation check
gradlew runGameTest        # server game tests only
gradlew runClientGameTest  # end-to-end test in a real client, screenshots in build/run/clientGameTest/screenshots
python tools/gen_icon.py . # regenerates the icon
```

`runGameTest` starts a test server with EcoBal and checks among other things: natural ore pays × multiplier, without a
tool/placed pays nothing, crops only when grown, mob kills including the spawner reduction and indirect kills, the
level curve, obsidian from lava + water, pistons, `/setblock`, growth, that all mixins apply, and that unchanged
messages follow the server language. The client game test (English, 1920×1080, with EcoBal) mines natural ores, levels
up, kills a husk, runs `/lvl` and changes settings in the config screen behind Mod Menu's button – a value, a reward
table, the server language and the "discard changes?" prompt. `checkTranslations` (part of `build`) fails when
`en_us.json` and `de_de.json` don't have the same keys.

## License

[MIT](LICENSE)
