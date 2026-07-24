# CJS Firearm Aiming Overhaul

A Project Zomboid B42.19 ZombieBuddy mod that turns the gap between a
firearm's effective-sight range and physical maximum range into an aim-time
tradeoff.

## Behavior

- Inside `MaxSightRange`, aim-time accumulation remains vanilla until full
  stabilization promotes the primary target to guaranteed-hit accuracy.
- Beyond `MaxSightRange`, stabilized hit and critical accuracy converge on the
  same optimal sight-band bonus available inside normal sight range instead
  of stopping at a merely penalty-free but still-wide spread.
- Recoverable accuracy penalties from movement history, arm pain, moodles,
  darkness/weather, and vision-restricting headgear become additional
  stabilization work beyond sight range. They delay full stabilization
  instead of imposing a permanent spread floor.
- Stabilization takes progressively longer for each tile beyond effective
  sight, reaching 4x normal time after 10 additional tiles by default.
- A firearm whose physical range ends sooner receives only the multiplier
  reached at that distance; a tiny sight-to-maximum gap is never forced to 4x.
- If live effective sight exceeds physical range because of Aiming skill,
  traits, or attachments, the same absolute-distance curve works in reverse:
  acquisition becomes progressively faster. Ten excess sight tiles produces
  one-quarter normal acquisition time with the defaults.
- Moving the reticle farther away reopens the crosshair according to the new
  required aim time. Moving closer keeps work already invested.
- Post-shot recovery uses the same distance scaling.
- Once stabilization work is completely finished, the first valid target gets
  100 hit chance. This makes the smallest crosshair a guaranteed damaging hit
  instead of leaving a hidden weapon-accuracy roll.
- Project Zomboid's ballistics controller already records the body part under
  the cursor. A damaging shot over the head therefore uses vanilla's targeted
  head-shot path; the mod does not approximate head position.
- Aiming skill, beneficial traits, vehicle firing limits, recoil, line
  obstruction, and maximum physical range remain active.

The default multiplier is:

```text
1 + 3 * min(1, (distance - sightRange) / 10) ^ 1.5
```

When sight exceeds physical range, the acquisition multiplier is the
reciprocal of the same curve:

```text
1 / (1 + 3 * min(1, (sightRange - physicalRange) / 10) ^ 1.5)
```

The maximum multiplier, curve exponent, and number of tiles needed to reach
the maximum distance multiplier are configurable under
**Sandbox > CJS Firearm Aiming Overhaul**. Recoverable condition penalties add
one unit of stabilization work for each hit-chance point they would normally
remove:

```text
total work = base aim time * distance multiplier + recoverable penalty points
```

The patch applies only to aimed firearms. Optics and Aiming skill naturally
move the boundary because the mod reads each live weapon's active
`MaxSightRange`.

## What the Crosshair Represents

With reticle mode 0, the four crosshair arms do not read `aimingDelay`
directly. Project Zomboid recalculates hit chance for every candidate target,
stores that chance on the closest valid target, and renders the crosshair
offset from it:

```text
offset = 5 + (maximum offset - 5) * (1 - hit chance / 100)
```

`aimingDelay` is one subtraction inside that hit-chance calculation. Vanilla
then also subtracts movement history, pain, moodles, weather/light, and
headgear penalties. When `aimingDelay` reaches zero those other terms remain,
which is why a crosshair can stop shrinking while still visibly wide.

Inside sight range, aim-time accumulation remains vanilla unless live sight
exceeds physical range, which activates the acquisition bonus. Beyond sight
range the mod uses the weapon's optimal sight-band distance for stabilized
accuracy, captures the exact recoverable penalties vanilla calculated for
the current target, removes those penalties from final hit chance, and adds
the same number of points to the stabilization work requirement.

When that work reaches zero, the primary `HitInfo.chance` is promoted to 100.
The reticle and the damaging-hit roll both read that same field, so smallest
spread and guaranteed damage cannot disagree. During hit processing, vanilla
asks `BallisticsController` for the cached body part beneath the cursor and
routes a head result through its targeted head-hit reactions and damage data.

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

## In-game verification

Enable `ZombieBuddy`, then `cjsFirearmAimingOverhaul`. With a firearm whose
maximum range exceeds its effective-sight range:

1. Aim at a zombie inside effective-sight range and confirm vanilla-speed
   stabilization.
2. Aim at zombies one, five, and ten tiles beyond effective sight and confirm
   the slowdown grows with absolute distance rather than the weapon's
   sight-to-maximum gap.
3. Confirm a stationary far target eventually reaches the same tightly
   stabilized spread as a comparable target near the middle of normal sight
   range.
4. Repeat while panicked, tired, hurt, recently moving, in low light, or
   wearing vision-restricting headgear. Confirm the far crosshair takes
   longer but still reaches the same weapon-and-skill ceiling.
5. Fully stabilize on a close zombie, move directly to a far zombie, and
   confirm the crosshair reopens.
6. Fire at long range and confirm recoil recovery follows the same slower
   timing.
7. Fully stabilize on a target and confirm the smallest crosshair always
   produces damage. Move the cursor over its head and confirm the targeted
   head-hit reaction.
8. Use a weapon whose effective sight exceeds physical range and confirm it
   stabilizes faster than normal, with a larger absolute surplus giving a
   larger bonus.
