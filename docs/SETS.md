# Named sets

Four sets ship by default. Each piece is an ordinary item dressed in the set's look (leather dye, armor trim, shield
colour), with its own stats; wearing more pieces unlocks bonuses. Pieces count only where they belong: armor in its
slot, the weapon in your main hand, the shield in either hand, the phylactery in a Curios slot. Set pieces are a green
**Set** grade: 1.5x durability, a loot beam, one inlay slot.

| Set | Role | Pieces | 2 pieces | 4 pieces | 5 pieces |
|---|---|---|---|---|---|
| **Harness of the Free Lance** | melee | iron sallet and sabatons, chainmail hauberk and chausses (copper trim), arming sword | +5 % damage | +8 % attack speed, +2 health | 25 % cleave, 15 % bleed |
| **Regalia of the Oathsworn** | defense | iron helm, cuirass, greaves, sabatons (gold ward trim), blue bulwark shield | +2 armor | +2 toughness, +10 % knockback resist, +4 health | +5 % damage resistance, 25 % reprisal |
| **Vestments of the Thaumaturge** | magic | violet leather cowl, robe, breeches, slippers (amethyst eye trim), emerald phylactery | spell power, magic damage, XP, luck | mana, mana regen, cooldowns, magic resist, +2 health | spell power, cast time, magic damage, luck |
| **Garb of the Wayfarer** | ranged | green leather hood, leggings, boots, chainmail brigandine (emerald wayfinder trim), longbow | +5 % speed, sneak speed | +10 % projectile damage, arrow speed, crit | +15 % projectile damage, draw speed, luck |

Magic and ranged stats use Iron's Spellbooks, Lodestone, Caverns & Chasms and Apothic Attributes when installed; a stat
whose mod is missing is simply left out, so every set still works in a pack without them.

**Where they come from:** when gear is graded from loot (0.4 % per matching item) or mob gear (0.1 %), an item that is a
set piece's base item may become that piece. Mountebank parcels at Journeyman and above can hold set pieces too.
`/hallmarked sets give <set> [piece]` gives one for testing.

## Format
`data/<namespace>/sets/*.json`:

```json
{ "color": "aqua", "chance": { "loot": 0.004, "mob": 0.001 },
  "pieces": [
    { "key": "helm", "item": "minecraft:iron_helmet", "trim": { "pattern": "minecraft:ward", "material": "minecraft:gold" },
      "dye": "#3a2458", "base_color": "blue", "glint": false,
      "modifiers": [ { "name": "my_helm_toughness", "attribute": "minecraft:generic.armor_toughness", "operation": "addition", "value": 1 } ] } ],
  "bonuses": [
    { "pieces": 2, "modifiers": [ ... ] },
    { "pieces": 5, "modifiers": [ ... ], "procs": [ { "type": "reprisal", "chance": 0.25, "power": 0.3 } ] } ] }
```

Names come from the lang keys `<set name>` and `<set name>.<piece key>`. Any item can be a piece, modded ones included.
