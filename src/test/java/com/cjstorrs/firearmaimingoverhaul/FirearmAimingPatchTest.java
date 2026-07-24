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
        testAbsoluteDistanceIgnoresSightToMaximumGap();
        testTinySightToMaximumGapGetsSmallPenalty();
        testExcessSightRangeSpeedsTargetAcquisition();
        testClosestTargetControlsAimTime();
        testInsideSightRangeMatchesVanilla();
        testMaximumRangeTakesFourTimesAsLong();
        testMovingFartherReopensStabilization();
        testMovingCloserRetainsAccumulatedWork();
        testPostShotRecoveryUsesDistanceScaling();
        testAccuracyChangesAreScopedToFirearms();
        testBeyondSightPenaltiesBecomeAdditionalAimTime();
        testFullyStabilizedTargetIsGuaranteedToTakeDamage();
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
        checkClose(1.7589F, FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon), "four tiles beyond sight");

        setTargetDistance(player, 15.0F);
        checkClose(3.5614F, FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon), "nine tiles beyond sight");

        setTargetDistance(player, 20.0F);
        checkClose(4.0F, FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon), "maximum range");

        SandboxOptions.instance.setOptionForTest(
            "CJSFirearmAimingOverhaul.MaximumAimTimeMultiplier",
            new SandboxOptions.DoubleSandboxOption(6.0)
        );
        checkClose(6.0F, FirearmAimSettings.getMaximumMultiplier(), "configured maximum multiplier");
        SandboxOptions.instance.setOptionForTest(
            "CJSFirearmAimingOverhaul.FullPenaltyDistanceTiles",
            new SandboxOptions.DoubleSandboxOption(5.0)
        );
        checkClose(5.0F, FirearmAimSettings.getFullPenaltyDistanceTiles(), "configured full-penalty distance");
        resetOptions();
    }

    private static void testAbsoluteDistanceIgnoresSightToMaximumGap() {
        resetRuntime();
        IsoPlayer player = createPlayer(10.0F);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();
        setTargetDistance(player, 8.0F);
        float wideGapMultiplier = FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon);

        weapon.setMaxSightRange(19.0F);
        weapon.setMaxRange(25.0F);
        setTargetDistance(player, 21.0F);
        float narrowGapMultiplier = FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon);

        weapon.setMaxSightRange(8.0F);
        weapon.setMaxRange(27.5F);
        setTargetDistance(player, 10.0F);
        float skillAdjustedMultiplier = FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon);

        checkClose(1.2683F, wideGapMultiplier, "two tiles beyond sight with a wide gap");
        checkClose(
            wideGapMultiplier,
            narrowGapMultiplier,
            "the same absolute distance beyond sight must have the same multiplier"
        );
        checkClose(
            wideGapMultiplier,
            skillAdjustedMultiplier,
            "skill-adjusted sight and maximum ranges must still use absolute over-sight distance"
        );
    }

    private static void testTinySightToMaximumGapGetsSmallPenalty() {
        resetRuntime();
        IsoPlayer player = createPlayer(10.0F);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();
        weapon.setMaxSightRange(19.0F);
        weapon.setMaxRange(20.0F);
        setTargetDistance(player, 20.0F);

        checkClose(
            1.0949F,
            FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon),
            "one-tile sight-to-maximum gap"
        );
    }

    private static void testExcessSightRangeSpeedsTargetAcquisition() {
        resetRuntime();
        IsoPlayer player = createPlayer(10.0F);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();
        weapon.setMaxRange(10.0F);
        setTargetDistance(player, 8.0F);

        weapon.setMaxSightRange(10.0F);
        checkClose(
            1.0F,
            FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon),
            "sight equal to physical range"
        );

        weapon.setMaxSightRange(11.0F);
        checkClose(
            0.9134F,
            FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon),
            "one excess sight tile must provide a modest acquisition bonus"
        );

        weapon.setMaxSightRange(20.0F);
        checkClose(
            0.25F,
            FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon),
            "ten excess sight tiles must reach the reciprocal maximum bonus"
        );

        for (int i = 0; i < 2; i++) {
            runAimingUpdate(player, 1.0F);
        }
        check(player.getAimingDelay() > 0.0F, "maximum sight surplus must not stabilize before enough work");
        runAimingUpdate(player, 1.0F);
        checkClose(0.0F, player.getAimingDelay(), "maximum sight surplus must acquire in one quarter normal time");
    }

    private static void testClosestTargetControlsAimTime() {
        resetRuntime();
        IsoPlayer player = createPlayer(10.0F);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();
        setTargetDistance(player, 20.0F);
        player.getHitInfoList().add(new HitInfo(100.0F));

        checkClose(
            1.7589F,
            FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon),
            "the closest target shown by the reticle must control aim time"
        );

        FirearmAimRuntime.beginAccuracyCalculation(player, weapon);
        FirearmAimRuntime.normalizeBeyondSightAccuracyDistance(20.0F, 2.0F, 6.0F);
        FirearmAimRuntime.convertBeyondSightPermanentPenalty(50.0F);
        FirearmAimRuntime.endAccuracyCalculation();
        FirearmAimRuntime.beginAccuracyCalculation(player, weapon);
        FirearmAimRuntime.normalizeBeyondSightAccuracyDistance(10.0F, 2.0F, 6.0F);
        FirearmAimRuntime.convertBeyondSightPermanentPenalty(10.0F);
        FirearmAimRuntime.endAccuracyCalculation();
        runAimingUpdate(player, 0.0F);

        checkClose(
            2.7589F,
            FirearmAimRuntime.calculateAimTimeMultiplier(player, weapon),
            "the closest target's recoverable penalties must control aim time"
        );
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
            10.0F,
            FirearmAimRuntime.normalizeBeyondSightAccuracyDistance(10.0F, 2.0F, 6.0F),
            "non-firearm accuracy distance"
        );
        FirearmAimRuntime.endAccuracyCalculation();

        FirearmAimRuntime.beginCriticalChanceCalculation(player);
        checkClose(
            4.0F,
            FirearmAimRuntime.normalizeBeyondSightAccuracyDistance(10.0F, 2.0F, 6.0F),
            "critical-chance accuracy must share firearm normalization"
        );
        FirearmAimRuntime.endAccuracyCalculation();

        FirearmAimRuntime.beginAccuracyCalculation(player, firearm);
        checkClose(
            5.0F,
            FirearmAimRuntime.normalizeBeyondSightAccuracyDistance(5.0F, 2.0F, 6.0F),
            "inside-sight accuracy distance"
        );
        checkClose(
            4.0F,
            FirearmAimRuntime.normalizeBeyondSightAccuracyDistance(10.0F, 2.0F, 6.0F),
            "beyond-sight accuracy must use the optimal sight-band midpoint"
        );
        checkClose(
            8.0F,
            FirearmAimRuntime.removeBeyondSightDelayScaling(10.0F, 6.0F, 11.2F),
            "beyond-sight delay scaling"
        );
        checkClose(
            21.0F,
            FirearmAimRuntime.normalizeBeyondSightAccuracyDistance(21.0F, 2.0F, 6.0F),
            "beyond-physical-range accuracy distance"
        );
        FirearmAimRuntime.endAccuracyCalculation();
    }

    private static void testBeyondSightPenaltiesBecomeAdditionalAimTime() {
        resetRuntime();
        IsoPlayer player = createPlayer(10.0F);
        HandWeapon firearm = (HandWeapon)player.getPrimaryHandItem();
        setTargetDistance(player, 20.0F);

        FirearmAimRuntime.beginAccuracyCalculation(player, firearm);
        checkClose(
            4.0F,
            FirearmAimRuntime.normalizeBeyondSightAccuracyDistance(20.0F, 2.0F, 6.0F),
            "far accuracy distance"
        );
        checkClose(
            0.0F,
            FirearmAimRuntime.convertBeyondSightPermanentPenalty(12.0F),
            "movement penalty must become aim time"
        );
        checkClose(
            0.0F,
            FirearmAimRuntime.convertBeyondSightPermanentPenalty(8.0F),
            "moodle penalty must become aim time"
        );
        checkClose(
            1.0F,
            FirearmAimRuntime.convertBeyondSightVisionModifier(20.0F / 19.0F),
            "vision penalty must become aim time"
        );
        FirearmAimRuntime.endAccuracyCalculation();
        runAimingUpdate(player, 0.0F);

        checkClose(
            6.5F,
            FirearmAimRuntime.calculateAimTimeMultiplier(player, firearm),
            "twenty-five recovered accuracy points must add 2.5x aim time"
        );

        for (int i = 0; i < 40; i++) {
            runAimingUpdate(player, 1.0F);
        }
        check(player.getAimingDelay() > 0.0F, "condition penalties must keep far stabilization in progress");

        for (int i = 0; i < 25; i++) {
            runAimingUpdate(player, 1.0F);
        }
        checkClose(0.0F, player.getAimingDelay(), "condition penalties must remain fully recoverable");

        FirearmAimRuntime.beginAccuracyCalculation(player, firearm);
        FirearmAimRuntime.normalizeBeyondSightAccuracyDistance(20.0F, 2.0F, 6.0F);
        checkClose(
            -3.0F,
            FirearmAimRuntime.convertBeyondSightPermanentPenalty(-3.0F),
            "accuracy bonuses must not be converted into penalties"
        );
        checkClose(
            0.8F,
            FirearmAimRuntime.convertBeyondSightVisionModifier(0.8F),
            "beneficial vision modifiers must remain active"
        );
        FirearmAimRuntime.endAccuracyCalculation();

        resetRuntime();
        player = createPlayer(10.0F);
        firearm = (HandWeapon)player.getPrimaryHandItem();
        setTargetDistance(player, 5.0F);
        FirearmAimRuntime.beginAccuracyCalculation(player, firearm);
        FirearmAimRuntime.normalizeBeyondSightAccuracyDistance(5.0F, 2.0F, 6.0F);
        checkClose(
            12.0F,
            FirearmAimRuntime.convertBeyondSightPermanentPenalty(12.0F),
            "inside-sight penalties must remain vanilla"
        );
        checkClose(
            1.25F,
            FirearmAimRuntime.convertBeyondSightVisionModifier(1.25F),
            "inside-sight vision must remain vanilla"
        );
        FirearmAimRuntime.endAccuracyCalculation();
        checkClose(
            1.0F,
            FirearmAimRuntime.calculateAimTimeMultiplier(player, firearm),
            "inside-sight penalties must not extend aim time"
        );
    }

    private static void testFullyStabilizedTargetIsGuaranteedToTakeDamage() {
        resetRuntime();
        IsoPlayer player = createPlayer(10.0F);
        setTargetDistance(player, 5.0F);
        HitInfo primaryTarget = player.getHitInfoList().get(0);
        primaryTarget.chance = 20;
        HitInfo secondaryTarget = new HitInfo(36.0F);
        secondaryTarget.chance = 15;
        player.getHitInfoList().add(secondaryTarget);

        FirearmAimRuntime.guaranteeFullyStabilizedHit(player);
        check(primaryTarget.chance == 20, "an unstabilized target must keep its vanilla hit chance");

        for (int i = 0; i < 10; i++) {
            runAimingUpdate(player, 1.0F);
        }
        FirearmAimRuntime.guaranteeFullyStabilizedHit(player);
        check(primaryTarget.chance == 100, "the fully stabilized primary target must have 100 hit chance");
        check(secondaryTarget.chance == 15, "full lock must not guarantee every shotgun or piercing target");

        setTargetDistance(player, 20.0F);
        HitInfo farTarget = player.getHitInfoList().get(0);
        farTarget.chance = 20;
        FirearmAimRuntime.guaranteeFullyStabilizedHit(player);
        check(farTarget.chance == 20, "moving a completed lock farther away must reopen it before guaranteeing damage");

        for (int i = 0; i < 30; i++) {
            runAimingUpdate(player, 1.0F);
        }
        FirearmAimRuntime.guaranteeFullyStabilizedHit(player);
        check(farTarget.chance == 100, "a fully completed far lock must guarantee damage");

        FirearmAimRuntime.beginAccuracyCalculation(player, (HandWeapon)player.getPrimaryHandItem());
        FirearmAimRuntime.normalizeBeyondSightAccuracyDistance(20.0F, 2.0F, 6.0F);
        FirearmAimRuntime.convertBeyondSightPermanentPenalty(10.0F);
        FirearmAimRuntime.endAccuracyCalculation();
        farTarget.chance = 20;
        FirearmAimRuntime.guaranteeFullyStabilizedHit(player);
        check(farTarget.chance == 20, "new recoverable penalties must reopen full lock before guaranteeing damage");

        for (int i = 0; i < 10; i++) {
            runAimingUpdate(player, 1.0F);
        }
        FirearmAimRuntime.guaranteeFullyStabilizedHit(player);
        check(farTarget.chance == 100, "recovered condition penalties must permit guaranteed damage again");

        player.setAiming(false);
        farTarget.chance = 20;
        FirearmAimRuntime.guaranteeFullyStabilizedHit(player);
        check(farTarget.chance == 20, "the guarantee must apply only while actively aiming");
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

        Method stabilizedHitExit = FirearmAimingPatches.FullyStabilizedHitChance.class.getDeclaredMethod(
            "exit",
            IsoGameCharacter.class
        );
        assertArgument(stabilizedHitExit.getParameters()[0], 0, true, "fully stabilized hit owner");

        Method scopeExit = FirearmAimingPatches.HitChanceCalculationScope.class.getDeclaredMethod("exit");
        Patch.OnExit scopeExitAdvice = scopeExit.getAnnotation(Patch.OnExit.class);
        check(scopeExitAdvice != null, "accuracy scope exit must carry @Patch.OnExit");
        check(Throwable.class.equals(scopeExitAdvice.onThrowable()), "accuracy scope must close after exceptions");

        Method criticalScopeEnter = FirearmAimingPatches.CriticalChanceCalculationScope.class.getDeclaredMethod(
            "enter",
            IsoPlayer.class
        );
        assertThis(criticalScopeEnter.getParameters()[0], "critical-chance receiver");
        Method criticalScopeExit = FirearmAimingPatches.CriticalChanceCalculationScope.class.getDeclaredMethod("exit");
        Patch.OnExit criticalScopeExitAdvice = criticalScopeExit.getAnnotation(Patch.OnExit.class);
        check(criticalScopeExitAdvice != null, "critical-chance scope exit must carry @Patch.OnExit");
        check(
            Throwable.class.equals(criticalScopeExitAdvice.onThrowable()),
            "critical-chance scope must close after exceptions"
        );

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
        check(discovered.size() == expected.size(), "ZombieBuddy must discover exactly twelve patch classes");
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
        SandboxOptions.instance.clearOptionsForTest();
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
