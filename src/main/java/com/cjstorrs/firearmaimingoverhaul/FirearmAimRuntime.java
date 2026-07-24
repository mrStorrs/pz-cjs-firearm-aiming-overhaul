package com.cjstorrs.firearmaimingoverhaul;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.network.fields.hit.HitInfo;
import zombie.util.list.PZArrayList;

public final class FirearmAimRuntime {
    private static final float MINIMUM_DELAY = 0.0001F;
    private static final float RANGE_EPSILON = 0.001F;
    private static final Map<IsoGameCharacter, AimState> AIM_STATES =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<AccuracyScope> ACCURACY_SCOPE =
        ThreadLocal.withInitial(AccuracyScope::new);

    private FirearmAimRuntime() {
    }

    public static void beforeAimingDelayUpdate(IsoGameCharacter character) {
        HandWeapon weapon = getAimedFirearm(character);
        if (!character.isAiming() || weapon == null) {
            AIM_STATES.remove(character);
            return;
        }

        AimState state = AIM_STATES.get(character);
        if (state == null || state.weapon != weapon) {
            state = createState(character, weapon);
            AIM_STATES.put(character, state);
        }

        state.consumePendingPenalty();
        state.multiplier = calculateAimTimeMultiplier(character, weapon);
        float remainingWork = state.getRemainingWork();
        character.setAimingDelay(remainingWork);
        state.workBeforeVanillaUpdate = remainingWork;
        state.updatePending = true;
    }

    public static void afterAimingDelayUpdate(IsoGameCharacter character) {
        AimState state = AIM_STATES.get(character);
        if (state == null || !state.updatePending) {
            return;
        }

        state.updatePending = false;
        float remainingWork = character.getAimingDelay();
        float completedWork = Math.max(0.0F, state.workBeforeVanillaUpdate - remainingWork);
        state.completedWork += completedWork;
        character.setAimingDelay(state.getEffectiveDelay());
    }

    public static void synchronizePostShotDelay(IsoPlayer player) {
        AimState state = AIM_STATES.get(player);
        if (state == null || state.weapon != getAimedFirearm(player)) {
            return;
        }

        state.multiplier = calculateAimTimeMultiplier(player, state.weapon);
        state.completedWork = (state.baseDelay - player.getAimingDelay()) * state.multiplier;
        state.updatePending = false;
    }

    public static float calculateAimTimeMultiplier(IsoGameCharacter character, HandWeapon weapon) {
        if (weapon == null || !weapon.isAimedFirearm()) {
            return 1.0F;
        }

        PZArrayList<HitInfo> hitInfoList = character.getHitInfoList();
        if (hitInfoList == null || hitInfoList.isEmpty()) {
            return 1.0F;
        }

        float minimumDistanceSquared = Float.MAX_VALUE;
        for (int index = 0; index < hitInfoList.size(); index++) {
            minimumDistanceSquared = Math.min(minimumDistanceSquared, hitInfoList.get(index).distSq);
        }

        float distance = (float)Math.sqrt(Math.max(0.0F, minimumDistanceSquared));
        float sightRange = weapon.getMaxSightRange(character);
        float physicalRange = getPhysicalRange(character, weapon);
        if (sightRange > physicalRange + RANGE_EPSILON) {
            return 1.0F / calculateRangeCurveMultiplier(sightRange - physicalRange);
        }
        if (distance <= sightRange || physicalRange <= sightRange + RANGE_EPSILON) {
            return 1.0F;
        }

        float distanceMultiplier = calculateRangeCurveMultiplier(distance - sightRange);
        AimState state = AIM_STATES.get(character);
        if (state == null || state.weapon != weapon) {
            return distanceMultiplier;
        }

        return distanceMultiplier + state.recoverablePenalty / state.baseDelay;
    }

    public static void guaranteeFullyStabilizedHit(IsoGameCharacter character) {
        if (!character.isAiming() || getAimedFirearm(character) == null) {
            return;
        }

        PZArrayList<HitInfo> hitInfoList = character.getHitInfoList();
        if (hitInfoList == null || hitInfoList.isEmpty() || !isFullyStabilized(character)) {
            return;
        }

        hitInfoList.get(0).chance = 100;
    }

    public static void beginAccuracyCalculation(IsoGameCharacter owner, HandWeapon weapon) {
        AccuracyScope scope = ACCURACY_SCOPE.get();
        if (scope.depth++ == 0) {
            scope.active = weapon != null && weapon.isAimedFirearm();
            scope.owner = owner;
            scope.weapon = weapon;
            scope.physicalRange = scope.active ? getPhysicalRange(owner, weapon) : 0.0F;
            scope.beyondSight = false;
            scope.targetDistance = Float.MAX_VALUE;
            scope.recoverablePenalty = 0.0F;
        }
    }

    public static void beginCriticalChanceCalculation(IsoPlayer player) {
        beginAccuracyCalculation(player, getAimedFirearm(player));
    }

    public static void endAccuracyCalculation() {
        AccuracyScope scope = ACCURACY_SCOPE.get();
        if (scope.depth <= 1) {
            captureRecoverablePenalty(scope);
            scope.depth = 0;
            scope.active = false;
            scope.owner = null;
            scope.weapon = null;
            scope.physicalRange = 0.0F;
            scope.beyondSight = false;
            scope.targetDistance = Float.MAX_VALUE;
            scope.recoverablePenalty = 0.0F;
        } else {
            scope.depth--;
        }
    }

    public static float normalizeBeyondSightAccuracyDistance(
            float distance,
            float minimumSightRange,
            float maximumSightRange) {
        if (!isBeyondSightWithinPhysicalRange(distance, maximumSightRange)) {
            return distance;
        }

        AccuracyScope scope = ACCURACY_SCOPE.get();
        scope.beyondSight = true;
        scope.targetDistance = distance;
        if (minimumSightRange < 0.0F || minimumSightRange >= maximumSightRange) {
            return maximumSightRange;
        }

        return minimumSightRange + (maximumSightRange - minimumSightRange) * 0.5F;
    }

    public static float convertBeyondSightPermanentPenalty(float penalty) {
        AccuracyScope scope = ACCURACY_SCOPE.get();
        if (!scope.beyondSight || !Float.isFinite(penalty) || penalty <= 0.0F) {
            return penalty;
        }

        scope.recoverablePenalty += penalty;
        return 0.0F;
    }

    public static float convertBeyondSightVisionModifier(float modifier) {
        AccuracyScope scope = ACCURACY_SCOPE.get();
        if (!scope.beyondSight || !Float.isFinite(modifier) || modifier <= 1.0F) {
            return modifier;
        }

        scope.recoverablePenalty += 100.0F - 100.0F / modifier;
        return 1.0F;
    }

    public static float removeBeyondSightDelayScaling(
            float distance,
            float maximumSightRange,
            float vanillaPenalty) {
        if (!isBeyondSightWithinPhysicalRange(distance, maximumSightRange)) {
            return vanillaPenalty;
        }

        float vanillaDistanceFactor = 1.0F + (distance - maximumSightRange) * 0.1F;
        return vanillaDistanceFactor > 0.0F ? vanillaPenalty / vanillaDistanceFactor : vanillaPenalty;
    }

    static void resetForTest() {
        AIM_STATES.clear();
        ACCURACY_SCOPE.remove();
    }

    private static AimState createState(IsoGameCharacter character, HandWeapon weapon) {
        float currentDelay = character.getAimingDelay();
        float baseDelay = currentDelay > MINIMUM_DELAY
            ? currentDelay
            : Math.max(MINIMUM_DELAY, (float)weapon.getAimingTime());
        AimState state = new AimState(weapon, baseDelay);
        state.completedWork = baseDelay - currentDelay;
        return state;
    }

    private static void captureRecoverablePenalty(AccuracyScope scope) {
        if (!scope.active || !scope.beyondSight || scope.owner == null || scope.weapon == null) {
            return;
        }

        AimState state = AIM_STATES.get(scope.owner);
        if (state == null || state.weapon != scope.weapon) {
            state = createState(scope.owner, scope.weapon);
            AIM_STATES.put(scope.owner, state);
        }

        state.capturePendingPenalty(scope.targetDistance, scope.recoverablePenalty);
    }

    private static HandWeapon getAimedFirearm(IsoGameCharacter character) {
        InventoryItem item = character.getPrimaryHandItem();
        return item instanceof HandWeapon weapon && weapon.isAimedFirearm() ? weapon : null;
    }

    private static float getPhysicalRange(IsoGameCharacter character, HandWeapon weapon) {
        return weapon.getMaxRange(character) * weapon.getRangeMod(character);
    }

    private static float calculateRangeCurveMultiplier(float rangeDifference) {
        float curveProgress = Math.min(
            1.0F,
            Math.max(0.0F, rangeDifference) / FirearmAimSettings.getFullPenaltyDistanceTiles()
        );
        return 1.0F
            + (FirearmAimSettings.getMaximumMultiplier() - 1.0F)
                * (float)Math.pow(curveProgress, FirearmAimSettings.getCurveExponent());
    }

    private static boolean isFullyStabilized(IsoGameCharacter character) {
        AimState state = AIM_STATES.get(character);
        HandWeapon weapon = getAimedFirearm(character);
        if (state == null || state.weapon != weapon) {
            return character.getAimingDelay() <= MINIMUM_DELAY;
        }

        state.consumePendingPenalty();
        state.multiplier = calculateAimTimeMultiplier(character, weapon);
        return state.getRemainingWork() <= MINIMUM_DELAY;
    }

    private static boolean isBeyondSightWithinPhysicalRange(float distance, float maximumSightRange) {
        AccuracyScope scope = ACCURACY_SCOPE.get();
        return scope.active
            && distance > maximumSightRange
            && distance <= scope.physicalRange + RANGE_EPSILON;
    }

    private static final class AimState {
        private final HandWeapon weapon;
        private final float baseDelay;
        private float completedWork;
        private float multiplier = 1.0F;
        private float workBeforeVanillaUpdate;
        private boolean updatePending;
        private float recoverablePenalty;
        private float pendingPenalty;
        private float pendingPenaltyDistance = Float.MAX_VALUE;
        private boolean hasPendingPenalty;

        private AimState(HandWeapon weapon, float baseDelay) {
            this.weapon = weapon;
            this.baseDelay = baseDelay;
        }

        private float getEffectiveDelay() {
            return Math.max(0.0F, this.baseDelay - this.completedWork / this.multiplier);
        }

        private float getRemainingWork() {
            return Math.max(0.0F, this.baseDelay * this.multiplier - this.completedWork);
        }

        private void capturePendingPenalty(float distance, float penalty) {
            if (!this.hasPendingPenalty || distance < this.pendingPenaltyDistance - RANGE_EPSILON) {
                this.pendingPenaltyDistance = distance;
                this.pendingPenalty = penalty;
                this.hasPendingPenalty = true;
            } else if (Math.abs(distance - this.pendingPenaltyDistance) <= RANGE_EPSILON) {
                this.pendingPenalty = Math.max(this.pendingPenalty, penalty);
            }
        }

        private void consumePendingPenalty() {
            if (!this.hasPendingPenalty) {
                return;
            }

            this.recoverablePenalty = this.pendingPenalty;
            this.pendingPenalty = 0.0F;
            this.pendingPenaltyDistance = Float.MAX_VALUE;
            this.hasPendingPenalty = false;
        }
    }

    private static final class AccuracyScope {
        private int depth;
        private boolean active;
        private IsoGameCharacter owner;
        private HandWeapon weapon;
        private float physicalRange;
        private boolean beyondSight;
        private float targetDistance = Float.MAX_VALUE;
        private float recoverablePenalty;
    }
}
