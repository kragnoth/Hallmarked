# Inlays

Inlays are fixed, hand-applied enhancements: a homebrew route to known stats. They're applied at the vanilla
**smithing table**:

**Template + equipment + material → the same equipment with that inlay.**

## Slots
A grade's `inlay_slots` sets how many regular inlays an item of that grade can take. Ungraded items count as Common.

| Grade | Slots |
|---|---|
| Shoddy | 0 |
| Common | 2 |
| Journeyman | 1 |
| Masterwork | 1 |
| Gearwrought | 0 |

* An item can't take two regular inlays from the same `group`.
* Inlays only show in the tooltip; they don't rename the item. A Common item with inlays shows its name in copper,
  set by the grade's `inlay_tint` field. Higher grades keep their grade colour.
* A **grindstone** with only the inlaid item in it strips the regular inlays and leaves enchantments alone.

## Templates
* **Smith's Stamp:** copper ingot + paper gives 2. It's used up with each inlay.
* **Gearwright's Seal:** Smith's Stamp + Dragon Egg, or Smith's Stamp + Nether Star. It applies one **sealed inlay**,
  which follows different rules:
  - it ignores free slots, so it works on any grade, including Gearwrought and Shoddy;
  - it ignores the group rule, so Lapis + sealed Lapis gives +2 Looting and +2 Fortune;
  - there's one per item;
  - it's permanent, and the grindstone won't remove it.

  Any item in the `hallmarked:seal_templates` tag works as a Seal.

## Default inlays (16)
| Material | Inlay | Applies to | Effect |
|---|---|---|---|
| Flint | Flint-Edged | tools/weapons | +3 % attack damage, +3 % attack speed |
| Quartz | Quartz-Honed | tools/weapons | **+1 Sharpness** |
| Redstone | Geared | tools | +8 % dig speed |
| Lapis | Lapis-Inlaid | tools/weapons | **+1 Fortune, +1 Looting** |
| Copper ingot | Copper-Wound | tools, bows, shields, armor | **+1 Unbreaking** |
| Amethyst | Resonant | tools/weapons | +0.5 reach |
| Ender pearl | Far-Reaching | tools | +0.5 block reach |
| Iron nugget | Riveted | armor, shields | +1 armor |
| Leather | Padded | armor | +4 % knockback resistance |
| Honeycomb | Lacquered | armor | **+1 Protection** |
| Slime ball | Sprung | boots | +2 safe fall distance |
| Feather | Feathered | boots | −15 % fall damage |
| Prismarine shard | Tide-Etched | helmet / leggings | +1 oxygen / +15 % water movement |
| Blaze powder | Ember-Tempered | armor | −15 % burning time |
| String | Re-strung | bows | +4 % projectile damage |
| Gold nugget | Gilt | baubles | +0.25 luck |

Materials are matched by `c:` tags where they exist, so modded quartz, copper and similar items work.

## Virtual enchantments
`"enchantments": { "minecraft:looting": 1 }` adds levels on top of whatever the item really has, through NeoForge's
enchantment-level hook. Nothing is written to the item, and removing the inlay removes the levels. Levels can go past
an enchantment's normal maximum, for example Fortune III + Lapis = Fortune IV. Tune the values in the data if that's
too strong.

## Data format
`data/<ns>/inlays/<id>.json`
```json
{ "name": "hallmarked.inlay.quartz_honed", "group": "edge",
  "template": { "item": "hallmarked:smiths_stamp" },
  "material": { "tag": "c:gems/quartz" },
  "tool": { "modifiers": [] },
  "enchantments": { "minecraft:sharpness": 1 } }
```
* `template` is optional and defaults to the Smith's Stamp.
* The sections (`tool`, `bow`, `shield`, `armor`, `helmet`, `chestplate`, `leggings`, `boots`, `bauble`) decide
  which equipment accepts the inlay. An inlay that only grants enchantments still needs a section, even an empty one.
* Modifiers use a fixed `value`.
* Entries can be overridden or disabled the same way as other Hallmarked data (`"enabled": false`).
* To switch inlays off entirely, override `data/hallmarked/recipe/inlay/inlay.json`.
* `/hallmarked defaults rebuild` exports these files too.
