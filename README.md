# Hallmarked

*Every forged thing bears a mark.*

Hallmarked is a NeoForge 1.21.1 mod that gives weapons, tools, bows, shields, armor and Curios baubles a **quality**,
a stamped mark such as *Keen*, *Rusted*, *Masterful*, *Forsaken* or *Ascended* that adds or subtracts attributes.
It was built for the **Gearwright's Hollow** modpack.

## Features
- **Grades of work, Diablo-style:** every piece of equipment is *Shoddy*, *Common*, *Journeyman*, *Masterwork* or
  *Gearwrought*. Higher grades roll more affixes with stronger values. Journeyman pieces get names like
  "Honed Iron Sword of Haste", and Masterwork and Gearwrought pieces get titles like "Ashen Vow". Loot rolls
  better than crafted gear, Luck tips the odds, and a crafted Gearwrought piece is rare but possible.
  Matching affix pairs fuse into named compounds (Honed + of Haste becomes *Duelist's*) that also shape item titles.
  See [docs/GRADES.md](docs/GRADES.md).
- **Inlays:** add known, fixed stats at the smithing table with a Smith's Stamp and a common material. Quartz gives
  +1 Sharpness, Lapis gives +1 Fortune and Looting, Copper gives +1 Unbreaking, and so on. The number of slots
  depends on the grade, with Common gear as the canvas. A rare **Gearwright's Seal** (made with a Nether Star or a
  Dragon Egg) adds one permanent extra inlay that can repeat one already present. See [docs/INLAYS.md](docs/INLAYS.md).
- **Found gear feels found:** Masterwork rolls play a sound and show an action-bar line, and Gearwrought finds are
  announced to the whole server. Mobs spawn with graded gear and fight with it. Masterwork and Gearwrought drops, and the mobs carrying
  them, show Diablo-style loot beams, and those mobs get a title like *"Zombie, Bearer of Grim Ledger"*. Tooltips stay compact, with Shift
  for the full stats. Stamps and Seals also turn up in structure chests.
- **The loot loop:**
  - Salvage unwanted gear into **Smith's Scrap**.
  - Found Masterwork, Gearwrought and Unique pieces arrive **Unappraised** until you strike them at the Assay Anvil.
  - Hand-written **Uniques** carry lore.
  - Gamble a piece at the **Ashen Altar**: it may ascend, gain a forbidden affix, or be marred, and is locked either way.
  - Deeper, farther and stranger places roll better, early chests roll worse, and crude materials (wood, leather,
    stone, chainmail) are capped unless they turn up somewhere special, while gold takes a mark best.

  See [docs/LOOT_LOOP.md](docs/LOOT_LOOP.md).
- **Procs:** Bleeding, Staggering, Leeching, of Embers, Cleaving, of Reprisal and of the Reaper put combat effects
  on affixes, for players and for mobs carrying the gear. Affixes from other mods (Apothic Attributes, Caverns & Chasms, NeoVitae, Iron's Spellbooks,
  Lodestone, FTB Ultimine) only exist when those mods are installed. See [docs/PROCS.md](docs/PROCS.md).
- **The Mountebank:** a shady villager who sells sealed parcels. Open one to find out what you paid for: cursed junk,
  ordinary gear, or exceptional work that rolls past its normal limits. His coffer is locked storage only you can open,
  and fences your found gear for emeralds and your spare set pieces for tokens. See [docs/GAMBLING.md](docs/GAMBLING.md).
- **Named sets:** fourteen of them, from the Free Lance and the Oathsworn to the Ashwalker (swim in lava), the Salvage
  Diver (mine underwater), the Silent Knife and the Reaver. Pieces mix looks (leather hood, chainmail body, plate boots)
  and many carry diamond or netherite stats under them. 2 to 5-piece bonuses, effects and procs; data-driven, any item
  can be a piece. See [docs/SETS.md](docs/SETS.md).
- **Grade affects durability:** Shoddy x0.75, Journeyman x1.1, Masterwork x1.25, Gearwrought, Unique and Set x1.5
  (the grade's `durability` key).
- **53 qualities** across tools, bows, shields, armor (per piece) and baubles, ranging from *Worthless* to *Ascended*.
- **Auto-marking:** the first time a piece of equipment enters your inventory, it has a chance (50 % by default) to roll a quality.
- **Assay Anvil:** place equipment and a material in it, then strike the hammer to re-roll the mark. Valid materials are the
  item's repair material or any `hallmarked:reforge_material` recipe (obsidian works for everything by default). The items
  rest on the anvil's top.
- **Emerald Ring and Emerald Amulet:** Curios baubles that can carry bauble qualities. Curios itself is optional.
- **New player attributes:** `hallmarked:projectile_damage` and `hallmarked:damage_resistance`.
- **Pack-maker hooks:**
  - a `hallmarked:reforge_material` recipe type for anvil materials
  - a `hallmarked:graded` ingredient, e.g. "requires a Masterwork sword"
  - NeoForge events for grade odds, graded items and reforges, usable from KubeJS without a plugin
  - test commands: generate tuned random gear, spawn a loot barrel or a champion mob, print exact grade odds
    (see [docs/TESTING.md](docs/TESTING.md))
  - JEI support

  See [docs/INTEGRATION.md](docs/INTEGRATION.md).
- **Compatibility:** Tetra modular items, Better Combat reach, JEI.

## Datapacks
Qualities are JSON files at `data/<namespace>/qualities/*.json`. A file whose `name` matches an existing quality replaces it.

```json
{
  "name": "hallmarked.quality.keen",
  "color": "blue",
  "weight": 1,
  "tool": { "modifiers": [
    { "name": "keen_attack_damage", "attribute": "minecraft:generic.attack_damage", "operation": "multiply_total", "value": 0.1 }
  ]}
}
```

* **Sections:** `tool`, `bow`, `shield`, `armor`, `helmet`, `chestplate`, `leggings`, `boots`, `bauble`.
* **Operations:** `addition`, `multiply_base`, `multiply_total`.

Datapacks written for *Quality Equipment* (`quality_equipment.quality.*` names, `color_code`, and `quality_equipment:` attributes)
still load. Items that carry the old mod's quality are converted automatically when they enter a player's inventory.

**Quality conversions** (`data/<namespace>/quality_conversions/*.json`): qualities are written for tools in general, so on a
pure weapon or a pure digger part of the bundle would do nothing. Instead, that stat becomes the matching one for the tool's role,
marked *(suited to this tool)* in the tooltip. Axes, bows, armor and baubles are unaffected, and penalties are never converted.

| Role | From | To |
|---|---|---|
| weapon (sword, mace) | break speed | attack damage (×0.75) |
| digger (pickaxe, shovel, hoe) | attack damage | break speed |
| digger | attack speed | mining efficiency (+15 per +100 %) |
| digger | reach | block reach |

```json
{ "role": "weapon", "from": "minecraft:player.block_break_speed", "to": "minecraft:generic.attack_damage",
  "scale": 0.75, "operation": "multiply_total", "convert_negative": false }
```

Grades live in `data/<namespace>/grades/*.json`, affixes in `data/<namespace>/affixes/*.json` and fusions in `data/<namespace>/fusions/*.json`.
See [docs/GRADES.md](docs/GRADES.md) for their formats.

Item tags (`hallmarked:equipment/*`) decide what counts as equipment. Baubles are opt-in through `hallmarked:equipment/bauble`
(by default the ring, necklace, bracelet and belt Curios slots; the charm slot is a catch-all for sigils, tomes and totems), so backpacks, terminals and other gear worn in
Curios slots stay unmarked. Horse and wolf armor is never marked. To dump every default into editable files,
run `/hallmarked defaults rebuild world` (or `kubejs`); it backs up your existing files first.

## Config
`config/hallmarked-common.toml` has these options. Exclusions use the `hallmarked:equipment/excluded` item tag, and reforge
materials are recipes; both sync to clients, unlike the config.
- `AutoReforge`, `AutoReforgeChance`
- `Grading.Enabled`, `Grading.LuckFactor`, `Grading.MobEquipment`, `Grading.Appraisal` (off = no Unappraised gear; overrides the grade files)
- `hallmarked-client.toml`: `ItemBeams`, `MobBeams`, `MobTitles`, `Range`
- `Procs.Enabled`, `Procs.MobsUseProcs`
- `MaxDamageResistance`

## Building
Run `./gradlew build`. This needs JDK 21.

## Acknowledgements
Hallmarked stands on other people's ideas. No code from any of these projects is included; thanks to:

- **Quality Equipment** by PooPmeep: the original concept of equipment qualities and a reforging station. Hallmarked
  began as a clean-room rebuild of its gameplay (see [docs/SPEC.md](docs/SPEC.md)).
- **Apotheosis** and **Placebo** by Shadows_of_Fire: they showed the right patterns for affix-style systems on
  NeoForge, including modifier ids that are unique per equipment slot, virtual enchantment levels through
  `GetEnchantmentLevelEvent`, and leaving server data alone on an integrated host when syncing to clients.
- **Farmer's Delight** by vectorwing: a reference for block entity, menu and client-sync conventions.
- **Diablo I and II** (Blizzard): the inspiration for grades, prefixes and suffixes, generated names, and rarity-driven rolls.
- **Excalibur** by Maffhew: the visual style the original textures are drawn to match.
- **Curios API** (TheIllusiveC4), **JEI** (mezz) and **NeoForge**, which the integrations are built on.

## License
MIT. See [LICENSE](LICENSE).

Hallmarked is a clean-room reimplementation of the gameplay of *Quality Equipment*. [docs/SPEC.md](docs/SPEC.md) is the behaviour
spec it was written from, and it lists every intentional difference. No code or assets from the original are included.
