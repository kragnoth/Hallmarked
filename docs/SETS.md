# Named sets

Four sets ship by default. Each piece is an ordinary item dressed in the set's look (leather dye, armor trim, shield
colour), with its own stats; wearing more pieces unlocks bonuses. Pieces count only where they belong: armor in its
slot, the weapon in your main hand, the shield in either hand, the phylactery in a Curios slot. Set pieces are a green
**Set** grade: 1.5x durability, a loot beam, one inlay slot.

| Set | Role | Pieces | 2 pieces | 3 pieces | 4 pieces | 5 pieces |
|---|---|---|---|---|---|---|
| **Harness of the Free Lance** | melee | iron sallet and sabatons, chainmail hauberk and chausses (copper trim), arming sword | +5 % damage | +0.5 knockback, +5 % knockback resist | +8 % attack speed, +2 health | 25 % cleave, 15 % bleed |
| **Regalia of the Oathsworn** | defense | iron helm, cuirass, greaves, sabatons (gold ward trim), blue bulwark shield | +2 armor | +1 armor, +2 health | +2 toughness, +10 % knockback resist, +4 health | +5 % damage resistance, 25 % reprisal |
| **Vestments of the Thaumaturge** | magic | violet leather cowl, robe, breeches, slippers (amethyst eye trim), emerald phylactery | spell power, magic damage, XP, luck | mana regen, magic resist, XP, +1 health | mana, mana regen, cooldowns, magic resist, +2 health | spell power, cast time, magic damage, luck |
| **Garb of the Wayfarer** | ranged | green leather hood, leggings, boots, chainmail brigandine (emerald wayfinder trim), longbow | +5 % speed, sneak speed | +1 armor, +2 safe fall | +10 % projectile damage, arrow speed, crit | +15 % projectile damage, draw speed, luck |

The Wayfarer also has a **per-piece** bonus: +2 % projectile damage for every piece worn (+10 % with all five).
Bonuses stack: wearing four pieces gives the 2, 3 and 4-piece bonuses.

Magic and ranged stats use Iron's Spellbooks, Lodestone, Caverns & Chasms and Apothic Attributes when installed; a stat
whose mod is missing is simply left out, so every set still works in a pack without them.

**Where they come from:**
* **Fitting places.** Each set lists the loot tables where it belongs, and a chest there may hold an extra piece:

  | Set | Tables (chance per chest) |
  |---|---|
  | Free Lance | village weaponsmith and armorer 5 %, pillager outpost 6 %, trial chamber vaults 5 %, bastions 3 % (helm, boots, sword) |
  | Oathsworn | stronghold corridors and crossings 5 %, end city treasure 6 %, desert and jungle temples 3 % (helm, cuirass, bulwark), village temple 3 % (bulwark, helm), village armorer 2 % (armor) |
  | Thaumaturge | woodland mansion 8 %, stronghold library 8 %, ancient city 5 %, igloo 4 % (cowl, robe), village temple and cartographer 3 % (phylactery, cowl), village tannery 3 % (leather pieces) |
  | Wayfarer | village fletcher 5 %, village tannery 5 % (leather pieces), pillager outpost 5 % (bow, brigandine, hood), jungle temple 5 %, shipwreck supply 2 % (hood, boots), trial chamber vaults 3 % (bow, brigandine) |

  The chance scales with luck: x(1 + LuckFactor x luck), counting the table's loot-odds luck (village chests -2, late
  tables +1) and the looter's Luck, never below a quarter of the listed chance.
* **Any matching drop.** When gear is graded from loot (0.4 % per matching item) or mob gear (0.1 %), an item that is a
  set piece's base item may become that piece.
* **Mountebank parcels** at Journeyman and above.
`/hallmarked sets give <set> [piece]` gives one for testing.

## Format
`data/<namespace>/sets/*.json`:

```json
{ "color": "aqua", "chance": { "loot": 0.004, "mob": 0.001 },
  "pieces": [
    { "key": "helm", "item": "minecraft:iron_helmet", "trim": { "pattern": "minecraft:ward", "material": "minecraft:gold" },
      "dye": "#3a2458", "base_color": "blue", "glint": false,
      "modifiers": [ { "name": "my_helm_toughness", "attribute": "minecraft:generic.armor_toughness", "operation": "addition", "value": 1 } ] } ],
  "tables": [ { "tables": ["minecraft:chests/stronghold_*"], "chance": 0.05, "pieces": ["helm", "bulwark"] } ],
  "bonuses": [
    { "pieces": 1, "per_piece": true, "modifiers": [ ... ] },
    { "pieces": 2, "modifiers": [ ... ] },
    { "pieces": 5, "modifiers": [ ... ], "procs": [ { "type": "reprisal", "chance": 0.25, "power": 0.3 } ] } ] }
```

Names come from the lang keys `<set name>` and `<set name>.<piece key>`. Any item can be a piece, modded ones included.
