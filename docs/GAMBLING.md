# The Mountebank

A new villager profession. An unemployed villager takes the job at a **Mountebank's Coffer**
(crafted: iron nugget top, iron nuggets either side of a barrel, gold ingot below). He sells sealed **parcels**, buys
Smith's Scrap (6 for an emerald), and buys unwanted set pieces through his coffer: sneak and right-click a Mountebank's Coffer holding one (he doesn't
have to be there). Each piece (config
`Sets.TradeInPieces`) earns a **Mountebank's Token**, and two tokens buy a **Sworn Parcel**, which always holds a set
piece (60 % of the time one you're missing; see [SETS.md](SETS.md)). Every Mountebank offers the Sworn Parcel from
Apprentice level on. A parcel's contents are decided the moment you open it (use it): a Rotten sword that
bites you every time you strike the undead, or a helmet whose stats run past their normal limits.

| Level | Parcel | Price | Holds | Grade luck | Curse | Exceptional | Set piece |
|---|---|---|---|---|---|---|---|
| Novice | Peddler's Bundle | 3-6 emeralds | anything | -1 | 25 % | 3 % | - |
| Apprentice | Bundle of Arms | 6-10 | tools, weapons, bows | 0 | 20 % | 5 % | - |
| Apprentice | Bundle of Harness | 6-10 | armor, shields | 0 | 20 % | 5 % | - |
| Apprentice (always) | Sworn Parcel | 2 tokens | a set piece | - | 0 | 0 | 100 % |
| Journeyman | Trinket Casket | 10-16 | baubles | +0.5 | 20 % | 8 % | - |
| Journeyman | Sealed Crate | 10-16 | anything | +0.5 | 20 % | 8 % | 2 % |
| Expert | Banded Strongbox | 18-28 + 2 gold | anything | +1.5 | 20 % | 12 % | 5 % |
| Master | Nameless Reliquary | 32-48 + 1 diamond | anything | +3 | 30 % | 20 % | 12 % |

Each villager level offers two parcels picked from that level's list, weighted; a parcel with `"always": true` is
guaranteed one of the two slots at its level. `"currency"` sets what the price is paid in (default emerald).

* **Grades** roll with the grades' `"gamble"` weights (Shoddy 14, Common 30, Journeyman 30, Masterwork 20,
  Gearwrought 6) plus the parcel's luck. Material tier caps still apply. A parcel item is never Unappraised: the parcel
  was the mystery. Uniques have a 2 % chance from parcels.
* **Cursed:** one curse affix, chosen from those that fit the item. Curses only come from parcels.
* **Exceptional:** the item is at least Journeyman, and every prefix and suffix rolls 15-35 % past its normal maximum.
  The tooltip says so. A piece can be both cursed and exceptional.
* **Contents** come from the `hallmarked:gamble/pool` item tag: vanilla gear plus the Emerald Ring and Amulet by default,
  so craft-only modded gear never turns up in a parcel. Add your own items to the tag.

## The Mountebank's Coffer
The coffer is worth placing even without a Mountebank:

* **Locked storage.** Right-click to open 27 slots. Only the player who placed it can open it (creative players too);
  anyone else hears the lock. Non-owners can't break it, explosions don't touch it, and hoppers can't reach into it.
  Breaking it (as its owner) drops its contents.
* **Fence.** Sneak and right-click it holding gear to sell:
  * **Set pieces** earn a Mountebank's Token each.
  * **Found graded gear** (from loot, mobs or parcels) earns emeralds by grade: Shoddy 1, Common 1, Journeyman 2,
    Masterwork 4, Gearwrought 8, Unique 12 (the grade's `sell` key). +1 when the quality is all upside; a worn piece
    pays down to half.
  * **Crafted gear** is refused ("He won't buy what you made yourself"), so a pile of wooden swords isn't an emerald
    printer. Gear graded before 1.19 isn't marked as found and can't be sold.

## Curses

| Curse | On | Effect |
|---|---|---|
| Rotten | weapons, bows | hitting the undead wounds you for 15-40 % of the damage dealt |
| Leaden | tools; armor | -8 to -18 % attack speed; -3 to -7 % movement speed per piece |
| Ill-omened | anything | -0.5 to -1.5 luck |
| Hungering | weapons, tools, bows | every hit drains hunger |
| Festering | armor, shields | 15-30 % chance to take 25-50 % more damage when struck |
| Wasting | armor; baubles | -1 to -3 max health; -2 to -4 |

Curses are affixes with `"type": "curse"`. Procs take an optional `"target"` entity tag, so you can write a curse
(or a blessing) that only triggers against, say, `#minecraft:arthropod`.

## Parcel format
`data/<namespace>/parcels/*.json`:

```json
{ "level": 4, "weight": 10, "color": "yellow", "price": { "min": 18, "max": 28 },
  "second_cost": { "item": "minecraft:gold_ingot", "count": 2 },
  "categories": ["tool", "bow", "shield", "armor", "bauble"],
  "luck": 1.5, "quality_chance": 0.6, "curse_chance": 0.2, "exceptional_chance": 0.12, "set_chance": 0.05,
  "max_uses": 3, "xp": 15 }
```

Test with `/hallmarked parcel give <parcel> [count]`.
