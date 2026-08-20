package com.cjstorrs.firearmaimingoverhaul;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.skills.PerkFactory;
import zombie.core.physics.BallisticsController;
import zombie.core.physics.RagdollBodyPart;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoMovingObject;
import zombie.network.fields.hit.HitInfo;
import zombie.util.list.PZArrayList;

public final class FirearmAimRuntime {
    private static final float MINIMUM_DELAY = 0.0001F;
    private static final float RANGE_EPSILON = 0.001F;
    private static final float VANILLA_WORK_PER_SECOND = 37.5F;
    private static final float MINIMUM_LOCK_SECONDS_AT_LEVEL_ZERO = 1.5F;
    private static final float MINIMUM_LOCK_SECONDS_PER_LEVEL = 0.08F;
    private static final float CLEAN_MAXIMUM_SKILL_REDUCTION_PER_LEVEL = 0.025F;
    private static final float FAR_ENTRY_SECONDS_AT_LEVEL_ZERO = 0.5F;
    private static final float FAR_ENTRY_SECONDS_PER_LEVEL = 0.02F;
    private static final float FULL_CONDITION_PENALTY_POINTS = 40.0F;
    private static final float CONDITION_MAXIMUM_SKILL_REDUCTION_PER_LEVEL = 0.0375F;
    private static final float TARGET_PROGRESS_RETENTION_AT_LEVEL_ZERO = 0.30F;
    private static final float TARGET_PROGRESS_RETENTION_PER_LEVEL = 0.04F;
    private static final float MINIMUM_TARGET_REACQUIRE_SECONDS = 0.35F;
    private static final float RECOIL_REOPEN_AT_LEVEL_ZERO = 0.45F;
    private static final float RECOIL_REOPEN_PER_LEVEL = 0.02F;
    private static final float EXCESS_SIGHT_BONUS_PER_TILE = 0.02F;
    private static final float MINIMUM_EXCESS_SIGHT_ACQUISITION_MULTIPLIER = 0.80F;
    private static final long NO_TARGET_KEY = 0L;
    private static final long OBJECT_TARGET_NAMESPACE = 1L << 62;
    private static final long HIT_INFO_TARGET_NAMESPACE = 1L << 61;
    private static final String HEADSHOT_DIAGNOSTIC_PREFIX =
        "[cjsFirearmAimingOverhaul][headshot-debug] ";
    private static final AtomicLong SHOT_IDS = new AtomicLong();
    private static final Map<IsoGameCharacter, AimState> AIM_STATES =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<AccuracyScope> ACCURACY_SCOPE =
        ThreadLocal.withInitial(AccuracyScope::new);
    private static final ThreadLocal<ShotScope> SHOT_SCOPE =
        ThreadLocal.withInitial(ShotScope::new);

    private FirearmAimRuntime() {
    }

    public static void beforeAimingDelayUpdate(IsoGameCharacter character) {
        HandWeapon weapon = getAimedFirearm(character);
        if (!character.isAiming() || weapon == null) {
            AIM_STATES.remove(character);
            return;
        }

        AimState state = getOrCreateState(character, weapon);
        refreshRequirement(character, state);
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
        state.completedWork = Math.min(state.requiredWork, state.completedWork + completedWork);
        character.setAimingDelay(state.getEffectiveDelay());
    }

    public static void synchronizePostShotDelay(IsoPlayer player) {
        AimState state = AIM_STATES.get(player);
        if (state == null || state.weapon != getAimedFirearm(player)) {
            return;
        }

        refreshRequirement(player, state);
        int aimingLevel = getAimingLevel(player);
        float vanillaCompletion = clamp01(1.0F - player.getAimingDelay() / state.baseDelay);
        float minimumReopen = calculateRecoilReopenFraction(aimingLevel);
        float completionCeiling = 1.0F - minimumReopen;
        state.completedWork = state.requiredWork * Math.min(vanillaCompletion, completionCeiling);
        state.updatePending = false;
        player.setAimingDelay(state.getEffectiveDelay());
    }

    public static void captureShotStabilization(IsoPlayer player, HandWeapon weapon) {
        ShotScope scope = SHOT_SCOPE.get();
        scope.reset();
        scope.shotId = SHOT_IDS.incrementAndGet();
        scope.diagnosticLogging = FirearmAimSettings.isHeadshotDiagnosticLoggingEnabled();
        scope.owner = player;
        scope.weapon = weapon;
        scope.stabilizationProgress = getStabilizationProgress(player);
        scope.fullyStabilized = weapon != null
            && weapon.isAimedFirearm()
            && weapon == getAimedFirearm(player)
            && scope.stabilizationProgress >= 1.0F;

        AimState state = AIM_STATES.get(player);
        scope.completedWork = state == null ? Float.NaN : state.completedWork;
        scope.requiredWork = state == null ? Float.NaN : state.requiredWork;
        TargetProfile primaryTarget = getPrimaryTarget(player);
        scope.primaryTargetKey = primaryTarget.key;
        scope.primaryTargetDistance = primaryTarget.distance;
        scope.outcome = scope.fullyStabilized
            ? "awaiting_targeted_body_part"
            : "not_fully_stabilized";

        if (scope.diagnosticLogging) {
            logHeadshotDiagnostic(
                "event=shot_capture"
                    + " shot=" + scope.shotId
                    + " ownerId=" + player.getID()
                    + " weapon=" + getWeaponType(weapon)
                    + " progress=" + formatDiagnosticFloat(scope.stabilizationProgress)
                    + " fullyStabilized=" + scope.fullyStabilized
                    + " aimingDelay=" + formatDiagnosticFloat(player.getAimingDelay())
                    + " completedWork=" + formatDiagnosticFloat(scope.completedWork)
                    + " requiredWork=" + formatDiagnosticFloat(scope.requiredWork)
                    + " targetKey=" + scope.primaryTargetKey
                    + " targetDistance=" + formatDiagnosticFloat(scope.primaryTargetDistance)
            );
        }
    }

    public static void recordTargetedBodyPart(
            IsoGameCharacter wielder,
            HandWeapon weapon,
            IsoGameCharacter target,
            int bodyPart) {
        ShotScope scope = SHOT_SCOPE.get();
        scope.bodyPartReported = true;
        scope.reportedBodyPart = bodyPart;
        scope.reportedTarget = target;

        String result;
        if (!scope.fullyStabilized) {
            result = "rejected_not_fully_stabilized";
        } else if (scope.owner != wielder) {
            result = "rejected_owner_mismatch";
        } else if (scope.weapon != weapon) {
            result = "rejected_weapon_mismatch";
        } else if (target == null) {
            result = "rejected_null_target";
        } else if (!RagdollBodyPart.isHead(bodyPart)) {
            result = "rejected_body_not_head";
        } else {
            scope.targetedHead = target;
            result = "accepted_head_marker";
        }
        scope.outcome = result;

        if (scope.diagnosticLogging) {
            logHeadshotDiagnostic(
                "event=targeted_body_part"
                    + " shot=" + scope.shotId
                    + " targetId=" + getTargetId(target)
                    + " targetType=" + getTargetType(target)
                    + " bodyPart=" + getBodyPartName(bodyPart)
                    + " result=" + result
            );
        }
    }

    public static float guaranteeLethalHeadshotDamage(
            IsoGameCharacter target,
            HandWeapon weapon,
            IsoGameCharacter wielder,
            boolean ignoreDamage,
            float damage) {
        ShotScope scope = SHOT_SCOPE.get();
        scope.damageChecks++;
        float targetHealth = target == null ? Float.NaN : target.getHealth();
        float resultDamage = damage;
        boolean promoted = false;
        String result;

        if (!scope.fullyStabilized) {
            result = "rejected_not_fully_stabilized";
        } else if (scope.owner != wielder) {
            result = "rejected_owner_mismatch";
        } else if (scope.weapon != weapon) {
            result = "rejected_weapon_mismatch";
        } else if (target == null) {
            result = "rejected_null_target";
        } else {
            boolean targetedHead = scope.targetedHead == target
                || hasCachedTargetedHead(wielder, target);
            if (!targetedHead) {
                result = "rejected_no_accepted_head_marker";
            } else if (ignoreDamage) {
                result = "rejected_ignore_damage";
            } else if (!target.isZombie() && !target.isAnimal()) {
                result = "rejected_ineligible_target_type";
            } else {
                scope.targetedHead = null;
                resultDamage = Math.max(damage, targetHealth);
                promoted = true;
                result = "promoted_lethal_headshot";
            }
        }

        scope.lethalPromoted |= promoted;
        scope.outcome = result;
        if (scope.diagnosticLogging) {
            logHeadshotDiagnostic(
                "event=damage_decision"
                    + " shot=" + scope.shotId
                    + " targetId=" + getTargetId(target)
                    + " targetType=" + getTargetType(target)
                    + " ignoreDamage=" + ignoreDamage
                    + " incomingDamage=" + formatDiagnosticFloat(damage)
                    + " targetHealth=" + formatDiagnosticFloat(targetHealth)
                    + " outgoingDamage=" + formatDiagnosticFloat(resultDamage)
                    + " result=" + result
            );
        }

        return resultDamage;
    }

    public static void endShot() {
        ShotScope scope = SHOT_SCOPE.get();
        if (scope.diagnosticLogging) {
            logHeadshotDiagnostic(
                "event=shot_end"
                    + " shot=" + scope.shotId
                    + " bodyPartReported=" + scope.bodyPartReported
                    + " bodyPart=" + getBodyPartName(scope.reportedBodyPart)
                    + " bodyTargetId=" + getTargetId(scope.reportedTarget)
                    + " damageChecks=" + scope.damageChecks
                    + " lethalPromoted=" + scope.lethalPromoted
                    + " outcome=" + scope.outcome
            );
        }
        SHOT_SCOPE.remove();
    }

    public static void promoteStabilizationHitChance(IsoGameCharacter character) {
        HandWeapon weapon = getAimedFirearm(character);
        ShotScope shot = SHOT_SCOPE.get();
        boolean capturedShot = shot.shotId != 0L
            && shot.owner == character
            && shot.weapon == weapon;
        if (weapon == null) {
            return;
        }

        PZArrayList<HitInfo> hitInfoList = character.getHitInfoList();
        if (hitInfoList == null || hitInfoList.isEmpty()) {
            return;
        }

        HitInfo primaryTarget = hitInfoList.get(0);
        if (!capturedShot && !character.isAiming()) {
            return;
        }

        int originalChance = primaryTarget.chance;
        float progress = capturedShot
            ? shot.stabilizationProgress
            : getStabilizationProgress(character);
        primaryTarget.chance = calculatePromotedHitChance(
            originalChance,
            progress
        );

        if (capturedShot && shot.diagnosticLogging) {
            logHeadshotDiagnostic(
                "event=hit_chance"
                    + " shot=" + shot.shotId
                    + " source=" + (capturedShot
                        ? "pre_recoil_snapshot"
                        : "live_target_state")
                    + " progress=" + formatDiagnosticFloat(progress)
                    + " incomingChance=" + originalChance
                    + " outgoingChance=" + primaryTarget.chance
            );
        }
    }

    public static void beginAccuracyCalculation(
            IsoGameCharacter owner,
            HandWeapon weapon,
            HitInfo hitInfo) {
        AccuracyScope scope = ACCURACY_SCOPE.get();
        if (scope.depth++ == 0) {
            scope.active = weapon != null && weapon.isAimedFirearm();
            scope.owner = owner;
            scope.weapon = weapon;
            scope.physicalRange = scope.active ? getPhysicalRange(owner, weapon) : 0.0F;
            scope.hasTarget = hitInfo != null;
            scope.targetKey = hitInfo == null ? NO_TARGET_KEY : getTargetKey(hitInfo);
            scope.convertPenalties = false;
            scope.targetDistance = Float.MAX_VALUE;
            scope.recoverablePenalty = 0.0F;
        }
    }

    public static void beginCriticalChanceCalculation(
            IsoPlayer player,
            IsoGameCharacter target) {
        AccuracyScope scope = ACCURACY_SCOPE.get();
        if (scope.depth++ == 0) {
            HandWeapon weapon = getAimedFirearm(player);
            scope.active = weapon != null;
            scope.owner = player;
            scope.weapon = weapon;
            scope.physicalRange = scope.active ? getPhysicalRange(player, weapon) : 0.0F;
            scope.hasTarget = target != null;
            scope.targetKey = target == null ? NO_TARGET_KEY : getTargetKey(target);
            scope.convertPenalties = false;
            scope.targetDistance = Float.MAX_VALUE;
            scope.recoverablePenalty = 0.0F;
        }
    }

    public static void endAccuracyCalculation() {
        AccuracyScope scope = ACCURACY_SCOPE.get();
        if (scope.depth <= 1) {
            captureRecoverablePenalty(scope);
            scope.reset();
        } else {
            scope.depth--;
        }
    }

    public static float prepareAccuracyDistance(
            float distance,
            float minimumSightRange,
            float maximumSightRange) {
        AccuracyScope scope = ACCURACY_SCOPE.get();
        if (!scope.active
                || !scope.hasTarget
                || distance > scope.physicalRange + RANGE_EPSILON) {
            return distance;
        }

        scope.convertPenalties = true;
        scope.targetDistance = distance;
        if (distance <= maximumSightRange) {
            return distance;
        }

        if (minimumSightRange < 0.0F || minimumSightRange >= maximumSightRange) {
            return maximumSightRange;
        }

        return minimumSightRange + (maximumSightRange - minimumSightRange) * 0.5F;
    }

    public static float convertRecoverablePenalty(float penalty) {
        AccuracyScope scope = ACCURACY_SCOPE.get();
        if (!scope.convertPenalties || !Float.isFinite(penalty) || penalty <= 0.0F) {
            return penalty;
        }

        scope.recoverablePenalty += penalty;
        return 0.0F;
    }

    public static float convertRecoverableVisionModifier(float modifier) {
        AccuracyScope scope = ACCURACY_SCOPE.get();
        if (!scope.convertPenalties || !Float.isFinite(modifier) || modifier <= 1.0F) {
            return modifier;
        }

        scope.recoverablePenalty += 100.0F - 100.0F / modifier;
        return 1.0F;
    }

    public static float removeBeyondSightDelayScaling(
            float distance,
            float maximumSightRange,
            float vanillaPenalty) {
        AccuracyScope scope = ACCURACY_SCOPE.get();
        if (!scope.active
                || !scope.hasTarget
                || distance <= maximumSightRange
                || distance > scope.physicalRange + RANGE_EPSILON) {
            return vanillaPenalty;
        }

        float vanillaDistanceFactor = 1.0F + (distance - maximumSightRange) * 0.1F;
        return vanillaDistanceFactor > 0.0F ? vanillaPenalty / vanillaDistanceFactor : vanillaPenalty;
    }

    static float calculateMinimumLockSeconds(int aimingLevel) {
        return MINIMUM_LOCK_SECONDS_AT_LEVEL_ZERO
            - MINIMUM_LOCK_SECONDS_PER_LEVEL * clampAimingLevel(aimingLevel);
    }

    static float calculateMaximumCleanAimSeconds(int aimingLevel) {
        return FirearmAimSettings.getMaximumCleanAimSeconds()
            * (1.0F - CLEAN_MAXIMUM_SKILL_REDUCTION_PER_LEVEL
                * clampAimingLevel(aimingLevel));
    }

    static float calculateMaximumConditionSeconds(int aimingLevel) {
        return FirearmAimSettings.getMaximumConditionSeconds()
            * (1.0F - CONDITION_MAXIMUM_SKILL_REDUCTION_PER_LEVEL
                * clampAimingLevel(aimingLevel));
    }

    static float calculateConditionSeconds(int aimingLevel, float penaltyPoints) {
        return calculateMaximumConditionSeconds(aimingLevel)
            * clamp01(Math.max(0.0F, penaltyPoints) / FULL_CONDITION_PENALTY_POINTS);
    }

    static float calculateTargetProgressRetention(int aimingLevel) {
        return TARGET_PROGRESS_RETENTION_AT_LEVEL_ZERO
            + TARGET_PROGRESS_RETENTION_PER_LEVEL * clampAimingLevel(aimingLevel);
    }

    static float calculateRecoilReopenFraction(int aimingLevel) {
        return RECOIL_REOPEN_AT_LEVEL_ZERO
            - RECOIL_REOPEN_PER_LEVEL * clampAimingLevel(aimingLevel);
    }

    static int calculatePromotedHitChance(int vanillaChance, float progress) {
        int clampedChance = Math.max(0, Math.min(100, vanillaChance));
        float clampedProgress = clamp01(progress);
        if (clampedChance == 100 || clampedProgress >= 1.0F) {
            return 100;
        }

        float curvedProgress = clampedProgress * clampedProgress;
        int promotedChance = Math.round(
            clampedChance + (100 - clampedChance) * curvedProgress
        );
        return Math.min(99, promotedChance);
    }

    static float calculateExcessSightAcquisitionMultiplier(float excessSightTiles) {
        return Math.max(
            MINIMUM_EXCESS_SIGHT_ACQUISITION_MULTIPLIER,
            1.0F - EXCESS_SIGHT_BONUS_PER_TILE * Math.max(0.0F, excessSightTiles)
        );
    }

    static float calculateCleanAimSeconds(
            int aimingLevel,
            float baseSeconds,
            float targetDistance,
            float sightRange,
            float physicalRange) {
        float gap = physicalRange - sightRange;
        if (gap <= RANGE_EPSILON || targetDistance <= sightRange) {
            return baseSeconds;
        }

        float progress = clamp01((targetDistance - sightRange) / gap);
        float gapWeight = (float)Math.sqrt(
            Math.min(1.0F, gap / FirearmAimSettings.getReferenceGapTiles())
        );
        float maximumCleanSeconds = Math.max(
            baseSeconds,
            calculateMaximumCleanAimSeconds(aimingLevel)
        );
        float availableFarSeconds = (maximumCleanSeconds - baseSeconds) * gapWeight;
        float entrySeconds = Math.min(
            availableFarSeconds,
            FAR_ENTRY_SECONDS_AT_LEVEL_ZERO
                - FAR_ENTRY_SECONDS_PER_LEVEL * clampAimingLevel(aimingLevel)
        );
        float progressiveSeconds = Math.max(0.0F, availableFarSeconds - entrySeconds)
            * (float)Math.pow(progress, FirearmAimSettings.getFarProgressExponent());
        return baseSeconds + entrySeconds + progressiveSeconds;
    }

    static float calculateRequiredAimWork(IsoGameCharacter character, HandWeapon weapon) {
        AimState state = getOrCreateState(character, weapon);
        refreshRequirement(character, state);
        return state.requiredWork;
    }

    static void resetForTest() {
        AIM_STATES.clear();
        ACCURACY_SCOPE.remove();
        SHOT_SCOPE.remove();
        SHOT_IDS.set(0L);
    }

    private static void logHeadshotDiagnostic(String message) {
        System.out.println(HEADSHOT_DIAGNOSTIC_PREFIX + message);
    }

    private static String formatDiagnosticFloat(float value) {
        return Float.isFinite(value)
            ? String.format(Locale.ROOT, "%.3f", value)
            : "n/a";
    }

    private static String getWeaponType(HandWeapon weapon) {
        if (weapon == null) {
            return "none";
        }
        String fullType = weapon.getFullType();
        return fullType == null || fullType.isEmpty() ? "unknown" : fullType;
    }

    private static int getTargetId(IsoGameCharacter target) {
        return target == null ? -1 : target.getID();
    }

    private static String getTargetType(IsoGameCharacter target) {
        if (target == null) {
            return "none";
        }
        if (target.isZombie()) {
            return "zombie";
        }
        if (target.isAnimal()) {
            return "animal";
        }
        if (target instanceof IsoPlayer) {
            return "player";
        }
        return "character";
    }

    private static String getBodyPartName(int bodyPart) {
        RagdollBodyPart[] bodyParts = RagdollBodyPart.values();
        if (bodyPart < 0 || bodyPart >= bodyParts.length) {
            return "NONE";
        }
        return bodyParts[bodyPart].name();
    }

    private static AimState getOrCreateState(IsoGameCharacter character, HandWeapon weapon) {
        AimState state = AIM_STATES.get(character);
        if (state == null || state.weapon != weapon) {
            state = createState(character, weapon);
            AIM_STATES.put(character, state);
        }
        return state;
    }

    private static AimState createState(IsoGameCharacter character, HandWeapon weapon) {
        float baseDelay = Math.max(MINIMUM_DELAY, (float)weapon.getAimingTime());
        AimState state = new AimState(weapon, baseDelay);
        state.completedWork = 0.0F;
        TargetProfile target = getPrimaryTarget(character);
        state.targetKey = target.key;
        state.targetInitialized = true;
        return state;
    }

    private static void refreshRequirement(IsoGameCharacter character, AimState state) {
        TargetProfile target = getPrimaryTarget(character);
        boolean targetChanged = state.targetInitialized && state.targetKey != target.key;
        state.consumePendingPenalty(target.key, targetChanged);

        int aimingLevel = getAimingLevel(character);
        float workRate = getWorkRate(aimingLevel);
        float baseWork = Math.max(
            state.baseDelay,
            calculateMinimumLockSeconds(aimingLevel) * workRate
        );
        float sightRange = state.weapon.getMaxSightRange(character);
        float physicalRange = getPhysicalRange(character, state.weapon);
        if (sightRange > physicalRange + RANGE_EPSILON) {
            baseWork *= calculateExcessSightAcquisitionMultiplier(sightRange - physicalRange);
        }

        float cleanWork = calculateCleanAimSeconds(
            aimingLevel,
            baseWork / workRate,
            target.distance,
            sightRange,
            physicalRange
        ) * workRate;
        float conditionWork = calculateConditionSeconds(
            aimingLevel,
            state.recoverablePenalty
        ) * workRate;
        float requiredWork = Math.max(MINIMUM_DELAY, cleanWork + conditionWork);
        state.applyRequirement(target.key, requiredWork, aimingLevel, workRate);
    }

    private static void captureRecoverablePenalty(AccuracyScope scope) {
        if (!scope.active
                || !scope.hasTarget
                || !scope.convertPenalties
                || scope.owner == null
                || scope.weapon == null) {
            return;
        }

        AimState state = getOrCreateState(scope.owner, scope.weapon);
        state.capturePendingPenalty(
            scope.targetKey,
            scope.targetDistance,
            scope.recoverablePenalty
        );
    }

    private static TargetProfile getPrimaryTarget(IsoGameCharacter character) {
        PZArrayList<HitInfo> hitInfoList = character.getHitInfoList();
        if (hitInfoList == null || hitInfoList.isEmpty()) {
            return TargetProfile.NONE;
        }

        HitInfo hitInfo = hitInfoList.get(0);
        float distance = (float)Math.sqrt(Math.max(0.0F, hitInfo.distSq));
        return new TargetProfile(getTargetKey(hitInfo), distance);
    }

    private static boolean hasCachedTargetedHead(
            IsoGameCharacter wielder,
            IsoGameCharacter target) {
        BallisticsController ballistics = wielder.getBallisticsController();
        return ballistics != null
            && ballistics.isCameraTarget(target.getID())
            && RagdollBodyPart.isHead(
                ballistics.getCachedTargetedBodyPart(target.getID())
            );
    }

    private static long getTargetKey(HitInfo hitInfo) {
        IsoMovingObject object = hitInfo.getObject();
        if (object != null) {
            return getTargetKey(object);
        }
        return HIT_INFO_TARGET_NAMESPACE | (System.identityHashCode(hitInfo) & 0xffffffffL);
    }

    private static long getTargetKey(IsoMovingObject object) {
        return OBJECT_TARGET_NAMESPACE | (object.getID() & 0xffffffffL);
    }

    private static HandWeapon getAimedFirearm(IsoGameCharacter character) {
        InventoryItem item = character.getPrimaryHandItem();
        return item instanceof HandWeapon weapon && weapon.isAimedFirearm() ? weapon : null;
    }

    private static float getPhysicalRange(IsoGameCharacter character, HandWeapon weapon) {
        return weapon.getMaxRange(character) * weapon.getRangeMod(character);
    }

    private static int getAimingLevel(IsoGameCharacter character) {
        return clampAimingLevel(character.getPerkLevel(PerkFactory.Perks.Aiming));
    }

    private static int clampAimingLevel(int aimingLevel) {
        return Math.max(0, Math.min(10, aimingLevel));
    }

    private static float getWorkRate(int aimingLevel) {
        return VANILLA_WORK_PER_SECOND * (1.0F + 0.05F * clampAimingLevel(aimingLevel));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float getStabilizationProgress(IsoGameCharacter character) {
        AimState state = AIM_STATES.get(character);
        HandWeapon weapon = getAimedFirearm(character);
        if (state == null || state.weapon != weapon) {
            return character.getAimingDelay() <= MINIMUM_DELAY ? 1.0F : 0.0F;
        }

        refreshRequirement(character, state);
        if (state.requiredWork <= MINIMUM_DELAY) {
            return 1.0F;
        }
        return clamp01(state.completedWork / state.requiredWork);
    }

    private static final class AimState {
        private final HandWeapon weapon;
        private final float baseDelay;
        private float requiredWork;
        private float completedWork;
        private float workBeforeVanillaUpdate;
        private boolean updatePending;
        private long targetKey;
        private boolean targetInitialized;
        private float recoverablePenalty;
        private long pendingPenaltyTargetKey;
        private float pendingPenalty;
        private float pendingPenaltyDistance = Float.MAX_VALUE;
        private boolean hasPendingPenalty;

        private AimState(HandWeapon weapon, float baseDelay) {
            this.weapon = weapon;
            this.baseDelay = baseDelay;
            this.requiredWork = baseDelay;
        }

        private float getEffectiveDelay() {
            if (this.requiredWork <= MINIMUM_DELAY) {
                return 0.0F;
            }
            return this.baseDelay * this.getRemainingWork() / this.requiredWork;
        }

        private float getRemainingWork() {
            return Math.max(0.0F, this.requiredWork - this.completedWork);
        }

        private void applyRequirement(
                long newTargetKey,
                float newRequiredWork,
                int aimingLevel,
                float workRate) {
            if (this.targetInitialized && this.targetKey != newTargetKey) {
                float oldProgress = this.requiredWork > MINIMUM_DELAY
                    ? clamp01(this.completedWork / this.requiredWork)
                    : 0.0F;
                float retainedProgress = oldProgress
                    * calculateTargetProgressRetention(aimingLevel);
                this.completedWork = newRequiredWork * retainedProgress;
                float maximumCompletedWork = Math.max(
                    0.0F,
                    newRequiredWork - MINIMUM_TARGET_REACQUIRE_SECONDS * workRate
                );
                this.completedWork = Math.min(this.completedWork, maximumCompletedWork);
            }

            this.targetKey = newTargetKey;
            this.targetInitialized = true;
            this.requiredWork = newRequiredWork;
            this.completedWork = Math.min(this.completedWork, this.requiredWork);
        }

        private void capturePendingPenalty(long targetKey, float distance, float penalty) {
            if (!this.hasPendingPenalty || distance < this.pendingPenaltyDistance - RANGE_EPSILON) {
                this.pendingPenaltyTargetKey = targetKey;
                this.pendingPenaltyDistance = distance;
                this.pendingPenalty = penalty;
                this.hasPendingPenalty = true;
            } else if (targetKey == this.pendingPenaltyTargetKey
                    && Math.abs(distance - this.pendingPenaltyDistance) <= RANGE_EPSILON) {
                this.pendingPenalty = Math.max(this.pendingPenalty, penalty);
            }
        }

        private void consumePendingPenalty(long currentTargetKey, boolean targetChanged) {
            if (this.hasPendingPenalty && this.pendingPenaltyTargetKey == currentTargetKey) {
                this.recoverablePenalty = this.pendingPenalty;
            } else if (targetChanged) {
                this.recoverablePenalty = 0.0F;
            }

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
        private boolean hasTarget;
        private long targetKey;
        private boolean convertPenalties;
        private float targetDistance = Float.MAX_VALUE;
        private float recoverablePenalty;

        private void reset() {
            this.depth = 0;
            this.active = false;
            this.owner = null;
            this.weapon = null;
            this.physicalRange = 0.0F;
            this.hasTarget = false;
            this.targetKey = NO_TARGET_KEY;
            this.convertPenalties = false;
            this.targetDistance = Float.MAX_VALUE;
            this.recoverablePenalty = 0.0F;
        }
    }

    private static final class ShotScope {
        private long shotId;
        private IsoGameCharacter owner;
        private HandWeapon weapon;
        private IsoGameCharacter targetedHead;
        private IsoGameCharacter reportedTarget;
        private long primaryTargetKey;
        private float primaryTargetDistance;
        private float stabilizationProgress;
        private float completedWork;
        private float requiredWork;
        private int reportedBodyPart = -1;
        private int damageChecks;
        private String outcome = "not_captured";
        private boolean diagnosticLogging;
        private boolean fullyStabilized;
        private boolean bodyPartReported;
        private boolean lethalPromoted;

        private void reset() {
            this.shotId = 0L;
            this.owner = null;
            this.weapon = null;
            this.targetedHead = null;
            this.reportedTarget = null;
            this.primaryTargetKey = NO_TARGET_KEY;
            this.primaryTargetDistance = Float.NaN;
            this.stabilizationProgress = 0.0F;
            this.completedWork = Float.NaN;
            this.requiredWork = Float.NaN;
            this.reportedBodyPart = -1;
            this.damageChecks = 0;
            this.outcome = "not_captured";
            this.diagnosticLogging = false;
            this.fullyStabilized = false;
            this.bodyPartReported = false;
            this.lethalPromoted = false;
        }
    }

    private static final class TargetProfile {
        private static final TargetProfile NONE =
            new TargetProfile(NO_TARGET_KEY, 0.0F);

        private final long key;
        private final float distance;

        private TargetProfile(long key, float distance) {
            this.key = key;
            this.distance = distance;
        }
    }
}
