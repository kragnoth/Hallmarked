# Hallmarked — Behaviour Specification

This document is the clean-room spec for **Hallmarked**. It describes only *observable behaviour* of the
"Quality Equipment 2.0" jar (NeoForge 1.21.1). It was written by observing the jar, and contains
no code from it. The Hallmarked implementation was written from this document plus the public
NeoForge and Minecraft sources. No assets from the original jar are shipped.

Section 10 lists every place where Hallmarked deliberately differs from the original (bug fixes and cleanup).

---

## 1. Concept

Equipment can carry exactly one **quality**, such as *Sharp*, *Rusted* or *Ascended*. A quality is a named
bundle of attribute modifiers. A single quality can define different modifiers per **equipment category**.
Qualities are data-driven JSON files that datapacks can override or extend.

An item gets a quality in one of two ways:
1. **Auto-reforge.** The first time an eligible item is seen in a player's inventory, it has a configurable
   chance of rolling a random quality. Each item gets exactly one roll.
2. **Reforging Station** (Hallmarked: *Assay Anvil*). Put the item and a valid material in the station,
   then press reforge. One material is consumed and the item rolls a new random quality.

## 2. Equipment categories

Categories are checked in the order below. The first one that matches decides the item's category.

| Category | Matches | Where modifiers apply |
|---|---|---|
| `bauble` | Item is in any `curios:*` item tag (only when Curios is loaded) | While worn in any Curios slot (player attributes) |
| `tool` | `TieredItem` (swords, pickaxes, axes, shovels, hoes) or a Tetra modular item | Main hand |
| `bow` | `BowItem`, `CrossbowItem`, Tetra modular bow or crossbow | Either hand |
| `shield` | `ShieldItem` or Tetra modular shield | Off hand |
| `armor` | `ArmorItem` | That piece's armor slot |

For **armor**, a quality may define a generic `armor` section, a piece-specific section
(`helmet`, `chestplate`, `leggings`, `boots`), or both.
* **Roll pool** for an armor piece: every quality with a matching piece section, plus every quality with an `armor` section.
* **Applied section:** if the quality has an `armor` section, that section is used. Otherwise the piece-specific section is used.

An item is **eligible** if it falls into a category *and* at least one loaded quality defines that category.

## 3. Quality data format

Files live at `data/<namespace>/qualities/<id>.json` and are loaded from every namespace.

```json
{
  "name": "quality_equipment.quality.sharp",
  "color_code": "§9",
  "tool":   { "modifiers": [ { "name": "sharp_attack_damage", "attribute": "minecraft:generic.attack_damage", "operation": "multiply_total", "value": 0.1 } ] },
  "bow":    { "modifiers": [ ... ] },
  "shield": { "modifiers": [ ... ] },
  "armor":  { "modifiers": [ ... ] },
  "helmet": { ... }, "chestplate": { ... }, "leggings": { ... }, "boots": { ... },
  "bauble": { "modifiers": [ ... ] }
}
```

* `name` is the quality's identity *and* its translation key. Two files with the same `name` resolve to
  one quality, and the later pack wins.
* `color_code` is a `§` formatting code used for the quality name in tooltips.
* `operation` is one of `addition` (add value), `multiply_base` (add multiplied base) or `multiply_total`
  (add multiplied total; this is also the fallback for unknown strings).
* `attribute` is a registry id. A missing namespace means `minecraft`.
* Within one quality, modifier `name`s become modifier ids. When a generic `armor` section is applied, the
  piece name is appended to the id so that four pieces with the same quality stack.
* Roll selection is uniformly random across the eligible pool.

## 4. Stored item state

* Quality: a string on the item (the quality `name`).
* Auto-reforge flag: a boolean on the item, set the first time the item is seen. It prevents any further auto rolls.

## 5. Attributes added to players

| Attribute | Base | Range | Effect |
|---|---|---|---|
| `projectile_damage` | 1.0 | −100…100 | Damage from projectiles the player shoots is multiplied by this value. |
| `damage_resistance` | 1.0 | 0…100 | Incoming damage is reduced by `(value − 1)`. Example: `multiply_total 0.03` gives 3 % less damage. |

## 6. Reforging Station block

* Horizontal facing (faces the player on placement). Waterloggable, not a full cube, anvil sounds.
  Strength 1.5 / 10. Mined with a pickaxe at stone tier or better, and drops itself.
* Has an inventory with an **equipment** slot and a **material** slot. Items stay in the block when the GUI
  closes, render on its top surface, and drop when the block is broken.
* Comparator output reflects how full the inventory is.
* Automation: hoppers and pipes may insert into either slot but may not extract.
* **The material is valid when any of these is true:**
  1. The config `Overrides` list has an entry `equipment_id,material_id` matching the pair, or
  2. The material is a valid repair item for the equipment, or
  3. The material equals the config `Fallback` item.
* **Reforge is available when** the equipment is eligible, not blacklisted, and the material is valid.
  The GUI shows a lit hammer when it is available and a dark hammer otherwise.
* **On reforge:** the item rolls a random quality from its category pool, one material is consumed, and the
  anvil-use sound plays.
* Recipe: `smooth_stone_slab / anvil / smooth_stone_slab` over `obsidian / crafting_table / obsidian`.

## 7. Baubles (Curios, optional)

* **Emerald Ring** (`curios:ring`) and **Emerald Amulet** (`curios:necklace`). Both stack to 1.
  * Ring: emerald on top, gold nuggets in a ring shape below.
  * Amulet: gold-nugget arch over an emerald.
* Data adds one `ring` and one `necklace` Curios slot to players.
* Every Curios-slotted item with a quality applies its `bauble` modifiers to the player while worn.
  Two copies of the same quality stack.

## 8. Tooltips

* The tooltip of an item with a quality gains a `Quality: <Name>` line in the quality's colour.
* Below it is a heading (e.g. "When in Main Hand:", or "When in any curios slot:" for baubles), followed by
  the quality's modifiers, positives first (blue) and negatives after (red).
* The quality's modifiers are removed from vanilla's own attribute sections, so they aren't listed twice.

## 9. Config (common)

| Key | Default | Meaning |
|---|---|---|
| `Fallback` | `minecraft:obsidian` | Material that can always reforge an item |
| `Overrides` | bow→string, crossbow→iron ingot, ring/amulet→emerald | `equipment,material` pairs |
| `Blacklist` | empty | Items that can never be reforged |
| `AutoReforge` | true | Enables auto-reforge |
| `AutoReforgeChance` | 0.5 | Chance that an auto roll applies a quality (0.1 … 1.0) |

Optional Better Combat integration: the player's `entity_interaction_range` bonus is forwarded to Better
Combat's attack range.

---

## 10. Deliberate differences in Hallmarked

| # | Original 2.0 behaviour | Hallmarked |
|---|---|---|
| 1 | **Damage resistance:** after the hit, health was *set* back by a hard-coded heal of 35 % of the **pre-armor** damage, on every hit, for every player, whatever the attribute said. With good armor the heal could exceed the post-armor damage, which made players invincible. | Applied as a **damage reduction** on post-armor damage, using the real attribute value, capped by `MaxDamageResistance` (default 0.8). No healing, so invincibility is impossible. Without resistance baubles there is no effect. |
| 2 | Projectile damage checked whether the *arrow entity* was a player, so the bonus never applied. | Uses the shooter (`getEntity()`) and the `minecraft:is_projectile` damage-type tag. |
| 3 | The reforge "button" was a fake item placed in a third slot. It could leak into inventories and dropped when the block broke. | A real GUI button with a server-validated packet. The block has two slots. |
| 4 | Station items were rendered by scanning every chunk in render distance every frame. | Standard block-entity renderer. |
| 5 | Quality state lived in `custom_data` (`Reforge`, `Autoreforge`). | Proper data components `hallmarked:quality` / `hallmarked:rolled`. **Legacy items are migrated automatically** when they enter a player inventory. |
| 6 | The config spec was built before its values were defined. | Defined properly in `hallmarked-common.toml`. |
| 7 | Bauble modifiers were re-parsed from JSON strings in player NBT every tick. | Diffed every 10 ticks against the applied set, so there is no health flicker when max-health baubles change. |
| 8 | Qualities were loaded on the overworld load event, with duplicate entries when files shared a name. | Standard reload listener, de-duplicated by name, and synced to clients on login and `/reload`. |
| 9 | The blacklist was ignored by auto-reforge. | The blacklist also blocks auto-reforge. |
| 10 | Station in the Spawn Eggs creative tab. | Hallmarked creative tab. |
| 11 | — | Gearwright's Hollow qualities are built in: **Exquisite, Mythic, Divine, Ascended, Forsaken, Impenetrable, Blessed** (53 qualities total). |
| 12 | — | Optional per-quality `"weight"` field (default 1) for weighted rolls. |
