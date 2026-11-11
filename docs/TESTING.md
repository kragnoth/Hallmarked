# Testing tools

All commands need op level 2. With no options, everything rolls exactly as natural loot would (the same odds, luck,
regions, uniques, appraisal and auto-quality), so these commands show what players will really see.

| Command | What it does |
|---|---|
| `/hallmarked generate [count] [options]` | Random equipment into your inventory (up to 64; drops anything that won't fit) |
| `/hallmarked generate for <players> <count> [options]` | The same for other players |
| `/hallmarked lootbarrel [options]` | A barrel in front of you: 24 generated pieces plus 4 Stamps, 16 Scrap and a Seal |
| `/hallmarked lootbarrel table <loot_table>` | A barrel carrying a real loot table; it rolls when opened, through every global loot modifier (grading, stamp and seal injections) and with the opener's Luck |
| `/hallmarked champion <entity> [options]` | That mob 3 blocks in front of you, in generated gear (a weapon and four armor pieces) that always drops; it's persistent so it won't despawn mid-test |
| `/hallmarked odds [source] [luck]` | Exact grade percentages for the held item (or an iron sword) at your position, after luck, regions and `GradeRollEvent` listeners; uniques are shown separately |
| `/hallmarked appraise` | Reveals the held Unappraised item, with fanfare |
| `/hallmarked data` | Counts of everything loaded after mod gating and `enabled:false`, plus how many items count as equipment |

The existing held-item commands (`inspect`, `grade roll/set/clear`, `quality roll/set/clear`, `affix add`, `unique set`)
are unchanged.

## Options
Space-separated `key=value` pairs, tab-completed. Any you leave out roll naturally.

| Option | Example | Effect |
|---|---|---|
| `item` | `item=minecraft:bow`, `item=#minecraft:swords` | Base item or item tag |
| `mod` | `mod=create` | Only items from that namespace (good for checking another mod's gear is detected) |
| `category` | `category=boots` | `tool`, `bow`, `shield`, `armor`, `helmet`, `chestplate`, `leggings`, `boots`, `bauble` |
| `source` | `source=crafted` | Which odds column: `loot` (default), `crafted`, `mob` (default for `champion`) |
| `luck` | `luck=4` | Luck for the roll (default: your Luck attribute; 0 for champions) |
| `grade` | `grade=gearwrought`, `grade=pure` | Force a grade, or pick one uniformly ignoring weights |
| `roll` | `roll=1` | Set every affix roll (0 = worst values, 1 = best) |
| `affix` | `affix=of_bleeding affix=searing` | Add an affix; repeatable; must be loaded (gated affixes need their mod) |
| `unique` | `unique=random` | Make a unique; picks one of its own base items |
| `quality` | `quality=keen`, `quality=random`, `quality=none` | Default: the config's auto-reforge chance |
| `appraised` | `appraised=false` | Force the Unappraised state on or off |
| `count` | `count=10` | `lootbarrel` only: how many generated pieces |
| `extras` | `extras=false` | `lootbarrel` only: leave out the Stamps, Scrap and Seal |

## Examples
```
/hallmarked generate 10                                  ten natural loot rolls
/hallmarked generate 5 grade=pure quality=random         flat odds across grades, always a quality
/hallmarked generate 1 item=minecraft:diamond_sword grade=masterwork roll=1 affix=of_bleeding appraised=true
/hallmarked generate 20 mod=<modid> category=tool       is that mod's gear detected and graded?
/hallmarked lootbarrel luck=10 extras=false
/hallmarked lootbarrel table minecraft:chests/ancient_city
/hallmarked champion minecraft:skeleton grade=gearwrought   beam, title, procs and a guaranteed drop
/hallmarked odds mob 0
```
