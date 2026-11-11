# Named sets

Each piece is an ordinary item dressed in the set's look (leather dye, armor trim, shield
colour), with its own stats; wearing more pieces unlocks bonuses. Pieces count only where they belong: armor in its
slot, the weapon in your main hand, the shield in either hand, the phylactery in a Curios slot. Set pieces are a green
**Set** grade: 1.5x durability, a loot beam, one inlay slot.

Fourteen sets ship by default. Pieces mix their looks freely (a leather hood over a chainmail body and plate boots,
diamond with leather), and ten of them carry diamond or netherite base stats whatever they look like. A piece is repaired
with the material it looks like: chainmail and iron with iron ingots, leather with leather, gold with gold.

| Set | Looks like | Stats | Bonuses (pieces: what) | Found in |
|---|---|---|---|---|
| **Harness of the Ashwalker** | leather helmet, chainmail chestplate, iron leggings, leather boots, iron sword | netherite (fire-proof) | 2: stats; 3: ignite; 4: fire resistance; 5: stats, reprisal | nether bridge 5 %; bastion other, bastion hoglin stable 3 %; ruined portal 3 % |
| **Mail of the Blackguard** | leather helmet, chainmail chestplate, chainmail leggings, iron boots, iron axe | netherite (fire-proof) | 2: stats; 3: leech; 4: stats; 5: stats, reaper, bleed | nether bridge 4 %; bastion treasure 6 %; bastion other, bastion bridge 3 % |
| **Kit of the Deep Delver** | leather helmet, iron chestplate, leather leggings, iron boots, iron pickaxe | diamond | 2: night vision; 3: stats; 4: stats, haste; 5: stats, haste II | abandoned mineshaft 5 %; simple dungeon 3 %; ancient city 3 % |
| **Harness of the Free Lance** | iron helmet, chainmail chestplate, leather leggings, iron boots, iron sword | its own items | 2: stats; 3: stats; 4: stats; 5: cleave, bleed | village weaponsmith, village armorer 5 %; pillager outpost 6 %; trial chambers/reward* 5 %; bastion other, bastion bridge 3 % |
| **Vigil of the Frostwarden** | leather helmet, chainmail chestplate, leather leggings, leather boots, iron sword | diamond | 2: stats; 3: stats; 4: stats, stagger; 5: stats, stagger | igloo chest 6 %; ancient city ice box 6 %; village snowy house 4 % |
| **Finery of the Gilded Duelist** | golden helmet, leather chestplate, leather leggings, golden boots, golden sword | diamond | 2: stats; 3: stats; 4: stats, leech; 5: stats, reprisal | bastion treasure 4 %; woodland mansion 3 %; end city treasure 3 %; village weaponsmith 1 % |
| **Coat of the Lamplighter** | leather helmet, iron chestplate, leather leggings, chainmail boots, emerald ring | diamond | 2: night vision; 3: ignite vs undead; 4: stats; 5: stats, reaper | stronghold corridor 3 %; simple dungeon 4 %; village temple 3 %; ancient city 2 % |
| **Regalia of the Oathsworn** | iron helmet, iron chestplate, chainmail leggings, iron boots, shield | its own items | 2: stats; 3: stats; 4: stats; 5: stats, reprisal | stronghold corridor, stronghold crossing 5 %; end city treasure 6 %; desert pyramid, jungle temple 3 %; village temple 3 %; village armorer 2 % |
| **War-Gear of the Reaver** | diamond helmet, leather chestplate, leather leggings, diamond boots, diamond axe | netherite | 2: stats; 3: leech; 4: stats, cleave; 5: stats, reaper, bleed | pillager outpost 3 %; woodland mansion 3 %; end city treasure 4 %; bastion treasure 3 % |
| **Gear of the Salvage Diver** | turtle helmet, leather chestplate, chainmail leggings, iron boots, iron pickaxe | diamond | 2: water breathing; 3: stats; 4: night vision, dolphins grace; 5: stats, conduit power | underwater ruin big 5 %; underwater ruin small 4 %; shipwreck treasure 5 %; shipwreck supply 3 %; buried treasure 4 % |
| **Leathers of the Silent Knife** | leather helmet, leather chestplate, chainmail leggings, leather boots, iron sword | diamond | 2: stats; 3: stats, night vision; 4: stats; 5: stats, bleed | woodland mansion 5 %; buried treasure 6 %; end city treasure 3 %; desert pyramid 3 % |
| **Plate of the Stonewardens** | iron helmet, iron chestplate, chainmail leggings, iron boots, iron axe | diamond | 2: stats; 3: stats; 4: stats; 5: stats, stagger, reprisal | abandoned mineshaft 3 %; stronghold corridor, stronghold crossing 3 %; ancient city 5 %; trial chambers/reward* 3 % |
| **Vestments of the Thaumaturge** | golden helmet, leather chestplate, leather leggings, leather boots, emerald amulet | its own items | 2: stats; 3: stats; 4: stats; 5: stats | woodland mansion 8 %; stronghold library 8 %; ancient city 5 %; igloo chest 4 %; village temple, village cartographer 3 %; village tannery 3 % |
| **Garb of the Wayfarer** | leather helmet, chainmail chestplate, leather leggings, chainmail boots, bow | its own items | per piece: stats; 2: stats; 3: stats; 4: stats; 5: stats | village fletcher 5 %; village tannery 5 %; pillager outpost 5 %; jungle temple 5 %; shipwreck supply 2 %; trial chambers/reward* 3 % |

Bonuses stack: four pieces give every bonus up to 4. "stats" means attribute bonuses (damage, armor, speed, crit, spell
power and so on: Shift on a piece shows them). Effects such as Fire Resistance stay on while the bonus is active and fade
a few seconds after a piece comes off; a stronger potion of the same effect is left alone.

* **Lava:** the Ashwalker's four-piece bonus keeps Fire Resistance on, so you can swim in lava for as long as you wear
  it, and every piece survives fire and lava as an item.
* **Underwater mining:** the Salvage Diver gives Water Breathing at two pieces, near-normal mining speed underwater at
  three, Night Vision and Dolphin's Grace at four and Conduit Power at five.

Magic and ranged stats use Iron's Spellbooks, Lodestone, Caverns & Chasms and Apothic Attributes when installed; a stat
whose mod is missing is simply left out, so every set still works in a pack without them.

**Where they come from:**
* **Fitting places.** Each set lists loot tables where it belongs (the table above), and a chest there may hold an
  extra piece. The chance scales with luck: x(1 + LuckFactor x luck), counting the table's loot-odds luck (village chests -2, late
  tables +1) and the looter's Luck, never below a quarter of the listed chance.
* **Any matching drop.** When gear is graded from loot (0.4 % per matching item) or mob gear (0.1 %), an item that is a
  set piece's base item may become that piece.
* **Mountebank parcels** at Journeyman and above, and the token-bought Sworn Parcel (always a set piece).

**Smart drops.** When a set piece drops for a player (a chest they open, a parcel they buy), there's a 60 % chance
(config `Sets.SmartDropChance`) that it's a piece they're missing from a set they've already started: one they own at
least one piece of (inventory, armor, ender chest, Curios) but haven't finished, preferring the set the chest belongs to.
Unwanted pieces can be traded to a Mountebank for tokens ([GAMBLING.md](GAMBLING.md)).

Simulated five-day playthroughs (Overworld and some Nether, no End), chance to have a full set by the end:

| Player | Before smart drops | With smart drops |
|---|---|---|
| Casual, ~7 hours | ~0 % | 1 % (5 % reach 4 pieces) |
| Average, ~12 hours | ~0 % | 10 % (25 % reach 4 pieces) |
| Explorer, ~18 hours | 0.1 % | 30 % (55 % reach 4 pieces) |
`/hallmarked sets give <set> [piece]` gives one for testing.

## Format
`data/<namespace>/sets/*.json`:

```json
{ "color": "aqua", "chance": { "loot": 0.004, "mob": 0.001 },
  "pieces": [
    { "key": "hood", "item": "minecraft:leather_helmet", "base_stats": { "armor": 3, "toughness": 3, "knockback_resistance": 0.1 },
      "durability": 407, "fire_resistant": true },
    { "key": "helm", "item": "minecraft:iron_helmet", "trim": { "pattern": "minecraft:ward", "material": "minecraft:gold" },
      "dye": "#3a2458", "base_color": "blue", "glint": false,
      "modifiers": [ { "name": "my_helm_toughness", "attribute": "minecraft:generic.armor_toughness", "operation": "addition", "value": 1 } ] } ],
  "tables": [ { "tables": ["minecraft:chests/stronghold_*"], "chance": 0.05, "pieces": ["helm", "bulwark"] } ],
  "bonuses": [
    { "pieces": 1, "per_piece": true, "modifiers": [ ... ] },
    { "pieces": 4, "effects": [ { "effect": "minecraft:fire_resistance", "amplifier": 0 } ] },
    { "pieces": 2, "modifiers": [ ... ] },
    { "pieces": 5, "modifiers": [ ... ], "procs": [ { "type": "reprisal", "chance": 0.25, "power": 0.3 } ] } ] }
```

Names come from the lang keys `<set name>` and `<set name>.<piece key>`. Any item can be a piece, modded ones included.
