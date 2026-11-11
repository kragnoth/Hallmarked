# Integration: recipes, ingredients, events, commands, JEI

## Reforge materials (`hallmarked:reforge_material`)
Assay Anvil materials are recipes. They replace the old `Fallback` and `Overrides` config keys.

```json
{ "type": "hallmarked:reforge_material",
  "equipment": { "tag": "c:tools/bow" },
  "material":  { "item": "minecraft:string" },
  "cost": 1 }
```
* **No `equipment`:** the material works on any piece of equipment. The built-in `fallback_obsidian` is one of these.
* **Repair materials:** an item's own repair material always works at cost 1, with no recipe needed.
* **Overlapping recipes:** if several match, one that names the equipment wins over a catch-all.
* **Defaults:** `data/hallmarked/recipe/reforge/` holds `fallback_obsidian`, `bow_string`, `crossbow_iron` and
  `emerald_baubles`.

KubeJS:
```js
ServerEvents.recipes(e => {
  e.remove({ type: 'hallmarked:reforge_material', id: 'hallmarked:reforge/fallback_obsidian' })
  e.custom({ type: 'hallmarked:reforge_material', material: { item: 'create:brass_ingot' }, cost: 2 })
})
```

## Graded ingredient (`hallmarked:graded`)
This ingredient matches items by grade and/or quality. It works wherever an ingredient is accepted, including vanilla
recipes, Create, KubeJS and JEI.

```json
{ "type": "hallmarked:graded", "items": { "tag": "minecraft:swords" },
  "min_grade": "masterwork", "max_grade": "gearwrought", "quality": "keen" }
```
* Only `items` is required. Grades can be short ids (`masterwork`) or full names.
* An item with no grade counts as Common.
* JEI shows the example items stamped with the minimum grade or the quality, so players can see what's required.

## Events (NeoForge game bus)
These need no KubeJS plugin; use `NativeEvents.onEvent(<class name>, e => ...)`.

| Event | When | You can |
|---|---|---|
| `com.kragnoth.hallmarked.api.event.GradeRollEvent` | Before a grade is picked | Read the source, luck, player, level and position; `getWeight` / `setWeight(gradeName, w)` |
| `com.kragnoth.hallmarked.api.event.ItemGradedEvent` | After rolling, before stamping | Read or replace the `GradeData` with `setResult` |
| `com.kragnoth.hallmarked.api.event.ReforgeEvent` | When the Assay Anvil is struck | Cancel it, `setCost` (0 makes it free), or `setQuality` |

```js
// Gearwrought is 3x likelier in the Nether
NativeEvents.onEvent('com.kragnoth.hallmarked.api.event.GradeRollEvent', e => {
  if (e.level && String(e.level.dimension().location()) == 'minecraft:the_nether')
    e.setWeight('hallmarked.grade.gearwrought', e.getWeight('hallmarked.grade.gearwrought') * 3)
})
// Announce Gearwrought finds
NativeEvents.onEvent('com.kragnoth.hallmarked.api.event.ItemGradedEvent', e => {
  if (e.player && e.grade == 'hallmarked.grade.gearwrought')
    e.player.server.tell(`${e.player.username} found a Gearwrought piece!`)
})
```

## Commands (op level 2)
All of these act on the item in your main hand.

```
/hallmarked inspect
/hallmarked grade roll [crafted|loot|<custom source>]
/hallmarked grade set <grade>
/hallmarked grade clear
/hallmarked quality roll | set <quality> | clear
/hallmarked affix add <affix> [roll 0..1]
/hallmarked defaults rebuild <world|kubejs>
```

## JEI
* **Assay Anvil category:** every `reforge_material` recipe, with the material cost shown, and the anvil as the catalyst.
* **Info pages** on the Assay Anvil and on the baubles.
* The JEI plugin loads only when JEI is installed.
