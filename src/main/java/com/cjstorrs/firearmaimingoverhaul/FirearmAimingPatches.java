package com.cjstorrs.firearmaimingoverhaul;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.HandWeapon;

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
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) IsoPlayer player) {
            FirearmAimRuntime.synchronizePostShotDelay(player);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "calculateHitChanceData")
    public static final class HitChanceCalculationScope {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(0) IsoGameCharacter owner,
                @Patch.Argument(1) HandWeapon weapon) {
            FirearmAimRuntime.beginAccuracyCalculation(owner, weapon);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            FirearmAimRuntime.endAccuracyCalculation();
        }
    }

    @Patch(className = "zombie.characters.IsoPlayer", methodName = "calculateCritChance")
    public static final class CriticalChanceCalculationScope {
        @Patch.OnEnter
        public static void enter(@Patch.This IsoPlayer player) {
            FirearmAimRuntime.beginCriticalChanceCalculation(player);
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
            distance = FirearmAimRuntime.normalizeBeyondSightAccuracyDistance(
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
            penalty = FirearmAimRuntime.convertBeyondSightPermanentPenalty(penalty);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "getPainPenalty")
    public static final class PainPenalty {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) float penalty) {
            penalty = FirearmAimRuntime.convertBeyondSightPermanentPenalty(penalty);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "getWeatherPenalty")
    public static final class WeatherPenalty {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) float penalty) {
            penalty = FirearmAimRuntime.convertBeyondSightPermanentPenalty(penalty);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "getMoodlesPenalty")
    public static final class MoodlesPenalty {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) float penalty) {
            penalty = FirearmAimRuntime.convertBeyondSightPermanentPenalty(penalty);
        }
    }

    @Patch(className = "zombie.characters.IsoGameCharacter", methodName = "getWornItemsVisionModifier")
    public static final class VisionPenalty {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) float modifier) {
            modifier = FirearmAimRuntime.convertBeyondSightVisionModifier(modifier);
        }
    }
}
