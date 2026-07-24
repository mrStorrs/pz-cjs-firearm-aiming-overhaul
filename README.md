# CJS Firearm Aiming Overhaul

A Project Zomboid B42.19 ZombieBuddy mod that makes full stabilization
possible at every valid weapon range while balancing it with explicit
acquisition time.

## Behavior

- Every aimed firearm has a minimum target-lock time. Aiming 0 starts at 1.5
  seconds, Aiming 5 at 1.1 seconds, and Aiming 10 at 0.7 seconds. A weapon's
  vanilla aiming time still wins when it is longer.
- Targets beyond effective sight add a normalized far-range surcharge. The
  surcharge follows progress through that weapon's live sight-to-maximum
  gap, so a long rifle gap is not punished once per absolute tile.
- A small sight-to-maximum gap receives a softer maximum surcharge. A gap of
  ten tiles receives full weight; larger gaps use the same normalized curve.
- Recoverable penalties from recent movement, arm pain, moodles,
  darkness/weather, and vision-restricting headgear become additional
  stabilization time at every range instead of imposing a permanent spread
  floor.
- Aiming at a different zombie retains `30% + 4% per Aiming level` of the
  progress earned on the previous target. Every change still requires at
  least 0.35 seconds of reacquisition.
- Recoil reopens at least `45% - 2% per Aiming level` of the crosshair.
  Vanilla recoil remains authoritative when it opens the crosshair farther.
- If effective sight exceeds physical maximum range, normal acquisition is
  accelerated by 2% per excess sight tile, capped at a 20% speed bonus.
- Once all work for the current target is complete, its primary hit chance is
  promoted to 100. The smallest crosshair therefore guarantees a damaging
  hit rather than hiding another accuracy roll.
- Project Zomboid's ballistics controller already records the body part under
  the cursor. A damaging shot over the head uses vanilla's targeted head-shot
  path.
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

The far-range surcharge is:

```text
gap = physicalMaxRange - effectiveSightRange
progress = clamp((targetDistance - effectiveSightRange) / gap, 0, 1)
gapWeight = sqrt(min(1, gap / referenceGap))

far surcharge seconds =
  (1 + maximumExtraSeconds * gapWeight * progress ^ curveExponent)
  * (1.25 - 0.045 * Aiming)
```

With the defaults (`maximumExtraSeconds = 5`, `curveExponent = 1.25`,
`referenceGap = 10`), maximum-range results are:

| Live sight-to-maximum gap | Raw maximum surcharge | Aiming 0 | Aiming 10 |
| ---: | ---: | ---: | ---: |
| 2 tiles | 3.24 s | 4.05 s | 2.59 s |
| 5 tiles | 4.54 s | 5.67 s | 3.63 s |
| 8 tiles | 5.47 s | 6.83 s | 4.37 s |
| 10+ tiles | 6.00 s | 7.50 s | 4.80 s |

Those values are surcharges on top of normal acquisition. For a fast weapon
at a full-weight maximum range, total lock time is therefore approximately
9.0 seconds at Aiming 0 and 5.5 seconds at Aiming 10. A slow weapon can take
longer.

Each recovered accuracy point adds:

```text
seconds per point = 0.04 - 0.0015 * Aiming
```

Twenty-five condition-penalty points therefore add 1.0 second at Aiming 0,
0.81 seconds at Aiming 5, and 0.625 seconds at Aiming 10.

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

Target identity comes from the primary `HitInfo` object's stable moving-object
ID. Changing distance on the same zombie preserves absolute work; changing
zombies applies the retention and minimum-reacquisition rules.

After a shot, vanilla first adds its recoil and aiming delay. The mod converts
that delay back into completion progress and then enforces the skill-scaled
minimum reopening. The larger reopening wins.

## Sandbox Settings

The three balance controls are under
**Sandbox > CJS Firearm Aiming Overhaul**:

- **Maximum Far-Aim Extra Seconds** defaults to 5.
- **Far-Range Progress Curve** defaults to 1.25.
- **Reference Range Gap** defaults to 10 tiles.

The internal option IDs are unchanged from v1.4 so existing saves remain
compatible. Existing saves also retain their previously stored numeric
values; the new recommended values can be selected in the sandbox settings.

## Build

The build requires Java 17 or newer, a local ZombieBuddy JAR, and the B42.19
Project Zomboid JAR. Compile-only API stubs bridge the game's newer Java
bytecode to the Java 17 patch target; the stubs are not packaged.

```bash
./build.sh
```

The tracked runtime JAR is written to
`42/media/java/CJSFirearmAimingOverhaul.jar`. The build runs behavior and
patch-discovery tests, then verifies every referenced method against the real
B42.19 game JAR under Project Zomboid's bundled Java runtime.

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
7. Confirm a fully stabilized shot always damages the current primary target,
   and that putting the cursor over its head uses the targeted head-hit path.
8. Repeat the range and target-change checks with a Simple Bows bow.
