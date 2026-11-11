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
