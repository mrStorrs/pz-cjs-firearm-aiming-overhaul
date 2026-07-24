# CJS Firearm Aiming Overhaul

A Project Zomboid B42.19 ZombieBuddy mod that turns the gap between a
firearm's effective-sight range and physical maximum range into an aim-time
tradeoff.

## Behavior

- Inside `MaxSightRange`, stabilization and accuracy remain vanilla.
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
- Moving the reticle farther away reopens the crosshair according to the new
  required aim time. Moving closer keeps work already invested.
- Post-shot recovery uses the same distance scaling.
- Weapon accuracy, Aiming skill, beneficial traits, vehicle firing limits,
  recoil, and maximum physical range remain active. The overhaul does not
  make a low-skill weapon more accurate than it can be at its optimal normal
  sight distance.

The default multiplier is:

```text
1 + 3 * min(1, (distance - sightRange) / 10) ^ 1.5
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

Inside sight range this mod leaves the entire calculation vanilla. Beyond
sight range it uses the weapon's optimal sight-band distance for stabilized
accuracy, captures the exact recoverable penalties vanilla calculated for
the current target, removes those penalties from final hit chance, and adds
the same number of points to the stabilization work requirement. The reticle
and the actual shot continue to use the same hit-chance result.

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
