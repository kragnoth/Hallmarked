# The Loot Loop: salvage, appraisal, uniques, tempering, regions

## Salvage → Smith's Scrap
Put a single piece of equipment in a **grindstone**:
1. **It has regular inlays:** they're scraped off and enchantments are left alone.
2. **It has enchantments:** vanilla disenchants as usual.
3. **Otherwise:** it's salvaged into **Smith's Scrap**. The amount comes from the grade's `salvage` field: Shoddy 1,
   Common 1, Journeyman 2, Masterwork 4, Gearwrought 8, Unique 6.

Uses for scrap:
* 4 scrap make 1 Smith's Stamp (2×2 recipe).
* 2 scrap re-mark any equipment at the Assay Anvil (`recipe/reforge/scrap.json`).

## Appraisal
Found gear (from loot or mobs, not crafted) of a grade with `"appraise": true` (Masterwork, Gearwrought, Unique)
arrives **Unappraised**:
* Its name is "Unappraised Iron Sword" in the grade colour, and its affixes are **hidden and inactive**.
* The loot beam still shows, so you know it's worth carrying home.
* Put it in an **Assay Anvil** and strike the hammer. Appraisal is free and needs no material.
* On appraisal the affixes wake up, the real name or title appears, and the grade's fanfare plays.

## Uniques (`data/<ns>/uniques/*.json`)
Hand-written named items with fixed stats, optional virtual enchantments and a lore line. Found gear whose base item
matches has a chance, per roll source, to become the unique instead of rolling a grade. Uniques use the **Unique**
grade (light purple, `weights` all 0 so it never rolls normally). They arrive Unappraised, get a beam and a champion
title, are announced server-wide, and have 1 inlay slot.

| Unique | Base | Highlights |
|---|---|---|
| The Lamplighter's Hook | iron sword | +12 % damage, +0.75 reach, +1 Looting |
| Bellringer's Maul | iron axe | +18 % damage, +1 knockback, −8 % speed |
| Widow's Toll | crossbow | +18 % projectile damage, +0.5 luck, +1 Quick Charge |
| The Gearwright's Apron | leather chestplate | +3 armor, +1 toughness, +4 health, +2 Unbreaking |
| Coalwalkers | iron boots | −50 % burning, +3 safe fall, +5 % speed |
| The Quiet Charm | emerald amulet | +1 luck, +3 % damage resistance |

The default chances are 1 % from loot and 0.2 % from mobs (the Quiet Charm is 2 % from loot).
`/hallmarked unique set <id>` makes the held item a unique.

```json
{ "name": "hallmarked.unique.lamplighters_hook", "lore": "hallmarked.unique.lamplighters_hook.lore",
  "items": { "item": "minecraft:iron_sword" }, "chance": { "loot": 0.01, "mob": 0.002 },
  "tool": { "modifiers": [ { "name": "lamplighter_damage", "attribute": "minecraft:generic.attack_damage",
                             "operation": "multiply_total", "value": 0.12 } ] },
  "enchantments": { "minecraft:looting": 1 } }
```

## Tempering at the Ashen Altar
* **Recipe:** candles, crying obsidian and polished blackstone.
* **Use:** hold a graded, appraised, non-unique piece in your main hand and a **catalyst** in your off hand (the
  `hallmarked:tempering_catalysts` tag, Echo Shard by default), then use the altar.
* **Outcome:** one catalyst is consumed and a weighted outcome from `data/<ns>/tempering/*.json` applies:

| Outcome | Weight | Effect |
|---|---|---|
| Ascend | 25 | Goes up one grade and re-rolls its affixes. At the top grade, you get Forbidden instead. |
| Forbidden | 25 | Gains a **forbidden affix** beyond the normal limits: strong, with a cost. |
| Marred | 30 | Gains a flaw. |
| Silent | 20 | Nothing changes. |

Every outcome leaves the piece **Tempered**, which means:
* it can't be tempered again;
* it can't be re-marked at the Assay Anvil;
* it can't take inlays, including a Seal.

Forbidden affixes are affixes with `"type": "forbidden"` and never roll normally: Bloodbound, Hollow-Eyed, Soulworn,
Ash-Strung, Unquiet and Grave-Touched.

## Regional quality (`data/<ns>/regions/*.json`)
Where loot and mob rolls happen changes their odds. Every matching region applies: luck adds together, and grade
multipliers multiply.

| Region | Condition | Effect |
|---|---|---|
| Nether | dimension the_nether | +1 luck |
| The End | dimension the_end | +2 luck |
| Deep | overworld, y ≤ 0 | +0.5 luck |
| Frontier | overworld, ≥ 3000 blocks from spawn | +0.75 luck |
| Far frontier | overworld, ≥ 10000 blocks from spawn | +0.75 luck, Gearwrought ×1.5 |

Conditions are `dimension`, `min_distance`/`max_distance` (horizontal blocks from world spawn), `min_y`/`max_y` and
`sources`. `sources` defaults to loot and mob, so crafting somewhere special doesn't help.
