# Procs and mod-gated affixes

## Procs
Affixes can carry `procs`: effects that trigger in combat. Their chance and power scale with the affix's 0–1 roll.
* **Who triggers them:** players *and* mobs wearing the gear. Turn mobs off with `Procs.MobsUseProcs`.
* **Where they count:** only in a slot where the affix actually applies. A sword's procs work in the main hand, not
  in the off hand.
* **Unappraised gear:** procs stay dormant until the item is appraised.
* **No chains:** proc damage (cleave, reprisal) can't trigger further procs.
* **Master switch:** `Procs.Enabled` in `hallmarked-common.toml`.

| Proc type | Trigger | Effect | Default affix |
|---|---|---|---|
| `bleed` | hit | Bleeding: armor-ignoring damage over time (`power` = damage/s, `duration` ticks) | *of Bleeding* (weapons, bows) |
| `stagger` | hit | Slowness II + Weakness for `duration` | *Staggering* |
| `leech` | hit | Heal `power` × damage dealt | *Leeching* |
| `ignite` | hit | Set the target alight for `power` seconds | *of Embers* (weapons, bows) |
| `cleave` | melee hit | `power` × damage to foes within 2.5 blocks | *Cleaving* |
| `reprisal` | when hurt | Reflect `power` × damage taken to the attacker | *of Reprisal* (chest, shield) |
| `reaper` | kill | Heal `power` | *of the Reaper* |
| `anima` | kill | `power` LP to your NeoVitae soul network (**needs NeoVitae**) | *of the Anima* |

Two new fusions: Cleaving + Staggering → *Butcher's*, and Leeching + of the Reaper → *Grave-Fed*.

```json
{ "name": "hallmarked.affix.of_bleeding", "type": "suffix", "weight": 6, "group": "bleed",
  "tool": { "modifiers": [] }, "bow": { "modifiers": [] },
  "procs": [ { "type": "bleed", "chance": { "min": 0.08, "max": 0.2 }, "power": { "min": 0.5, "max": 1.5 }, "duration": 80 } ] }
```

## Mod-gated content (on/off by installed mods)
Nothing that depends on another mod can reach an item unless that mod is installed:
* **`"requires_mods": ["modid", ...]`:** works on any qualities, grades, affixes, fusions, inlays, uniques, regions or
  tempering entry. If a listed mod is missing, the entry is skipped quietly at load (the log gives a count) and never rolls.
* **Unregistered attributes:** an affix whose attribute isn't registered (e.g. `irons_spellbooks:spell_power` without
  Iron's Spellbooks) can't load.
* **Mod-dependent proc types:** a proc type that needs a mod (`anima` → NeoVitae) is unavailable without it, so any
  affix using it can't load.
* **No hard dependencies:** compat code is reflective, with nothing to install.

Bundled mod-gated affixes:

| Affix | Needs | Effect |
|---|---|---|
| *of the Anima* | NeoVitae | On kill: 25–150 LP |
| *Runed* | Iron's Spellbooks | +3–8 % spell power (baubles), +2–6 % (helmets) |
| *of the Well* | Iron's Spellbooks | +20–60 max mana (baubles), +15–40 (chestplates) |
| *Thaumic* | Lodestone | +0.5–1.5 magic proficiency (baubles) |
| *Exacting* | Apothic Attributes | +3–10 % crit chance (tools, bows) |
| *of Ruin* | Apothic Attributes | +10–35 % crit damage (tools, bows) |
| *Bodkin* | Apothic Attributes | +1–4 armor pierce (tools, bows) |
| *Rending* | Apothic Attributes | +3–10 % armor shred (tools); shares a group with *Bodkin* |
| *of the Breach* | Apothic Attributes | +1–4 protection pierce (tools) |
| *of the Headsman* | Apothic Attributes | +1–4 % of the target's current health as damage (tools) |
| *Searing* / *Rimed* | Apothic Attributes | +1–3 fire / cold damage (tools); one or the other |
| *Supple* | Apothic Attributes | +5–20 % draw speed (bows) |
| *of Distance* | Apothic Attributes | +8–25 % arrow velocity (bows) |
| *Elusive* | Apothic Attributes | +1–3 % dodge per armor piece, +2–5 % on baubles |
| *of Convalescence* | Apothic Attributes | +3–8 % healing received per armor piece, +5–15 % on baubles |
| *of the Scholar* | Apothic Attributes | +5–20 % experience (tools), +10–30 % (baubles) |
| *Salt-lined* | Caverns & Chasms | +3–8 % magic protection per armor piece |
| *Hushed* | Caverns & Chasms | +3–8 % stealth per armor piece, +5–15 % on baubles |
| *Hexed* | Lodestone | +1–3 magic damage (tools) |
| *Chalk-scribed* | Lodestone | +3–10 % magic resistance per armor piece |
| *Veinseeking* | FTB Ultimine | +4–16 blocks per ultimine (tools) |

Gated fusions: *Assassin's* (Exacting + of Ruin), *of the Siege* (Bodkin + of the Breach),
*of the Longbowman* (Supple + of Distance), *Cutpurse's* (Soft-soled + Hushed).

## New vanilla-attribute affixes (always available)

| Affix | Effect |
|---|---|
| *Broad-bladed* | +0.10–0.35 sweeping damage ratio (tools) |
| *Mason's* | +1–5 mining efficiency (tools) |
| *Soft-soled* | +3–10 % sneaking speed per armor piece |
| *Sapper's* | +8–25 % explosion knockback resistance per armor piece |
| *of the Deep* | Oxygen and water movement per armor piece; helmets get more oxygen and underwater mining speed instead |
| *of the Salamander* | 10–30 % shorter burning per armor piece |
| *of Sure Footing* | +0.5–1.5 safe fall distance per armor piece; boots get +1–4 and movement efficiency instead |

Fusion: *of the Diver* (of the Deep + of Sure Footing).

A piece-specific section (`helmet`, `chestplate`, `leggings`, `boots`) now wins over a generic `armor` section in the same file.

