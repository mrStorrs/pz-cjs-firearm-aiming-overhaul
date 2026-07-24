package com.cjstorrs.firearmaimingoverhaul;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import me.zed_0xff.zombie_buddy.Patch;
import me.zed_0xff.zombie_buddy.PatchEngine;
import zombie.SandboxOptions;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoMovingObject;
import zombie.network.fields.hit.HitInfo;

public final class FirearmAimingPatchTest {
    private FirearmAimingPatchTest() {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        testHybridCurveDefaultsAndConfiguration();
        testHybridCurveNormalizesWeaponGap();
        testSkillScaledMinimumLockTime();
        testFarAimAddsSecondsInsteadOfMultiplyingTinyTimers();
        testExcessSightBonusIsCapped();
        testConditionsBecomeAimTimeAtEveryRange();
        testTargetChangesRequireReacquisition();
        testDistanceChangesOnSameTargetPreserveWork();
        testRecoilReopensMinimumSpread();
        testFullyStabilizedTargetIsGuaranteedToTakeDamage();
        testAccuracyChangesAreScopedToValidTargets();
        testPatchMetadata();
        testZombieBuddyDiscovery();
        System.out.println("FirearmAimingPatchTest: PASS");
    }

    private static void testHybridCurveDefaultsAndConfiguration() {
        resetRuntime();
        checkClose(
            5.0F,
            FirearmAimSettings.getMaximumFarExtraSeconds(),
            "default maximum far extra seconds"
        );
        checkClose(
            1.25F,
            FirearmAimSettings.getFarProgressExponent(),
            "default far progress exponent"
        );
        checkClose(
            10.0F,
            FirearmAimSettings.getReferenceGapTiles(),
            "default reference gap"
        );

        SandboxOptions.instance.setOptionForTest(
            "CJSFirearmAimingOverhaul.MaximumAimTimeMultiplier",
            new SandboxOptions.DoubleSandboxOption(6.0)
        );
        SandboxOptions.instance.setOptionForTest(
            "CJSFirearmAimingOverhaul.CurveExponent",
            new SandboxOptions.DoubleSandboxOption(2.0)
        );
        SandboxOptions.instance.setOptionForTest(
            "CJSFirearmAimingOverhaul.FullPenaltyDistanceTiles",
            new SandboxOptions.DoubleSandboxOption(5.0)
        );
        checkClose(6.0F, FirearmAimSettings.getMaximumFarExtraSeconds(), "configured far seconds");
        checkClose(2.0F, FirearmAimSettings.getFarProgressExponent(), "configured exponent");
        checkClose(5.0F, FirearmAimSettings.getReferenceGapTiles(), "configured reference gap");
    }

    private static void testHybridCurveNormalizesWeaponGap() {
        resetRuntime();

        checkClose(
            3.8778F,
            FirearmAimRuntime.calculateFarAimSeconds(0, 11.0F, 6.0F, 16.0F),
            "halfway through a ten-tile gap"
        );
        checkClose(
            3.8778F,
            FirearmAimRuntime.calculateFarAimSeconds(0, 16.0F, 6.0F, 26.0F),
            "halfway through a twenty-tile rifle gap"
        );
        checkClose(
            4.0451F,
            FirearmAimRuntime.calculateFarAimSeconds(0, 8.0F, 6.0F, 8.0F),
            "maximum of a two-tile gap"
        );
        checkClose(
            7.5F,
            FirearmAimRuntime.calculateFarAimSeconds(0, 16.0F, 6.0F, 16.0F),
            "maximum of a ten-tile gap at aiming zero"
        );
        checkClose(
            4.8F,
            FirearmAimRuntime.calculateFarAimSeconds(10, 16.0F, 6.0F, 16.0F),
            "maximum of a ten-tile gap at aiming ten"
        );
    }

    private static void testSkillScaledMinimumLockTime() {
        resetRuntime();
        checkClose(1.5F, FirearmAimRuntime.calculateMinimumLockSeconds(0), "aiming zero floor");
        checkClose(1.1F, FirearmAimRuntime.calculateMinimumLockSeconds(5), "aiming five floor");
        checkClose(0.7F, FirearmAimRuntime.calculateMinimumLockSeconds(10), "aiming ten floor");

        IsoPlayer player = createPlayer(15.0F, 0);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();
        setTarget(player, 5.0F, new IsoMovingObject(1));
        checkClose(
            56.25F,
            FirearmAimRuntime.calculateRequiredAimWork(player, weapon),
            "fast pistol work floor at aiming zero"
        );

        resetRuntime();
        player = createPlayer(15.0F, 10);
        weapon = (HandWeapon)player.getPrimaryHandItem();
        setTarget(player, 5.0F, new IsoMovingObject(1));
        checkClose(
            39.375F,
            FirearmAimRuntime.calculateRequiredAimWork(player, weapon),
            "fast pistol work floor at aiming ten"
        );

        resetRuntime();
        player = createPlayer(75.0F, 10);
        weapon = (HandWeapon)player.getPrimaryHandItem();
        weapon.setAimingTime(75);
        setTarget(player, 5.0F, new IsoMovingObject(1));
        checkClose(
            75.0F,
            FirearmAimRuntime.calculateRequiredAimWork(player, weapon),
            "a slower weapon keeps its longer vanilla time"
        );
    }

    private static void testFarAimAddsSecondsInsteadOfMultiplyingTinyTimers() {
        resetRuntime();
        IsoPlayer player = createPlayer(15.0F, 0);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();
        weapon.setMaxRange(16.0F);
        setTarget(player, 16.0F, new IsoMovingObject(1));

        checkClose(
            337.5F,
            FirearmAimRuntime.calculateRequiredAimWork(player, weapon),
            "aiming-zero maximum-range work"
        );
        runAimingUpdate(player, 56.25F);
        check(player.getAimingDelay() > 0.0F, "inside-range floor alone must not finish far aim");
        runAimingUpdate(player, 281.25F);
        checkClose(0.0F, player.getAimingDelay(), "maximum-range aim must eventually finish");

        resetRuntime();
        player = createPlayer(15.0F, 10);
        weapon = (HandWeapon)player.getPrimaryHandItem();
        weapon.setMaxRange(16.0F);
        setTarget(player, 16.0F, new IsoMovingObject(1));
        checkClose(
            309.375F,
            FirearmAimRuntime.calculateRequiredAimWork(player, weapon),
            "aiming-ten maximum-range work"
        );
    }

    private static void testExcessSightBonusIsCapped() {
        resetRuntime();
        checkClose(
            0.98F,
            FirearmAimRuntime.calculateExcessSightAcquisitionMultiplier(1.0F),
            "one excess sight tile"
        );
        checkClose(
            0.8F,
            FirearmAimRuntime.calculateExcessSightAcquisitionMultiplier(10.0F),
            "ten excess sight tiles"
        );
        checkClose(
            0.8F,
            FirearmAimRuntime.calculateExcessSightAcquisitionMultiplier(100.0F),
            "excess sight bonus cap"
        );

        IsoPlayer player = createPlayer(15.0F, 0);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();
        weapon.setMaxSightRange(20.0F);
        weapon.setMaxRange(10.0F);
        setTarget(player, 8.0F, new IsoMovingObject(1));
        checkClose(
            45.0F,
            FirearmAimRuntime.calculateRequiredAimWork(player, weapon),
            "capped sight surplus work"
        );
    }

    private static void testConditionsBecomeAimTimeAtEveryRange() {
        resetRuntime();
        IsoPlayer player = createPlayer(15.0F, 0);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();
        HitInfo target = setTarget(player, 5.0F, new IsoMovingObject(1));

        FirearmAimRuntime.beginAccuracyCalculation(player, weapon, target);
        checkClose(
            5.0F,
            FirearmAimRuntime.prepareAccuracyDistance(5.0F, 2.0F, 6.0F),
            "inside-sight accuracy distance"
        );
        checkClose(
            0.0F,
            FirearmAimRuntime.convertRecoverablePenalty(12.0F),
            "movement penalty becomes time inside sight"
        );
        checkClose(
            0.0F,
            FirearmAimRuntime.convertRecoverablePenalty(8.0F),
            "moodle penalty becomes time inside sight"
        );
        checkClose(
            1.0F,
            FirearmAimRuntime.convertRecoverableVisionModifier(20.0F / 19.0F),
            "vision penalty becomes time inside sight"
        );
        FirearmAimRuntime.endAccuracyCalculation();

        checkClose(
            0.04F,
            FirearmAimRuntime.calculateConditionSecondsPerPoint(0),
            "aiming-zero condition time"
        );
        checkClose(
            0.025F,
            FirearmAimRuntime.calculateConditionSecondsPerPoint(10),
            "aiming-ten condition time"
        );
        checkClose(
            93.75F,
            FirearmAimRuntime.calculateRequiredAimWork(player, weapon),
            "twenty-five penalty points add one second at aiming zero"
        );

        runAimingUpdate(player, 56.25F);
        checkClose(6.0F, player.getAimingDelay(), "conditions must keep stabilization open");
        runAimingUpdate(player, 37.5F);
        checkClose(0.0F, player.getAimingDelay(), "condition work must remain recoverable");
    }

    private static void testTargetChangesRequireReacquisition() {
        resetRuntime();
        IsoPlayer player = createPlayer(15.0F, 0);
        setTarget(player, 5.0F, new IsoMovingObject(1));
        runAimingUpdate(player, 56.25F);
        checkClose(0.0F, player.getAimingDelay(), "first target must be fully locked");

        setTarget(player, 5.0F, new IsoMovingObject(2));
        runAimingUpdate(player, 0.0F);
        checkClose(10.5F, player.getAimingDelay(), "aiming zero retains thirty percent on target change");

        resetRuntime();
        player = createPlayer(15.0F, 10);
        setTarget(player, 5.0F, new IsoMovingObject(1));
        runAimingUpdate(player, 39.375F);
        setTarget(player, 5.0F, new IsoMovingObject(2));
        runAimingUpdate(player, 0.0F);
        checkClose(
            7.5F,
            player.getAimingDelay(),
            "aiming ten still needs the minimum 0.35-second reacquisition"
        );

        resetRuntime();
        player = createPlayer(15.0F, 0);
        IsoMovingObject sameTarget = new IsoMovingObject(1);
        setTarget(player, 5.0F, sameTarget);
        runAimingUpdate(player, 28.125F);
        setTarget(player, 5.0F, sameTarget);
        runAimingUpdate(player, 0.0F);
        checkClose(7.5F, player.getAimingDelay(), "rebuilt HitInfo for the same zombie keeps progress");
    }

    private static void testDistanceChangesOnSameTargetPreserveWork() {
        resetRuntime();
        IsoPlayer player = createPlayer(15.0F, 0);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();
        weapon.setMaxRange(16.0F);
        IsoMovingObject target = new IsoMovingObject(1);
        setTarget(player, 5.0F, target);
        runAimingUpdate(player, 56.25F);

        setTarget(player, 16.0F, target);
        runAimingUpdate(player, 0.0F);
        checkClose(12.5F, player.getAimingDelay(), "moving the same target farther reopens spread");

        setTarget(player, 5.0F, target);
        runAimingUpdate(player, 0.0F);
        checkClose(0.0F, player.getAimingDelay(), "moving the same target closer keeps invested work");
    }

    private static void testRecoilReopensMinimumSpread() {
        resetRuntime();
        checkClose(0.45F, FirearmAimRuntime.calculateRecoilReopenFraction(0), "aiming-zero recoil floor");
        checkClose(0.35F, FirearmAimRuntime.calculateRecoilReopenFraction(5), "aiming-five recoil floor");
        checkClose(0.25F, FirearmAimRuntime.calculateRecoilReopenFraction(10), "aiming-ten recoil floor");

        IsoPlayer player = createPlayer(15.0F, 0);
        setTarget(player, 5.0F, new IsoMovingObject(1));
        runAimingUpdate(player, 56.25F);
        player.setAimingDelay(1.0F);
        FirearmAimRuntime.synchronizePostShotDelay(player);
        checkClose(6.75F, player.getAimingDelay(), "weak vanilla recoil still reopens forty-five percent");

        resetRuntime();
        player = createPlayer(15.0F, 10);
        setTarget(player, 5.0F, new IsoMovingObject(1));
        runAimingUpdate(player, 39.375F);
        player.setAimingDelay(1.0F);
        FirearmAimRuntime.synchronizePostShotDelay(player);
        checkClose(3.75F, player.getAimingDelay(), "aiming ten recoil floor");

        resetRuntime();
        player = createPlayer(15.0F, 0);
        setTarget(player, 5.0F, new IsoMovingObject(1));
        runAimingUpdate(player, 56.25F);
        player.setAimingDelay(12.0F);
        FirearmAimRuntime.synchronizePostShotDelay(player);
        checkClose(12.0F, player.getAimingDelay(), "stronger vanilla recoil remains authoritative");
    }

    private static void testFullyStabilizedTargetIsGuaranteedToTakeDamage() {
        resetRuntime();
        IsoPlayer player = createPlayer(15.0F, 0);
        HitInfo target = setTarget(player, 5.0F, new IsoMovingObject(1));
        target.chance = 20;

        FirearmAimRuntime.guaranteeFullyStabilizedHit(player);
        check(target.chance == 20, "unstabilized target must retain vanilla chance");

        runAimingUpdate(player, 56.25F);
        FirearmAimRuntime.guaranteeFullyStabilizedHit(player);
        check(target.chance == 100, "fully stabilized primary target must be guaranteed");

        target = setTarget(player, 5.0F, new IsoMovingObject(2));
        target.chance = 20;
        FirearmAimRuntime.guaranteeFullyStabilizedHit(player);
        check(target.chance == 20, "guarantee must not transfer to another zombie");

        player.setAiming(false);
        target.chance = 20;
        FirearmAimRuntime.guaranteeFullyStabilizedHit(player);
        check(target.chance == 20, "guarantee applies only while aiming");
    }

    private static void testAccuracyChangesAreScopedToValidTargets() {
        resetRuntime();
        IsoPlayer player = createPlayer(15.0F, 0);
        HandWeapon firearm = (HandWeapon)player.getPrimaryHandItem();
        HandWeapon nonFirearm = createWeapon(false);
        HitInfo target = setTarget(player, 10.0F, new IsoMovingObject(1));

        FirearmAimRuntime.beginAccuracyCalculation(player, nonFirearm, target);
        checkClose(
            10.0F,
            FirearmAimRuntime.prepareAccuracyDistance(10.0F, 2.0F, 6.0F),
            "non-firearm distance"
        );
        checkClose(
            12.0F,
            FirearmAimRuntime.convertRecoverablePenalty(12.0F),
            "non-firearm penalty"
        );
        FirearmAimRuntime.endAccuracyCalculation();

        FirearmAimRuntime.beginAccuracyCalculation(player, firearm, null);
        checkClose(
            6.0F,
            FirearmAimRuntime.prepareAccuracyDistance(6.0F, 2.0F, 6.0F),
            "no-target reticle distance"
        );
        checkClose(
            12.0F,
            FirearmAimRuntime.convertRecoverablePenalty(12.0F),
            "no-target reticle keeps vanilla penalty"
        );
        FirearmAimRuntime.endAccuracyCalculation();

        FirearmAimRuntime.beginAccuracyCalculation(player, firearm, target);
        checkClose(
            4.0F,
            FirearmAimRuntime.prepareAccuracyDistance(10.0F, 2.0F, 6.0F),
            "far target uses optimal sight-band midpoint"
        );
        checkClose(
            8.0F,
            FirearmAimRuntime.removeBeyondSightDelayScaling(10.0F, 6.0F, 11.2F),
            "far target removes vanilla double-scaling"
        );
        checkClose(
            -3.0F,
            FirearmAimRuntime.convertRecoverablePenalty(-3.0F),
            "accuracy bonuses remain active"
        );
        checkClose(
            0.8F,
            FirearmAimRuntime.convertRecoverableVisionModifier(0.8F),
            "beneficial vision modifiers remain active"
        );
        FirearmAimRuntime.endAccuracyCalculation();

        FirearmAimRuntime.beginCriticalChanceCalculation(player, new IsoPlayer());
        checkClose(
            4.0F,
            FirearmAimRuntime.prepareAccuracyDistance(10.0F, 2.0F, 6.0F),
            "critical chance shares far normalization"
        );
        FirearmAimRuntime.endAccuracyCalculation();
    }

    private static void testPatchMetadata() throws ReflectiveOperationException {
        assertPatchTarget(
            FirearmAimingPatches.AimingDelayUpdate.class,
            "zombie.characters.IsoGameCharacter",
            "updateAimingDelay"
        );
        assertPatchTarget(
            FirearmAimingPatches.PostShotAimingDelay.class,
            "zombie.CombatManager",
            "setAimingDelay"
        );
        assertPatchTarget(
            FirearmAimingPatches.FullyStabilizedHitChance.class,
            "zombie.CombatManager",
            "calculateHitInfoList"
        );
        assertPatchTarget(
            FirearmAimingPatches.HitChanceCalculationScope.class,
            "zombie.CombatManager",
            "calculateHitChanceData"
        );
        assertPatchTarget(
            FirearmAimingPatches.CriticalChanceCalculationScope.class,
            "zombie.characters.IsoPlayer",
            "calculateCritChance"
        );
        assertPatchTarget(
            FirearmAimingPatches.DistanceModifier.class,
            "zombie.CombatManager",
            "getDistanceModifier"
        );
        assertPatchTarget(
            FirearmAimingPatches.AimDelayPenalty.class,
            "zombie.CombatManager",
            "getAimDelayPenalty"
        );
        assertPatchTarget(
            FirearmAimingPatches.MovementPenalty.class,
            "zombie.CombatManager",
            "getMovePenalty"
        );
        assertPatchTarget(
            FirearmAimingPatches.PainPenalty.class,
            "zombie.CombatManager",
            "getPainPenalty"
        );
        assertPatchTarget(
            FirearmAimingPatches.WeatherPenalty.class,
            "zombie.CombatManager",
            "getWeatherPenalty"
        );
        assertPatchTarget(
            FirearmAimingPatches.MoodlesPenalty.class,
            "zombie.CombatManager",
            "getMoodlesPenalty"
        );
        assertPatchTarget(
            FirearmAimingPatches.VisionPenalty.class,
            "zombie.characters.IsoGameCharacter",
            "getWornItemsVisionModifier"
        );

        Method scopeEnter = FirearmAimingPatches.HitChanceCalculationScope.class.getDeclaredMethod(
            "enter",
            IsoGameCharacter.class,
            HandWeapon.class,
            HitInfo.class
        );
        assertArgument(scopeEnter.getParameters()[0], 0, true, "hit owner");
        assertArgument(scopeEnter.getParameters()[1], 1, true, "hit weapon");
        assertArgument(scopeEnter.getParameters()[2], 2, true, "hit target");
        assertThrowableExit(FirearmAimingPatches.HitChanceCalculationScope.class, "hit scope");

        Method criticalEnter = FirearmAimingPatches.CriticalChanceCalculationScope.class.getDeclaredMethod(
            "enter",
            IsoPlayer.class,
            IsoGameCharacter.class
        );
        assertThis(criticalEnter.getParameters()[0], "critical receiver");
        assertArgument(criticalEnter.getParameters()[1], 0, true, "critical target");
        assertThrowableExit(FirearmAimingPatches.CriticalChanceCalculationScope.class, "critical scope");

        Method distanceEnter = FirearmAimingPatches.DistanceModifier.class.getDeclaredMethod(
            "enter",
            float.class,
            float.class,
            float.class
        );
        assertArgument(distanceEnter.getParameters()[0], 0, false, "accuracy distance");
        assertArgument(distanceEnter.getParameters()[1], 1, true, "minimum sight range");
        assertArgument(distanceEnter.getParameters()[2], 2, true, "maximum sight range");

        Method delayExit = FirearmAimingPatches.AimDelayPenalty.class.getDeclaredMethod(
            "exit",
            float.class,
            float.class,
            float.class
        );
        assertMutableReturn(delayExit.getParameters()[2], "aim-delay penalty");

        for (Class<?> penaltyPatch : List.of(
                FirearmAimingPatches.MovementPenalty.class,
                FirearmAimingPatches.PainPenalty.class,
                FirearmAimingPatches.WeatherPenalty.class,
                FirearmAimingPatches.MoodlesPenalty.class,
                FirearmAimingPatches.VisionPenalty.class)) {
            Method penaltyExit = penaltyPatch.getDeclaredMethod("exit", float.class);
            assertMutableReturn(penaltyExit.getParameters()[0], penaltyPatch.getSimpleName());
        }
    }

    private static void testZombieBuddyDiscovery() {
        List<Class<?>> discovered = PatchEngine.collectPatches(
            "com.cjstorrs.firearmaimingoverhaul",
            FirearmAimingPatchTest.class.getClassLoader()
        );
        List<Class<?>> expected = List.of(
            FirearmAimingPatches.AimingDelayUpdate.class,
            FirearmAimingPatches.PostShotAimingDelay.class,
            FirearmAimingPatches.FullyStabilizedHitChance.class,
            FirearmAimingPatches.HitChanceCalculationScope.class,
            FirearmAimingPatches.CriticalChanceCalculationScope.class,
            FirearmAimingPatches.DistanceModifier.class,
            FirearmAimingPatches.AimDelayPenalty.class,
            FirearmAimingPatches.MovementPenalty.class,
            FirearmAimingPatches.PainPenalty.class,
            FirearmAimingPatches.WeatherPenalty.class,
            FirearmAimingPatches.MoodlesPenalty.class,
            FirearmAimingPatches.VisionPenalty.class
        );
        check(discovered.size() == expected.size(), "ZombieBuddy must discover exactly twelve patches");
        for (Class<?> patchClass : expected) {
            check(discovered.contains(patchClass), "ZombieBuddy missed " + patchClass.getName());
        }
    }

    private static IsoPlayer createPlayer(float aimingDelay, int aimingLevel) {
        IsoPlayer player = new IsoPlayer();
        player.setAiming(true);
        player.setAimingLevel(aimingLevel);
        player.setPrimaryHandItem(createWeapon(true));
        player.setAimingDelay(aimingDelay);
        return player;
    }

    private static HandWeapon createWeapon(boolean aimedFirearm) {
        HandWeapon weapon = new HandWeapon();
        weapon.setAimedFirearm(aimedFirearm);
        weapon.setAimingTime(15);
        weapon.setMaxSightRange(6.0F);
        weapon.setMaxRange(16.0F);
        return weapon;
    }

    private static HitInfo setTarget(
            IsoGameCharacter character,
            float distance,
            IsoMovingObject target) {
        character.getHitInfoList().clear();
        HitInfo hitInfo = new HitInfo(distance * distance, target);
        character.getHitInfoList().add(hitInfo);
        return hitInfo;
    }

    private static void runAimingUpdate(IsoGameCharacter character, float vanillaReduction) {
        FirearmAimRuntime.beforeAimingDelayUpdate(character);
        character.setAimingDelay(Math.max(0.0F, character.getAimingDelay() - vanillaReduction));
        FirearmAimRuntime.afterAimingDelayUpdate(character);
    }

    private static void resetRuntime() {
        SandboxOptions.instance.clearOptionsForTest();
        FirearmAimRuntime.resetForTest();
    }

    private static void assertPatchTarget(Class<?> patchClass, String className, String methodName) {
        Patch patch = patchClass.getAnnotation(Patch.class);
        check(patch != null, patchClass.getName() + " must carry @Patch");
        check(className.equals(patch.className()), patchClass.getName() + " target class changed");
        check(methodName.equals(patch.methodName()), patchClass.getName() + " target method changed");
    }

    private static void assertArgument(Parameter parameter, int index, boolean readOnly, String label) {
        Patch.Argument argument = parameter.getAnnotation(Patch.Argument.class);
        check(argument != null, label + " must carry @Patch.Argument");
        check(argument.value() == index, label + " argument index changed");
        check(argument.readOnly() == readOnly, label + " mutability changed");
    }

    private static void assertThis(Parameter parameter, String label) {
        Patch.This thisArgument = parameter.getAnnotation(Patch.This.class);
        check(thisArgument != null, label + " must carry @Patch.This");
        check(thisArgument.readOnly(), label + " must be read-only");
    }

    private static void assertMutableReturn(Parameter parameter, String label) {
        Patch.Return returnValue = parameter.getAnnotation(Patch.Return.class);
        check(returnValue != null, label + " must carry @Patch.Return");
        check(!returnValue.readOnly(), label + " must remain mutable");
    }

    private static void assertThrowableExit(Class<?> patchClass, String label)
            throws ReflectiveOperationException {
        Method exit = patchClass.getDeclaredMethod("exit");
        Patch.OnExit advice = exit.getAnnotation(Patch.OnExit.class);
        check(advice != null, label + " exit must carry @Patch.OnExit");
        check(Throwable.class.equals(advice.onThrowable()), label + " must close after exceptions");
    }

    private static void checkClose(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > 0.001F) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
