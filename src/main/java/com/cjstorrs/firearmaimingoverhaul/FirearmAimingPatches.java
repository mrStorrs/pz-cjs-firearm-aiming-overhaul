package com.cjstorrs.firearmaimingoverhaul;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.physics.RagdollBodyPart;
import zombie.inventory.types.HandWeapon;
import zombie.network.fields.hit.HitInfo;

public final class FirearmAimingPatches {
    private FirearmAimingPatches() {
    }

    @Patch(className = "zombie.characters.IsoGameCharacter", methodName = "updateAimingDelay")
    public static final class AimingDelayUpdate {
        @Patch.OnEnter
        public static void enter(@Patch.This IsoGameCharacter character) {
            FirearmAimRuntime.beforeAimingDelayUpdate(character);
        }

        @Patch.OnExit
        public static void exit(@Patch.This IsoGameCharacter character) {
            FirearmAimRuntime.afterAimingDelayUpdate(character);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "setAimingDelay")
    public static final class PostShotAimingDelay {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(0) IsoPlayer player,
                @Patch.Argument(1) HandWeapon weapon) {
            FirearmAimRuntime.captureShotStabilization(player, weapon);
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) IsoPlayer player) {
            FirearmAimRuntime.synchronizePostShotDelay(player);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "processTargetedHit")
    public static final class TargetedBodyPart {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(0) HandWeapon weapon,
                @Patch.Argument(1) IsoGameCharacter wielder,
                @Patch.Argument(2) IsoGameCharacter target,
                @Patch.Argument(3) RagdollBodyPart bodyPart) {
            FirearmAimRuntime.recordTargetedBodyPart(
                wielder,
                weapon,
                target,
                bodyPart.ordinal()
            );
        }
    }

    @Patch(className = "zombie.characters.IsoGameCharacter", methodName = "hitConsequences")
    public static final class LethalHeadshot {
        @Patch.OnEnter
        public static void enter(
                @Patch.This IsoGameCharacter target,
                @Patch.Argument(0) HandWeapon weapon,
                @Patch.Argument(1) IsoGameCharacter wielder,
                @Patch.Argument(2) boolean ignoreDamage,
                @Patch.Argument(value = 3, readOnly = false) float damage) {
            damage = FirearmAimRuntime.guaranteeLethalHeadshotDamage(
                target,
                weapon,
                wielder,
                ignoreDamage,
                damage
            );
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "attackCollisionCheck")
    public static final class ShotCleanup {
        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            FirearmAimRuntime.endShot();
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "calculateHitInfoList")
    public static final class StabilizationHitChance {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) IsoGameCharacter owner) {
            FirearmAimRuntime.promoteStabilizationHitChance(owner);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "calculateHitChanceData")
    public static final class HitChanceCalculationScope {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(0) IsoGameCharacter owner,
                @Patch.Argument(1) HandWeapon weapon,
                @Patch.Argument(2) HitInfo hitInfo) {
            FirearmAimRuntime.beginAccuracyCalculation(owner, weapon, hitInfo);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            FirearmAimRuntime.endAccuracyCalculation();
        }
    }

    @Patch(className = "zombie.characters.IsoPlayer", methodName = "calculateCritChance")
    public static final class CriticalChanceCalculationScope {
        @Patch.OnEnter
        public static void enter(
                @Patch.This IsoPlayer player,
                @Patch.Argument(0) IsoGameCharacter target) {
            FirearmAimRuntime.beginCriticalChanceCalculation(player, target);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            FirearmAimRuntime.endAccuracyCalculation();
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "getDistanceModifier")
    public static final class DistanceModifier {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(value = 0, readOnly = false) float distance,
                @Patch.Argument(1) float minimumSightRange,
                @Patch.Argument(2) float maximumSightRange) {
            distance = FirearmAimRuntime.prepareAccuracyDistance(
                distance,
                minimumSightRange,
                maximumSightRange
            );
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "getAimDelayPenalty")
    public static final class AimDelayPenalty {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(1) float distance,
                @Patch.Argument(3) float maximumSightRange,
                @Patch.Return(readOnly = false) float penalty) {
            penalty = FirearmAimRuntime.removeBeyondSightDelayScaling(
                distance,
                maximumSightRange,
                penalty
            );
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "getMovePenalty")
    public static final class MovementPenalty {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) float penalty) {
            penalty = FirearmAimRuntime.convertRecoverablePenalty(penalty);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "getPainPenalty")
    public static final class PainPenalty {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) float penalty) {
            penalty = FirearmAimRuntime.convertRecoverablePenalty(penalty);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "getWeatherPenalty")
    public static final class WeatherPenalty {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) float penalty) {
            penalty = FirearmAimRuntime.convertRecoverablePenalty(penalty);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "getMoodlesPenalty")
    public static final class MoodlesPenalty {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) float penalty) {
            penalty = FirearmAimRuntime.convertRecoverablePenalty(penalty);
        }
    }

    @Patch(className = "zombie.characters.IsoGameCharacter", methodName = "getWornItemsVisionModifier")
    public static final class VisionPenalty {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) float modifier) {
            modifier = FirearmAimRuntime.convertRecoverableVisionModifier(modifier);
        }
    }
}
