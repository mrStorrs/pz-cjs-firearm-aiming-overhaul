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
import zombie.network.fields.hit.HitInfo;

public final class FirearmAimingPatchTest {
    private FirearmAimingPatchTest() {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        testCurveDefaultsAndConfiguration();
        testInsideSightRangeMatchesVanilla();
        testMaximumRangeTakesFourTimesAsLong();
        testMovingFartherReopensStabilization();
        testMovingCloserRetainsAccumulatedWork();
        testPostShotRecoveryUsesDistanceScaling();
        testAccuracyChangesAreScopedToFirearms();
        testPatchMetadata();
        testZombieBuddyDiscovery();
        System.out.println("FirearmAimingPatchTest: PASS");
    }

    private static void testCurveDefaultsAndConfiguration() {
        resetOptions();
        IsoPlayer player = createPlayer(10.0F);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();

        setTargetDistance(player, 6.0F);
        checkClose(1.0F, FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon), "sight range");

        setTargetDistance(player, 10.0F);
        checkClose(1.4582F, FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon), "mid-near range");

        setTargetDistance(player, 15.0F);
        checkClose(2.5464F, FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon), "mid-far range");

        setTargetDistance(player, 20.0F);
        checkClose(4.0F, FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon), "maximum range");

        SandboxOptions.instance.setOptionForTest(new SandboxOptions.DoubleSandboxOption(6.0));
        checkClose(6.0F, FirearmAimSettings.getMaximumMultiplier(), "configured maximum multiplier");
        resetOptions();
    }

    private static void testInsideSightRangeMatchesVanilla() {
        resetRuntime();
        IsoPlayer player = createPlayer(10.0F);
        setTargetDistance(player, 5.0F);

        runAimingUpdate(player, 1.0F);
        checkClose(9.0F, player.getAimingDelay(), "inside sight range must keep vanilla countdown");

        runAimingUpdate(player, 1.0F);
        checkClose(8.0F, player.getAimingDelay(), "inside sight range must remain exact");
    }

    private static void testMaximumRangeTakesFourTimesAsLong() {
        resetRuntime();
        IsoPlayer player = createPlayer(10.0F);
        setTargetDistance(player, 20.0F);

        for (int i = 0; i < 10; i++) {
            runAimingUpdate(player, 1.0F);
        }

        checkClose(7.5F, player.getAimingDelay(), "ten vanilla work units must be one quarter stabilized at maximum range");

        for (int i = 0; i < 30; i++) {
            runAimingUpdate(player, 1.0F);
        }

        checkClose(0.0F, player.getAimingDelay(), "maximum range must fully stabilize after forty work units");
    }

    private static void testMovingFartherReopensStabilization() {
        resetRuntime();
        IsoPlayer player = createPlayer(10.0F);
        setTargetDistance(player, 5.0F);

        for (int i = 0; i < 10; i++) {
            runAimingUpdate(player, 1.0F);
        }
        checkClose(0.0F, player.getAimingDelay(), "close target should fully stabilize");

        setTargetDistance(player, 20.0F);
        FirearmAimRuntime.beforeAimingDelayUpdate(player);
        FirearmAimRuntime.afterAimingDelayUpdate(player);
        checkClose(7.5F, player.getAimingDelay(), "moving to maximum range must reopen stabilization");
    }

    private static void testMovingCloserRetainsAccumulatedWork() {
        resetRuntime();
        IsoPlayer player = createPlayer(10.0F);
        setTargetDistance(player, 20.0F);

        for (int i = 0; i < 20; i++) {
            runAimingUpdate(player, 1.0F);
        }
        checkClose(5.0F, player.getAimingDelay(), "far target should be half stabilized");

        setTargetDistance(player, 5.0F);
        runAimingUpdate(player, 0.0F);
        checkClose(0.0F, player.getAimingDelay(), "moving closer may use accumulated stabilization");

        setTargetDistance(player, 20.0F);
        FirearmAimRuntime.beforeAimingDelayUpdate(player);
        FirearmAimRuntime.afterAimingDelayUpdate(player);
        checkClose(5.0F, player.getAimingDelay(), "moving close must not erase accumulated far-range work");
    }

    private static void testPostShotRecoveryUsesDistanceScaling() {
        resetRuntime();
        IsoPlayer player = createPlayer(10.0F);
        setTargetDistance(player, 20.0F);

        for (int i = 0; i < 40; i++) {
            runAimingUpdate(player, 1.0F);
        }

        player.setAimingDelay(5.0F);
        FirearmAimRuntime.synchronizePostShotDelay(player);
        for (int i = 0; i < 19; i++) {
            runAimingUpdate(player, 1.0F);
        }
        check(player.getAimingDelay() > 0.0F, "far post-shot recovery must still be stabilizing before four-times duration");

        runAimingUpdate(player, 1.0F);
        checkClose(0.0F, player.getAimingDelay(), "far post-shot recovery must finish after scaled duration");
    }

    private static void testAccuracyChangesAreScopedToFirearms() {
        resetRuntime();
        IsoPlayer player = createPlayer(10.0F);
        HandWeapon firearm = (HandWeapon)player.getPrimaryHandItem();
        HandWeapon nonFirearm = createWeapon(false);

        FirearmAimRuntime.beginAccuracyCalculation(player, nonFirearm);
        checkClose(
            -20.0F,
            FirearmAimRuntime.removeBeyondSightDistancePenalty(10.0F, 6.0F, -20.0F),
            "non-firearm distance modifier"
        );
        FirearmAimRuntime.endAccuracyCalculation();

        FirearmAimRuntime.beginAccuracyCalculation(player, firearm);
        checkClose(
            -20.0F,
            FirearmAimRuntime.removeBeyondSightDistancePenalty(5.0F, 6.0F, -20.0F),
            "inside-sight modifier"
        );
        checkClose(
            0.0F,
            FirearmAimRuntime.removeBeyondSightDistancePenalty(10.0F, 6.0F, -20.0F),
            "beyond-sight modifier"
        );
        checkClose(
            8.0F,
            FirearmAimRuntime.removeBeyondSightDelayScaling(10.0F, 6.0F, 11.2F),
            "beyond-sight delay scaling"
        );
        checkClose(
            -20.0F,
            FirearmAimRuntime.removeBeyondSightDistancePenalty(21.0F, 6.0F, -20.0F),
            "beyond-physical-range modifier"
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
            FirearmAimingPatches.HitChanceCalculationScope.class,
            "zombie.CombatManager",
            "calculateHitChanceData"
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

        Method updateEnter = FirearmAimingPatches.AimingDelayUpdate.class.getDeclaredMethod(
            "enter",
            IsoGameCharacter.class
        );
        assertThis(updateEnter.getParameters()[0], "aim-delay receiver");

        Method postShotExit = FirearmAimingPatches.PostShotAimingDelay.class.getDeclaredMethod(
            "exit",
            IsoPlayer.class
        );
        assertArgument(postShotExit.getParameters()[0], 0, true, "post-shot player");

        Method scopeExit = FirearmAimingPatches.HitChanceCalculationScope.class.getDeclaredMethod("exit");
        Patch.OnExit scopeExitAdvice = scopeExit.getAnnotation(Patch.OnExit.class);
        check(scopeExitAdvice != null, "accuracy scope exit must carry @Patch.OnExit");
        check(Throwable.class.equals(scopeExitAdvice.onThrowable()), "accuracy scope must close after exceptions");

        Method distanceExit = FirearmAimingPatches.DistanceModifier.class.getDeclaredMethod(
            "exit",
            float.class,
            float.class,
            float.class
        );
        assertMutableReturn(distanceExit.getParameters()[2], "distance modifier");

        Method delayExit = FirearmAimingPatches.AimDelayPenalty.class.getDeclaredMethod(
            "exit",
            float.class,
            float.class,
            float.class
        );
        assertMutableReturn(delayExit.getParameters()[2], "aim-delay penalty");
    }

    private static void testZombieBuddyDiscovery() {
        List<Class<?>> discovered = PatchEngine.collectPatches(
            "com.cjstorrs.firearmaimingoverhaul",
            FirearmAimingPatchTest.class.getClassLoader()
        );
        List<Class<?>> expected = List.of(
            FirearmAimingPatches.AimingDelayUpdate.class,
            FirearmAimingPatches.PostShotAimingDelay.class,
            FirearmAimingPatches.HitChanceCalculationScope.class,
            FirearmAimingPatches.DistanceModifier.class,
            FirearmAimingPatches.AimDelayPenalty.class
        );
        check(discovered.size() == expected.size(), "ZombieBuddy must discover exactly five patch classes");
        for (Class<?> patchClass : expected) {
            check(discovered.contains(patchClass), "ZombieBuddy missed " + patchClass.getName());
        }
    }

    private static IsoPlayer createPlayer(float aimingDelay) {
        IsoPlayer player = new IsoPlayer();
        player.setAiming(true);
        player.setPrimaryHandItem(createWeapon(true));
        player.setAimingDelay(aimingDelay);
        return player;
    }

    private static HandWeapon createWeapon(boolean aimedFirearm) {
        HandWeapon weapon = new HandWeapon();
        weapon.setAimedFirearm(aimedFirearm);
        weapon.setAimingTime(10);
        weapon.setMaxSightRange(6.0F);
        weapon.setMaxRange(20.0F);
        return weapon;
    }

    private static void setTargetDistance(IsoGameCharacter character, float distance) {
        character.getHitInfoList().clear();
        character.getHitInfoList().add(new HitInfo(distance * distance));
    }

    private static void runAimingUpdate(IsoGameCharacter character, float vanillaReduction) {
        FirearmAimRuntime.beforeAimingDelayUpdate(character);
        character.setAimingDelay(Math.max(0.0F, character.getAimingDelay() - vanillaReduction));
        FirearmAimRuntime.afterAimingDelayUpdate(character);
    }

    private static void resetRuntime() {
        resetOptions();
        FirearmAimRuntime.resetForTest();
    }

    private static void resetOptions() {
        SandboxOptions.instance.setOptionForTest(null);
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
