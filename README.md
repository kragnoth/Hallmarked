# Hallmarked

*Every forged thing bears a mark.*

Hallmarked is a NeoForge 1.21.1 mod that gives weapons, tools, bows, shields, armor and Curios baubles a **quality**,
a stamped mark such as *Keen*, *Rusted*, *Masterful*, *Forsaken* or *Ascended* that adds or subtracts attributes.
It was built for the **Gearwright's Hollow** modpack.

## Features
- **53 qualities** across tools, bows, shields, armor (per piece) and baubles, ranging from *Worthless* to *Ascended*.
- **Auto-marking:** the first time a piece of equipment enters your inventory, it has a chance (50 % by default) to roll a quality.
- **Assay Anvil:** place equipment and a material in it, then strike the hammer to re-roll the mark. Valid materials are the
  item's repair material, a configured override, or the fallback (obsidian). The items rest on the anvil's top.
- **Emerald Ring and Emerald Amulet:** Curios baubles that can carry bauble qualities. Curios itself is optional.
- **New player attributes:** `hallmarked:projectile_damage` and `hallmarked:damage_resistance`.
- **Compatibility:** Tetra modular items and Better Combat reach.

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

## Config
`config/hallmarked-common.toml` has these options:
- `Fallback`, `Overrides`, `Blacklist`
- `AutoReforge`, `AutoReforgeChance`
- `MaxDamageResistance`

## Building
Run `./gradlew build`. This needs JDK 21.

## License
MIT. See [LICENSE](LICENSE).

Hallmarked is a clean-room reimplementation of the gameplay of *Quality Equipment*. [docs/SPEC.md](docs/SPEC.md) is the behaviour
spec it was written from, and it lists every intentional difference. No code or assets from the original are included.
