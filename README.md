# CJS Firearm Aiming Overhaul

A Project Zomboid B42.20 ZombieBuddy mod that makes full stabilization
possible at every valid weapon range while balancing it with explicit
acquisition time.

## Behavior

- Every aimed firearm has a minimum target-lock time. Aiming 0 starts at 1.5
  seconds, Aiming 5 at 1.1 seconds, and Aiming 10 at 0.7 seconds. A weapon's
  vanilla aiming time still wins when it is longer.
- Changing to a Simple Bows weapon starts from that bow's own vanilla aiming
  time; it cannot inherit a shorter residual timer from a pistol.
- B42.20 ballistic bows report a near-hit distance even for distant targets.
  Their acquisition distance therefore uses the resolved ballistic hit point
  that B42.20 writes after camera-target selection, preserving the full
  far-range aiming curve even when the hit object is only a nearby proxy.
- Targets beyond effective sight follow a normalized clean-acquisition curve
  toward a skill-scaled ceiling: 4 seconds at Aiming 0, 3.5 seconds at
  Aiming 5, and 3 seconds at Aiming 10.
- The curve follows progress through that weapon's live sight-to-maximum gap,
  so a long rifle gap is not punished once per absolute tile. A small gap
  receives a softer maximum; a gap of ten tiles receives full weight.
- Recoverable penalties from recent movement, arm pain, moodles,
  darkness/weather, and vision-restricting headgear become additional
  stabilization time at every range instead of imposing a permanent spread
  floor. Forty combined penalty points reaches a skill-scaled cap: 4 seconds
  at Aiming 0, 3.25 seconds at Aiming 5, and 2.5 seconds at Aiming 10.
- Aiming at a different zombie retains `30% + 4% per Aiming level` of the
  progress earned on the previous target. Every change still requires at
  least 0.35 seconds of reacquisition.
- Recoil reopens at least `45% - 2% per Aiming level` of the crosshair.
  Vanilla recoil remains authoritative when it opens the crosshair farther.
- If effective sight exceeds physical maximum range, normal acquisition is
  accelerated by 2% per excess sight tile, capped at a 20% speed bonus.
- The resolved ballistic target's real hit chance grows toward 100 along a late-biased
  quadratic curve as stabilization work completes. This visibly tightens the
  crosshair throughout acquisition without over-rewarding a quick partial
  aim. Full stabilization still guarantees a damaging hit.
- Project Zomboid's ballistics controller records the body part under the
  cursor. A fully stabilized damaging shot through its resolved targeted
  head-shot path kills zombies and animals, including B42.20 bow shots whose
  body-part callback arrives after damage resolution. Players retain normal
  headshot damage.
- Simple Bows works because its bows are aimed firearms and use the same live
  sight, maximum-range, skill, target, and stabilization calculations.

Traits, weapon aiming time, vehicle aiming modifiers, line obstruction,
weapon range, and vanilla firing restrictions remain active. Marksman still
speeds the underlying vanilla work rate.

## Balance Model

The inside-range minimum is:

```text
minimum lock seconds = 1.5 - 0.08 * Aiming
base work = max(vanilla weapon work, minimum lock seconds converted to work)
```

The clean acquisition curve is:

```text
gap = physicalMaxRange - effectiveSightRange
progress = clamp((targetDistance - effectiveSightRange) / gap, 0, 1)
gapWeight = sqrt(min(1, gap / referenceGap))
maximumCleanSeconds = configuredMaximum * (1 - 0.025 * Aiming)
availableFarSeconds =
  max(0, maximumCleanSeconds - baseSeconds) * gapWeight
entrySeconds =
  min(availableFarSeconds, 0.5 - 0.02 * Aiming)

clean seconds =
  baseSeconds
  + entrySeconds
  + max(0, availableFarSeconds - entrySeconds)
    * progress ^ curveExponent
```

Inside effective sight, clean acquisition remains `baseSeconds`. Crossing
beyond sight adds the 0.3-to-0.5-second entry cost, then the progressive
portion grows toward the gap-weighted ceiling. A slow weapon whose vanilla
base already exceeds the configured ceiling keeps its vanilla time.

With the defaults (`configuredMaximum = 4`, `curveExponent = 1.25`,
`referenceGap = 10`), maximum-range clean acquisition for a fast weapon is:

| Live sight-to-maximum gap | Aiming 0 | Aiming 5 | Aiming 10 |
| ---: | ---: | ---: | ---: |
| 2 tiles | 2.62 s | 2.17 s | 1.73 s |
| 5 tiles | 3.27 s | 2.80 s | 2.33 s |
| 8 tiles | 3.74 s | 3.25 s | 2.76 s |
| 10+ tiles | 4.00 s | 3.50 s | 3.00 s |

The condition delay is:

```text
maximumConditionSeconds =
  configuredConditionMaximum * (1 - 0.0375 * Aiming)
conditionSeconds =
  maximumConditionSeconds * min(1, recoveredPenaltyPoints / 40)
```

| Recovered penalty points | Aiming 0 | Aiming 5 | Aiming 10 |
| ---: | ---: | ---: | ---: |
| 10 | +1.00 s | +0.81 s | +0.63 s |
| 25 | +2.50 s | +2.03 s | +1.56 s |
| 40 or more | +4.00 s | +3.25 s | +2.50 s |

At a full-weight maximum range, clean aim plus maximum conditions can
therefore reach 8 seconds at Aiming 0, 6.75 seconds at Aiming 5, and 5.5
seconds at Aiming 10. Firing before full lock remains possible; these totals
are the time required for the guaranteed fully stabilized shot.

When sight exceeds physical range:

```text
base acquisition multiplier = max(0.80, 1 - 0.02 * excessSightTiles)
```

Only base acquisition receives this speed bonus; condition work is not
discounted.

## What the Crosshair Represents

With reticle mode 0, Project Zomboid calculates hit chance for each candidate
target and renders the crosshair offset from the primary target's chance:

```text
offset = 5 + (maximum offset - 5) * (1 - hit chance / 100)
```

Vanilla `aimingDelay` is only one part of that chance. Movement, pain,
moodles, weather/light, and headgear can keep the crosshair wide after the
timer reaches zero. This mod captures those recoverable penalties for the
current target, removes their permanent accuracy subtraction, and converts
them into more required work.

The runtime temporarily supplies remaining work to vanilla's
`updateAimingDelay`, records the exact amount vanilla completed, and then
maps that progress back to the ordinary aiming-delay scale used by hit chance
and the reticle. This preserves Aiming and Marksman work-speed effects while
allowing a long balance timer without inflating weapon script values.

The primary target's resulting vanilla chance is also promoted toward the
guaranteed full-lock chance:

```text
progress = completed work / required work
promoted chance =
  vanilla chance + (100 - vanilla chance) * progress ^ 2
```

The quadratic curve keeps early partial shots close to vanilla accuracy while
making the crosshair visibly tighten as the target lock matures. A promotion
below complete progress is capped at 99, so only full stabilization—or a
natural vanilla 100—receives the mod's guarantee.

Full stabilization is captured when the shot starts, before recoil reopens the
crosshair. That pre-recoil snapshot also controls the shot's final hit-chance
promotion, so recoil cannot retroactively turn a fully stabilized shot into a
failed Damage Chance roll. If the successful hit then follows vanilla's explicit
cursor-targeted head path, the final damage is raised to the target's remaining
health for zombies and animals. The normal `Hit` and `hitConsequences` flow
still handles damage, death animation, kill credit, XP, and other hooks.
Random critical head reactions never enter this path. The Damage Chance fix
reroutes failed-roll head targeting before this mod records the body part, so a
reduced-damage graze cannot become lethal.

Target identity comes from the primary `HitInfo` object's stable moving-object
ID. The lock is synchronized only after B42.20 updates the on-screen reticle,
so transient combat calculations cannot clear the target and reset aiming.
A completed base lock immediately reopens when the same zombie is resolved at
a farther distance. Changing zombies applies the retention and
minimum-reacquisition rules.

After a shot, vanilla first adds its recoil and aiming delay. The mod converts
that delay back into completion progress and then enforces the skill-scaled
minimum reopening. The larger reopening wins.

## Sandbox Settings

Four balance controls and one diagnostic toggle are under
**Sandbox > CJS Firearm Aiming Overhaul**:

- **Maximum Clean Aim Seconds** defaults to 4.
- **Maximum Condition Seconds** defaults to 4.
- **Far-Range Progress Curve** defaults to 1.25.
- **Reference Range Gap** defaults to 10 tiles.
- **Headshot Diagnostic Logging** defaults to enabled.

Version 1.6 replaces the old maximum-extra-seconds option with the explicit
clean and condition maximums. If a save has not stored the new options yet,
the runtime uses the new four-second defaults.

Headshot diagnostics write a compact sequence for each shot to
`/home/cjstorrs/games/Project Zomboid Linux 42.20.0/user-data/Zomboid/console.txt`
with the prefix
`[cjsFirearmAimingOverhaul][headshot-debug]`. The sequence reports captured
stabilization, Project Zomboid's targeted body part, the final damage decision,
the pre-recoil and promoted hit chances, and a shot summary. Disable the sandbox
toggle after collecting the needed shots.

## Build

The build requires Java 17 or newer, a local ZombieBuddy JAR, and the B42.20
Project Zomboid JAR. Compile-only API stubs bridge the game's newer Java
bytecode to the Java 17 patch target; the stubs are not packaged.

```bash
./build.sh
```

The tracked runtime JAR is written to
`42/media/java/CJSFirearmAimingOverhaul.jar`. The build runs behavior and
patch-discovery tests, then verifies every referenced method against the real
B42.20 game JAR under Project Zomboid's bundled Java runtime.

## In-Game Verification

Enable `ZombieBuddy`, then `cjsFirearmAimingOverhaul`.

1. Compare Aiming 0, 5, and 10 with a fast pistol inside effective sight.
   Confirm acquisition follows the new minimum times.
2. Aim at targets near effective sight, halfway through the live range gap,
   and at physical maximum. Confirm the far surcharge grows smoothly.
3. Compare a rifle with a large sight-to-maximum gap and a weapon with a
   two-to-five-tile gap. Confirm both use progress through their own gap while
   the small gap receives the softer maximum.
4. Repeat while panicked, tired, hurt, recently moving, in low light, and
   wearing vision-restricting headgear. Confirm the crosshair takes longer
   but still reaches full stabilization.
5. Fully stabilize on one zombie, move to another at the same distance, and
   confirm the crosshair reopens. Move closer or farther on the same zombie
   and confirm invested work is retained.
6. Fire after full stabilization and confirm recoil visibly reopens the
   crosshair, with Aiming skill reducing but not eliminating recovery.
7. Confirm the crosshair tightens throughout acquisition and a fully
   stabilized shot always damages the current primary target.
8. Fully stabilize with the cursor over a zombie's head and confirm the hit
   kills it. Repeat against an animal, then confirm partial-lock headshots and
   player headshots retain normal damage.
9. Repeat the range, target-change, and lethal targeted-headshot checks with a
   Simple Bows bow.
