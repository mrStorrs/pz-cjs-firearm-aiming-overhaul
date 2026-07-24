# CJS Firearm Aiming Overhaul

A Project Zomboid B42.19 ZombieBuddy mod that turns the gap between a
firearm's effective-sight range and physical maximum range into an aim-time
tradeoff.

## Behavior

- Inside `MaxSightRange`, stabilization and accuracy remain vanilla.
- Beyond `MaxSightRange`, the permanent distance accuracy penalty is removed.
- Stabilization takes progressively longer toward the firearm's actual
  `MaxRange`, reaching 4x normal time at maximum range by default.
- Moving the reticle farther away reopens the crosshair according to the new
  required aim time. Moving closer keeps work already invested.
- Post-shot recovery uses the same distance scaling.
- Panic, pain, movement, darkness, weather, skill, traits, recoil, and other
  vanilla modifiers remain active. Maximum physical range is unchanged.

The default multiplier is:

```text
1 + 3 * ((distance - sightRange) / (maxRange - sightRange)) ^ 1.5
```

The maximum multiplier and curve exponent are configurable under
**Sandbox > CJS Firearm Aiming Overhaul**.

The patch applies only to aimed firearms. Optics and Aiming skill naturally
move the boundary because the mod reads each live weapon's active
`MaxSightRange`.

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
2. Aim at a zombie near maximum range and confirm the crosshair eventually
   reaches full stabilization, but substantially more slowly.
3. Fully stabilize on a close zombie, move directly to a far zombie, and
   confirm the crosshair reopens.
4. Fire at long range and confirm recoil recovery follows the same slower
   timing.
