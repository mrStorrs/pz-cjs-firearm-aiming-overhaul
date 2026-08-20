package com.cjstorrs.firearmaimingoverhaul;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import me.zed_0xff.zombie_buddy.Patch;
import me.zed_0xff.zombie_buddy.PatchEngine;
import zombie.SandboxOptions;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.physics.BallisticsController;
import zombie.core.physics.RagdollBodyPart;
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
        testWeaponChangeUsesTheNewWeaponAimingTime();
        testPhysicalTargetDistanceOverridesBowBallisticsHitDistance();
        testResolvedHitPointDistanceOverridesBowProxyPosition();
        testReticleCameraTargetProvidesMaximumRangeWithoutHitInfo();
        testReticleTargetSurvivesBriefMissingUpdate();
        testFarAimAddsSecondsInsteadOfMultiplyingTinyTimers();
        testExcessSightBonusIsCapped();
        testConditionsBecomeAimTimeAtEveryRange();
        testTargetChangesRequireReacquisition();
        testDistanceChangesOnSameTargetPreserveWork();
        testRecoilReopensMinimumSpread();
        testStabilizationProgressesHitChanceToGuarantee();
        testShotHitChanceUsesPreRecoilStabilizationForResolvedTarget();
        testFullyStabilizedTargetedHeadshotsAreLethal();
        testHeadshotDiagnosticsExplainDecision();
        testAccuracyChangesAreScopedToValidTargets();
        testPatchMetadata();
        testZombieBuddyDiscovery();
        System.out.println("FirearmAimingPatchTest: PASS");
    }

    private static void testHybridCurveDefaultsAndConfiguration() {
        resetRuntime();
        SandboxOptions.instance.clearOptionsForTest();
        checkClose(
            4.0F,
            FirearmAimSettings.getMaximumCleanAimSeconds(),
            "default maximum clean aim seconds"
        );
        checkClose(
            4.0F,
            FirearmAimSettings.getMaximumConditionSeconds(),
            "default maximum condition seconds"
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
        check(
            FirearmAimSettings.isHeadshotDiagnosticLoggingEnabled(),
            "headshot diagnostics must default on"
        );

        SandboxOptions.instance.setOptionForTest(
            "CJSFirearmAimingOverhaul.MaximumCleanAimSeconds",
            new SandboxOptions.DoubleSandboxOption(6.0)
        );
        SandboxOptions.instance.setOptionForTest(
            "CJSFirearmAimingOverhaul.MaximumConditionSeconds",
            new SandboxOptions.DoubleSandboxOption(3.0)
        );
        SandboxOptions.instance.setOptionForTest(
            "CJSFirearmAimingOverhaul.CurveExponent",
            new SandboxOptions.DoubleSandboxOption(2.0)
        );
        SandboxOptions.instance.setOptionForTest(
            "CJSFirearmAimingOverhaul.FullPenaltyDistanceTiles",
            new SandboxOptions.DoubleSandboxOption(5.0)
        );
        SandboxOptions.instance.setOptionForTest(
            "CJSFirearmAimingOverhaul.HeadshotDiagnosticLogging",
            new SandboxOptions.BooleanSandboxOption(false)
        );
        checkClose(6.0F, FirearmAimSettings.getMaximumCleanAimSeconds(), "configured clean seconds");
        checkClose(3.0F, FirearmAimSettings.getMaximumConditionSeconds(), "configured condition seconds");
        checkClose(2.0F, FirearmAimSettings.getFarProgressExponent(), "configured exponent");
        checkClose(5.0F, FirearmAimSettings.getReferenceGapTiles(), "configured reference gap");
        check(
            !FirearmAimSettings.isHeadshotDiagnosticLoggingEnabled(),
            "configured headshot diagnostics"
        );
    }

    private static void testHybridCurveNormalizesWeaponGap() {
        resetRuntime();
        checkClose(4.0F, FirearmAimRuntime.calculateMaximumCleanAimSeconds(0),
            "aiming-zero clean cap");
        checkClose(3.5F, FirearmAimRuntime.calculateMaximumCleanAimSeconds(5),
            "aiming-five clean cap");
        checkClose(3.0F, FirearmAimRuntime.calculateMaximumCleanAimSeconds(10),
            "aiming-ten clean cap");

        checkClose(
            2.8409F,
            FirearmAimRuntime.calculateCleanAimSeconds(0, 1.5F, 11.0F, 6.0F, 16.0F),
            "halfway through a ten-tile gap"
        );
        checkClose(
            2.8409F,
            FirearmAimRuntime.calculateCleanAimSeconds(0, 1.5F, 16.0F, 6.0F, 26.0F),
            "halfway through a twenty-tile rifle gap"
        );
        checkClose(
            2.6180F,
            FirearmAimRuntime.calculateCleanAimSeconds(0, 1.5F, 8.0F, 6.0F, 8.0F),
            "maximum of a two-tile gap"
        );
        checkClose(
            4.0F,
            FirearmAimRuntime.calculateCleanAimSeconds(0, 1.5F, 16.0F, 6.0F, 16.0F),
            "maximum of a ten-tile gap at aiming zero"
        );
        checkClose(
            3.0F,
            FirearmAimRuntime.calculateCleanAimSeconds(10, 0.7F, 16.0F, 6.0F, 16.0F),
            "maximum of a ten-tile gap at aiming ten"
        );
        checkClose(
            2.0F,
            FirearmAimRuntime.calculateCleanAimSeconds(0, 2.0F, 5.0F, 6.0F, 16.0F),
            "inside sight keeps base acquisition"
        );
        checkClose(
            5.0F,
            FirearmAimRuntime.calculateCleanAimSeconds(0, 5.0F, 16.0F, 6.0F, 16.0F),
            "slow vanilla acquisition remains authoritative"
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

    private static void testWeaponChangeUsesTheNewWeaponAimingTime() {
        resetRuntime();
        IsoPlayer player = createPlayer(25.0F, 0);
        HandWeapon bow = (HandWeapon)player.getPrimaryHandItem();
        bow.setFullType("cjsSimpleBows.SB_Bow_crafted");
        bow.setAimingTime(65);
        player.setAimingDelay(25.0F);
        setTarget(player, 5.0F, new IsoMovingObject(1));

        checkClose(
            65.0F,
            FirearmAimRuntime.calculateRequiredAimWork(player, bow),
            "switching from a pistol must not shorten a crafted bow's aim time"
        );

        FirearmAimRuntime.beforeAimingDelayUpdate(player);
        checkClose(
            65.0F,
            player.getAimingDelay(),
            "a new bow aim must start from its full vanilla delay"
        );
        FirearmAimRuntime.afterAimingDelayUpdate(player);
    }

    private static void testPhysicalTargetDistanceOverridesBowBallisticsHitDistance() {
        resetRuntime();
        IsoPlayer player = createPlayer(65.0F, 0);
        HandWeapon bow = (HandWeapon)player.getPrimaryHandItem();
        bow.setFullType("cjsSimpleBows.SB_Bow_crafted");
        bow.setAimingTime(65);
        bow.setMaxSightRange(6.0F);
        bow.setMaxRange(16.0F);

        IsoMovingObject distantTarget = new IsoMovingObject(1);
        distantTarget.setX(16.0F);
        HitInfo hitInfo = setTarget(player, 0.5F, distantTarget);

        checkClose(
            150.0F,
            FirearmAimRuntime.calculateRequiredAimWork(player, bow),
            "a bow's far target must use world distance, not B42.20 hit-info distance"
        );
        checkClose(0.25F, hitInfo.distSq, "test must preserve the bad ballistic hit distance");
    }

    private static void testResolvedHitPointDistanceOverridesBowProxyPosition() {
        resetRuntime();
        IsoPlayer player = createPlayer(65.0F, 0);
        HandWeapon bow = (HandWeapon)player.getPrimaryHandItem();
        bow.setFullType("cjsSimpleBows.SB_Bow_crafted");
        bow.setAimingTime(65);
        bow.setMaxSightRange(6.0F);
        bow.setMaxRange(16.0F);

        IsoMovingObject ballisticProxy = new IsoMovingObject(1);
        ballisticProxy.setX(0.5F);
        HitInfo hitInfo = setTarget(player, 0.5F, ballisticProxy);
        hitInfo.x = 16.0F;
        hitInfo.y = 0.0F;

        checkClose(
            150.0F,
            FirearmAimRuntime.calculateRequiredAimWork(player, bow),
            "a bow's far target must use B42.20's resolved ballistic hit point"
        );
        checkClose(0.25F, hitInfo.distSq, "test must preserve the bad ballistic hit distance");
    }

    private static void testReticleCameraTargetProvidesMaximumRangeWithoutHitInfo() {
        resetRuntime();
        IsoPlayer player = createPlayer(65.0F, 0);
        HandWeapon bow = (HandWeapon)player.getPrimaryHandItem();
        bow.setFullType("cjsSimpleBows.SB_Bow_crafted");
        bow.setAimingTime(65);
        bow.setMaxSightRange(6.0F);
        bow.setMaxRange(16.0F);
        BallisticsController ballistics = new BallisticsController();
        ballistics.setCameraTargetForTest(1, 16.0F, 0.0F, RagdollBodyPart.BODYPART_HEAD.ordinal());
        player.setBallisticsController(ballistics);

        FirearmAimRuntime.captureReticleTarget(player);
        checkClose(
            150.0F,
            FirearmAimRuntime.calculateRequiredAimWork(player, bow),
            "a maximum-range camera target must not fall back to the bow's bare timer"
        );
    }

    private static void testReticleTargetSurvivesBriefMissingUpdate() {
        resetRuntime();
        IsoPlayer player = createPlayer(65.0F, 0);
        HandWeapon bow = (HandWeapon)player.getPrimaryHandItem();
        bow.setMaxSightRange(6.0F);
        bow.setMaxRange(16.0F);
        BallisticsController ballistics = new BallisticsController();
        ballistics.setCameraTargetForTest(1, 16.0F, 0.0F, RagdollBodyPart.BODYPART_HEAD.ordinal());
        player.setBallisticsController(ballistics);
        FirearmAimRuntime.captureReticleTarget(player);

        ballistics.clearCameraTargetsForTest();
        FirearmAimRuntime.captureReticleTarget(player);
        checkClose(
            150.0F,
            FirearmAimRuntime.calculateRequiredAimWork(player, bow),
            "one missing reticle update must not reset a maximum-range lock to the bare timer"
        );
    }

    private static void testFarAimAddsSecondsInsteadOfMultiplyingTinyTimers() {
        resetRuntime();
        IsoPlayer player = createPlayer(15.0F, 0);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();
        weapon.setMaxRange(16.0F);
        setTarget(player, 16.0F, new IsoMovingObject(1));

        checkClose(
            150.0F,
            FirearmAimRuntime.calculateRequiredAimWork(player, weapon),
            "aiming-zero maximum-range work"
        );
        runAimingUpdate(player, 56.25F);
        check(player.getAimingDelay() > 0.0F, "inside-range floor alone must not finish far aim");
        runAimingUpdate(player, 93.75F);
        checkClose(0.0F, player.getAimingDelay(), "maximum-range aim must eventually finish");

        resetRuntime();
        player = createPlayer(15.0F, 10);
        weapon = (HandWeapon)player.getPrimaryHandItem();
        weapon.setMaxRange(16.0F);
        setTarget(player, 16.0F, new IsoMovingObject(1));
        checkClose(
            168.75F,
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

        checkClose(4.0F, FirearmAimRuntime.calculateMaximumConditionSeconds(0),
            "aiming-zero condition cap");
        checkClose(3.25F, FirearmAimRuntime.calculateMaximumConditionSeconds(5),
            "aiming-five condition cap");
        checkClose(2.5F, FirearmAimRuntime.calculateMaximumConditionSeconds(10),
            "aiming-ten condition cap");
        checkClose(2.5F, FirearmAimRuntime.calculateConditionSeconds(0, 25.0F),
            "twenty-five condition points at aiming zero");
        checkClose(1.5625F, FirearmAimRuntime.calculateConditionSeconds(10, 25.0F),
            "twenty-five condition points at aiming ten");
        checkClose(4.0F, FirearmAimRuntime.calculateConditionSeconds(0, 100.0F),
            "condition time is capped at aiming zero");
        checkClose(2.5F, FirearmAimRuntime.calculateConditionSeconds(10, 100.0F),
            "condition time is capped at aiming ten");
        checkClose(
            150.0F,
            FirearmAimRuntime.calculateRequiredAimWork(player, weapon),
            "twenty-five penalty points add two and a half seconds at aiming zero"
        );

        runAimingUpdate(player, 56.25F);
        checkClose(9.375F, player.getAimingDelay(), "conditions must keep stabilization open");
        runAimingUpdate(player, 93.75F);
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
        checkClose(9.375F, player.getAimingDelay(), "moving the same target farther reopens spread");

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

    private static void testStabilizationProgressesHitChanceToGuarantee() {
        resetRuntime();
        IsoPlayer player = createPlayer(15.0F, 0);
        HitInfo target = setTarget(player, 5.0F, new IsoMovingObject(1));
        target.chance = 20;

        FirearmAimRuntime.promoteStabilizationHitChance(player);
        check(target.chance == 20, "zero progress must retain vanilla chance");

        runAimingUpdate(player, 28.125F);
        target.chance = 20;
        FirearmAimRuntime.promoteStabilizationHitChance(player);
        check(target.chance == 40, "half progress must apply the quadratic chance curve");

        runAimingUpdate(player, 28.125F);
        target.chance = 20;
        FirearmAimRuntime.promoteStabilizationHitChance(player);
        check(target.chance == 100, "fully stabilized primary target must be guaranteed");

        target = setTarget(player, 5.0F, new IsoMovingObject(2));
        target.chance = 20;
        FirearmAimRuntime.promoteStabilizationHitChance(player);
        check(target.chance == 27, "new target must use only retained acquisition progress");

        player.setAiming(false);
        target.chance = 20;
        FirearmAimRuntime.promoteStabilizationHitChance(player);
        check(target.chance == 20, "chance promotion applies only while aiming");

        check(
            FirearmAimRuntime.calculatePromotedHitChance(20, 0.25F) == 25,
            "quarter progress"
        );
        check(
            FirearmAimRuntime.calculatePromotedHitChance(20, 0.75F) == 65,
            "three-quarter progress"
        );
        check(
            FirearmAimRuntime.calculatePromotedHitChance(99, 0.75F) == 99,
            "promotion must not round up to a guarantee before full lock"
        );
        check(
            FirearmAimRuntime.calculatePromotedHitChance(100, 0.25F) == 100,
            "natural guarantees must remain guarantees"
        );
    }

    private static void testShotHitChanceUsesPreRecoilStabilizationForResolvedTarget() {
        resetRuntime();
        IsoPlayer player = createPlayer(15.0F, 0);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();
        HitInfo target = setTarget(player, 5.0F, new IsoMovingObject(1));
        runAimingUpdate(player, 56.25F);

        FirearmAimRuntime.captureShotStabilization(player, weapon);
        player.setAimingDelay(1.0F);
        FirearmAimRuntime.synchronizePostShotDelay(player);
        player.setAiming(false);

        target.chance = 20;
        FirearmAimRuntime.promoteStabilizationHitChance(player);
        check(
            target.chance == 100,
            "full-lock shot chance must use the pre-recoil stabilization snapshot"
        );
        FirearmAimRuntime.endShot();

        resetRuntime();
        player = createPlayer(15.0F, 0);
        weapon = (HandWeapon)player.getPrimaryHandItem();
        setTarget(player, 5.0F, new IsoMovingObject(1));
        runAimingUpdate(player, 56.25F);
        FirearmAimRuntime.captureShotStabilization(player, weapon);
        player.setAimingDelay(1.0F);
        FirearmAimRuntime.synchronizePostShotDelay(player);

        target = setTarget(player, 5.0F, new IsoMovingObject(2));
        target.chance = 20;
        FirearmAimRuntime.promoteStabilizationHitChance(player);
        check(
            target.chance == 100,
            "full-lock shot must guarantee the B42.20-resolved ballistic target"
        );
        FirearmAimRuntime.endShot();
    }

    private static void testFullyStabilizedTargetedHeadshotsAreLethal() {
        resetRuntime();
        IsoPlayer player = createPlayer(15.0F, 0);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();
        IsoGameCharacter zombie = new IsoGameCharacter();
        zombie.setZombie(true);
        zombie.setHealth(3.5F);
        setTarget(player, 5.0F, zombie);
        runAimingUpdate(player, 56.25F);

        FirearmAimRuntime.captureShotStabilization(player, weapon);
        FirearmAimRuntime.recordTargetedBodyPart(
            player,
            weapon,
            zombie,
            RagdollBodyPart.BODYPART_HEAD.ordinal()
        );
        checkClose(
            3.5F,
            FirearmAimRuntime.guaranteeLethalHeadshotDamage(
                zombie,
                weapon,
                player,
                false,
                0.4F
            ),
            "fully stabilized targeted zombie headshot"
        );
        checkClose(
            0.4F,
            FirearmAimRuntime.guaranteeLethalHeadshotDamage(
                zombie,
                weapon,
                player,
                false,
                0.4F
            ),
            "lethal marker must be consumed by the matching hit"
        );

        resetRuntime();
        player = createPlayer(15.0F, 0);
        weapon = (HandWeapon)player.getPrimaryHandItem();
        IsoGameCharacter animal = new IsoGameCharacter();
        animal.setAnimal(true);
        animal.setHealth(2.25F);
        setTarget(player, 5.0F, animal);
        runAimingUpdate(player, 56.25F);
        FirearmAimRuntime.captureShotStabilization(player, weapon);
        FirearmAimRuntime.recordTargetedBodyPart(
            player,
            weapon,
            animal,
            RagdollBodyPart.BODYPART_HEAD.ordinal()
        );
        checkClose(
            2.25F,
            FirearmAimRuntime.guaranteeLethalHeadshotDamage(
                animal,
                weapon,
                player,
                false,
                0.4F
            ),
            "fully stabilized targeted animal headshot"
        );

        resetRuntime();
        player = createPlayer(15.0F, 0);
        weapon = (HandWeapon)player.getPrimaryHandItem();
        IsoPlayer otherPlayer = new IsoPlayer();
        otherPlayer.setHealth(2.0F);
        setTarget(player, 5.0F, otherPlayer);
        runAimingUpdate(player, 56.25F);
        FirearmAimRuntime.captureShotStabilization(player, weapon);
        FirearmAimRuntime.recordTargetedBodyPart(
            player,
            weapon,
            otherPlayer,
            RagdollBodyPart.BODYPART_HEAD.ordinal()
        );
        checkClose(
            0.4F,
            FirearmAimRuntime.guaranteeLethalHeadshotDamage(
                otherPlayer,
                weapon,
                player,
                false,
                0.4F
            ),
            "players must not receive lethal headshot promotion"
        );

        resetRuntime();
        player = createPlayer(15.0F, 0);
        weapon = (HandWeapon)player.getPrimaryHandItem();
        zombie = new IsoGameCharacter();
        zombie.setZombie(true);
        zombie.setHealth(3.5F);
        setTarget(player, 5.0F, zombie);
        runAimingUpdate(player, 28.125F);
        FirearmAimRuntime.captureShotStabilization(player, weapon);
        FirearmAimRuntime.recordTargetedBodyPart(
            player,
            weapon,
            zombie,
            RagdollBodyPart.BODYPART_HEAD.ordinal()
        );
        checkClose(
            0.4F,
            FirearmAimRuntime.guaranteeLethalHeadshotDamage(
                zombie,
                weapon,
                player,
                false,
                0.4F
            ),
            "partial stabilization must not become lethal"
        );

        resetRuntime();
        player = createPlayer(15.0F, 0);
        weapon = (HandWeapon)player.getPrimaryHandItem();
        zombie = new IsoGameCharacter();
        zombie.setZombie(true);
        zombie.setHealth(3.5F);
        setTarget(player, 5.0F, zombie);
        runAimingUpdate(player, 56.25F);
        FirearmAimRuntime.captureShotStabilization(player, weapon);
        FirearmAimRuntime.recordTargetedBodyPart(
            player,
            weapon,
            zombie,
            RagdollBodyPart.BODYPART_SPINE.ordinal()
        );
        checkClose(
            0.4F,
            FirearmAimRuntime.guaranteeLethalHeadshotDamage(
                zombie,
                weapon,
                player,
                false,
                0.4F
            ),
            "rerouted failed-damage headshots must not become lethal"
        );

        resetRuntime();
        player = createPlayer(15.0F, 0);
        weapon = (HandWeapon)player.getPrimaryHandItem();
        zombie = new IsoGameCharacter();
        zombie.setZombie(true);
        zombie.setHealth(3.5F);
        setTarget(player, 5.0F, zombie);
        runAimingUpdate(player, 56.25F);
        BallisticsController ballistics = new BallisticsController();
        ballistics.setCachedTargetedBodyPart(
            zombie.getID(),
            RagdollBodyPart.BODYPART_HEAD.ordinal()
        );
        player.setBallisticsController(ballistics);
        FirearmAimRuntime.captureShotStabilization(player, weapon);
        checkClose(
            3.5F,
            FirearmAimRuntime.guaranteeLethalHeadshotDamage(
                zombie,
                weapon,
                player,
                false,
                0.4F
            ),
            "cached B42.20 ballistic head target must be lethal before the callback"
        );

        FirearmAimRuntime.recordTargetedBodyPart(
            player,
            weapon,
            zombie,
            RagdollBodyPart.BODYPART_HEAD.ordinal()
        );
        checkClose(
            0.4F,
            FirearmAimRuntime.guaranteeLethalHeadshotDamage(
                zombie,
                weapon,
                player,
                true,
                0.4F
            ),
            "ignored damage must not become lethal"
        );
        FirearmAimRuntime.endShot();
    }

    private static void testHeadshotDiagnosticsExplainDecision() {
        resetRuntime();
        SandboxOptions.instance.setOptionForTest(
            "CJSFirearmAimingOverhaul.HeadshotDiagnosticLogging",
            new SandboxOptions.BooleanSandboxOption(true)
        );
        IsoPlayer player = createPlayer(15.0F, 0);
        HandWeapon weapon = (HandWeapon)player.getPrimaryHandItem();
        weapon.setFullType("Base.DiagnosticPistol");
        IsoGameCharacter zombie = new IsoGameCharacter();
        zombie.setZombie(true);
        zombie.setHealth(2.0F);
        setTarget(player, 5.0F, zombie);
        runAimingUpdate(player, 56.25F);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            FirearmAimRuntime.captureShotStabilization(player, weapon);
            FirearmAimRuntime.recordTargetedBodyPart(
                player,
                weapon,
                zombie,
                RagdollBodyPart.BODYPART_SPINE.ordinal()
            );
            checkClose(
                0.4F,
                FirearmAimRuntime.guaranteeLethalHeadshotDamage(
                    zombie,
                    weapon,
                    player,
                    false,
                    0.4F
                ),
                "diagnostic spine shot must retain ordinary damage"
            );
            FirearmAimRuntime.endShot();
        } finally {
            System.setOut(originalOut);
        }

        String diagnostic = output.toString(StandardCharsets.UTF_8);
        checkContains(diagnostic, "event=shot_capture", "shot capture diagnostic");
        checkContains(diagnostic, "weapon=Base.DiagnosticPistol", "diagnostic weapon type");
        checkContains(diagnostic, "progress=1.000", "diagnostic stabilization");
        checkContains(diagnostic, "bodyPart=BODYPART_SPINE", "diagnostic body part");
        checkContains(diagnostic, "result=rejected_body_not_head", "body-part rejection");
        checkContains(
            diagnostic,
            "result=rejected_no_accepted_head_marker",
            "damage rejection"
        );
        checkContains(diagnostic, "event=shot_end", "shot-end diagnostic");
        checkContains(diagnostic, "lethalPromoted=false", "diagnostic lethal result");

        resetRuntime();
        SandboxOptions.instance.setOptionForTest(
            "CJSFirearmAimingOverhaul.HeadshotDiagnosticLogging",
            new SandboxOptions.BooleanSandboxOption(false)
        );
        player = createPlayer(0.0F, 0);
        weapon = (HandWeapon)player.getPrimaryHandItem();
        output.reset();
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            FirearmAimRuntime.captureShotStabilization(player, weapon);
            FirearmAimRuntime.endShot();
        } finally {
            System.setOut(originalOut);
        }
        check(output.size() == 0, "disabled headshot diagnostics must stay silent");
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
            FirearmAimingPatches.TargetedBodyPart.class,
            "zombie.CombatManager",
            "processTargetedHit"
        );
        assertPatchTarget(
            FirearmAimingPatches.LethalHeadshot.class,
            "zombie.characters.IsoGameCharacter",
            "hitConsequences"
        );
        assertPatchTarget(
            FirearmAimingPatches.ShotCleanup.class,
            "zombie.CombatManager",
            "attackCollisionCheck"
        );
        assertPatchTarget(
            FirearmAimingPatches.StabilizationHitChance.class,
            "zombie.CombatManager",
            "calculateHitInfoList"
        );
        assertPatchTarget(
            FirearmAimingPatches.ReticleTargetCapture.class,
            "zombie.CombatManager",
            "updateReticle"
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
        assertThrowableExit(FirearmAimingPatches.ShotCleanup.class, "shot scope");

        Method shotEnter = FirearmAimingPatches.PostShotAimingDelay.class.getDeclaredMethod(
            "enter",
            IsoPlayer.class,
            HandWeapon.class
        );
        assertArgument(shotEnter.getParameters()[0], 0, true, "shot player");
        assertArgument(shotEnter.getParameters()[1], 1, true, "shot weapon");

        Method bodyPartEnter = FirearmAimingPatches.TargetedBodyPart.class.getDeclaredMethod(
            "enter",
            HandWeapon.class,
            IsoGameCharacter.class,
            IsoGameCharacter.class,
            RagdollBodyPart.class
        );
        assertArgument(bodyPartEnter.getParameters()[0], 0, true, "targeted weapon");
        assertArgument(bodyPartEnter.getParameters()[1], 1, true, "targeted wielder");
        assertArgument(bodyPartEnter.getParameters()[2], 2, true, "targeted character");
        assertArgument(bodyPartEnter.getParameters()[3], 3, true, "targeted body part");

        Method lethalEnter = FirearmAimingPatches.LethalHeadshot.class.getDeclaredMethod(
            "enter",
            IsoGameCharacter.class,
            HandWeapon.class,
            IsoGameCharacter.class,
            boolean.class,
            float.class
        );
        assertThis(lethalEnter.getParameters()[0], "lethal target");
        assertArgument(lethalEnter.getParameters()[1], 0, true, "lethal weapon");
        assertArgument(lethalEnter.getParameters()[2], 1, true, "lethal wielder");
        assertArgument(lethalEnter.getParameters()[3], 2, true, "lethal ignore flag");
        assertArgument(lethalEnter.getParameters()[4], 3, false, "lethal damage");

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
            FirearmAimingPatches.TargetedBodyPart.class,
            FirearmAimingPatches.LethalHeadshot.class,
            FirearmAimingPatches.ShotCleanup.class,
            FirearmAimingPatches.StabilizationHitChance.class,
            FirearmAimingPatches.ReticleTargetCapture.class,
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
        check(discovered.size() == expected.size(), "ZombieBuddy must discover every aiming patch");
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
        weapon.setFullType("Base.TestFirearm");
        weapon.setAimedFirearm(aimedFirearm);
        weapon.setRanged(aimedFirearm);
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
        SandboxOptions.instance.setOptionForTest(
            "CJSFirearmAimingOverhaul.HeadshotDiagnosticLogging",
            new SandboxOptions.BooleanSandboxOption(false)
        );
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

    private static void checkContains(String text, String expected, String message) {
        check(text.contains(expected), message + ": missing \"" + expected + "\" in:\n" + text);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
