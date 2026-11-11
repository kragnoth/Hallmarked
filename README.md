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
  - held-item test commands
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

Grades live in `data/<namespace>/grades/*.json`, affixes in `data/<namespace>/affixes/*.json` and fusions in `data/<namespace>/fusions/*.json`.
See [docs/GRADES.md](docs/GRADES.md) for their formats.

Item tags (`hallmarked:equipment/*`) decide what counts as equipment. To dump every default into editable files,
run `/hallmarked defaults rebuild world` (or `kubejs`); it backs up your existing files first.

## Config
`config/hallmarked-common.toml` has these options. Exclusions use the `hallmarked:equipment/excluded` item tag, and reforge
materials are recipes; both sync to clients, unlike the config.
- `AutoReforge`, `AutoReforgeChance`
- `Grading.Enabled`, `Grading.LuckFactor`
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
