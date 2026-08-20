package com.cjstorrs.firearmaimingoverhaul;

import java.lang.reflect.Method;

public final class GameApiLinkageTest {
    private GameApiLinkageTest() {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        ClassLoader loader = GameApiLinkageTest.class.getClassLoader();
        Class<?> combatManager = load(loader, "zombie.CombatManager");
        Class<?> isoGameCharacter = load(loader, "zombie.characters.IsoGameCharacter");
        Class<?> isoPlayer = load(loader, "zombie.characters.IsoPlayer");
        Class<?> perk = load(loader, "zombie.characters.skills.PerkFactory$Perk");
        Class<?> perks = load(loader, "zombie.characters.skills.PerkFactory$Perks");
        Class<?> inventoryItem = load(loader, "zombie.inventory.InventoryItem");
        Class<?> handWeapon = load(loader, "zombie.inventory.types.HandWeapon");
        Class<?> hitInfo = load(loader, "zombie.network.fields.hit.HitInfo");
        Class<?> isoMovingObject = load(loader, "zombie.iso.IsoMovingObject");
        Class<?> ballisticsController = load(loader, "zombie.core.physics.BallisticsController");
        Class<?> ragdollBodyPart = load(loader, "zombie.core.physics.RagdollBodyPart");
        Class<?> shotDirection = load(loader, "zombie.combat.ShotDirection");
        Class<?> swipeStatePlayer = load(loader, "zombie.ai.states.SwipeStatePlayer");
        Class<?> attackType = load(loader, "zombie.AttackType");
        Class<?> isoGridSquare = load(loader, "zombie.iso.IsoGridSquare");
        Class<?> pzArrayList = load(loader, "zombie.util.list.PZArrayList");

        requireMethod(isoGameCharacter, "updateAimingDelay");
        check(requireMethod(isoGameCharacter, "getAimingDelay").getReturnType() == float.class, "aiming delay return type");
        requireMethod(isoGameCharacter, "setAimingDelay", float.class);
        check(requireMethod(isoGameCharacter, "getHitInfoList").getReturnType() == pzArrayList, "hit-info list return type");
        requireMethod(isoGameCharacter, "isAiming");
        check(requireMethod(isoGameCharacter, "getHealth").getReturnType() == float.class, "health return type");
        check(requireMethod(isoGameCharacter, "isZombie").getReturnType() == boolean.class, "zombie flag return type");
        check(requireMethod(isoGameCharacter, "isAnimal").getReturnType() == boolean.class, "animal flag return type");
        requireMethod(isoGameCharacter, "getPrimaryHandItem");
        check(
            requireMethod(isoGameCharacter, "getPerkLevel", perk).getReturnType() == int.class,
            "perk level return type"
        );
        check(perks.getField("Aiming").getType() == perk, "aiming perk field type");
        check(
            requireMethod(isoGameCharacter, "getBallisticsController").getReturnType() == ballisticsController,
            "ballistics-controller return type"
        );
        check(
            requireMethod(isoGameCharacter, "getWornItemsVisionModifier").getReturnType() == float.class,
            "vision modifier return type"
        );
        check(
            requireMethod(isoPlayer, "calculateCritChance", isoGameCharacter).getReturnType() == int.class,
            "critical-chance return type"
        );

        requireMethod(combatManager, "setAimingDelay", isoPlayer, handWeapon);
        requireMethod(
            combatManager,
            "processTargetedHit",
            handWeapon,
            isoGameCharacter,
            isoGameCharacter,
            ragdollBodyPart,
            shotDirection
        );
        requireMethod(
            combatManager,
            "attackCollisionCheck",
            isoGameCharacter,
            handWeapon,
            swipeStatePlayer,
            attackType
        );
        requireMethod(
            isoGameCharacter,
            "hitConsequences",
            handWeapon,
            isoGameCharacter,
            boolean.class,
            float.class,
            boolean.class
        );
        requireMethod(combatManager, "calculateHitInfoList", isoGameCharacter);
        requireMethod(combatManager, "calculateHitChanceData", isoGameCharacter, handWeapon, hitInfo);
        check(
            requireMethod(combatManager, "getDistanceModifier", float.class, float.class, float.class, boolean.class)
                .getReturnType() == float.class,
            "distance modifier return type"
        );
        check(
            requireMethod(combatManager, "getAimDelayPenalty", float.class, float.class, float.class, float.class)
                .getReturnType() == float.class,
            "aim-delay penalty return type"
        );
        check(
            requireMethod(combatManager, "getMovePenalty", isoGameCharacter, float.class).getReturnType() == float.class,
            "movement penalty return type"
        );
        check(
            requireMethod(combatManager, "getPainPenalty", isoGameCharacter).getReturnType() == float.class,
            "pain penalty return type"
        );
        check(
            requireMethod(combatManager, "getWeatherPenalty", isoGameCharacter, handWeapon, isoGridSquare, float.class)
                .getReturnType() == float.class,
            "weather penalty return type"
        );
        check(
            requireMethod(combatManager, "getMoodlesPenalty", isoGameCharacter, float.class).getReturnType() == float.class,
            "moodles penalty return type"
        );

        check(requireMethod(handWeapon, "isAimedFirearm").getReturnType() == boolean.class, "aimed-firearm return type");
        check(requireMethod(handWeapon, "isRanged").getReturnType() == boolean.class, "ranged return type");
        check(requireMethod(handWeapon, "getAimingTime").getReturnType() == int.class, "aiming-time return type");
        check(
            requireMethod(handWeapon, "getMaxSightRange", isoGameCharacter).getReturnType() == float.class,
            "maximum sight range return type"
        );
        check(
            requireMethod(handWeapon, "getMaxRange", isoGameCharacter).getReturnType() == float.class,
            "maximum range return type"
        );
        check(
            requireMethod(handWeapon, "getRangeMod", isoGameCharacter).getReturnType() == float.class,
            "range modifier return type"
        );
        check(requireMethod(inventoryItem, "getFullType").getReturnType() == String.class, "weapon full type");
        hitInfo.getField("distSq");
        check(hitInfo.getField("x").getType() == float.class, "hit-point x type");
        check(hitInfo.getField("y").getType() == float.class, "hit-point y type");
        check(hitInfo.getField("chance").getType() == int.class, "hit chance field type");
        check(requireMethod(hitInfo, "getObject").getReturnType() == isoMovingObject, "hit object return type");
        check(requireMethod(isoMovingObject, "getID").getReturnType() == int.class, "object id return type");
        check(requireMethod(isoMovingObject, "getX").getReturnType() == float.class, "object x return type");
        check(requireMethod(isoMovingObject, "getY").getReturnType() == float.class, "object y return type");
        check(
            requireMethod(ballisticsController, "isCameraTarget", int.class).getReturnType() == boolean.class,
            "camera-target return type"
        );
        check(
            requireMethod(ballisticsController, "getCachedTargetedBodyPart", int.class).getReturnType() == int.class,
            "targeted-body-part return type"
        );

        Class<?> sandboxOptions = load(loader, "zombie.SandboxOptions");
        Class<?> sandboxOption = load(loader, "zombie.SandboxOptions$SandboxOption");
        Class<?> doubleSandboxOption = load(loader, "zombie.SandboxOptions$DoubleSandboxOption");
        Class<?> booleanSandboxOption = load(loader, "zombie.SandboxOptions$BooleanSandboxOption");
        check(
            requireMethod(sandboxOptions, "getOptionByName", String.class).getReturnType() == sandboxOption,
            "sandbox option return type"
        );
        check(doubleSandboxOption.getMethod("getValue").getReturnType() == double.class, "double sandbox value type");
        check(
            booleanSandboxOption.getMethod("getValue").getReturnType() == boolean.class,
            "boolean sandbox value type"
        );

        Class<?> runtime = load(loader, "com.cjstorrs.firearmaimingoverhaul.FirearmAimRuntime");
        requireMethod(runtime, "beforeAimingDelayUpdate", isoGameCharacter);
        requireMethod(runtime, "afterAimingDelayUpdate", isoGameCharacter);
        requireMethod(runtime, "synchronizePostShotDelay", isoPlayer);
        requireMethod(runtime, "captureShotStabilization", isoPlayer, handWeapon);
        requireMethod(
            runtime,
            "recordTargetedBodyPart",
            isoGameCharacter,
            handWeapon,
            isoGameCharacter,
            int.class
        );
        requireMethod(
            runtime,
            "guaranteeLethalHeadshotDamage",
            isoGameCharacter,
            handWeapon,
            isoGameCharacter,
            boolean.class,
            float.class
        );
        requireMethod(runtime, "endShot");
        requireMethod(runtime, "promoteStabilizationHitChance", isoGameCharacter);
        requireMethod(runtime, "beginAccuracyCalculation", isoGameCharacter, handWeapon, hitInfo);
        requireMethod(runtime, "beginCriticalChanceCalculation", isoPlayer, isoGameCharacter);
        requireMethod(runtime, "endAccuracyCalculation");
        requireMethod(runtime, "prepareAccuracyDistance", float.class, float.class, float.class);
        requireMethod(runtime, "convertRecoverablePenalty", float.class);
        requireMethod(runtime, "convertRecoverableVisionModifier", float.class);
        requireMethod(runtime, "removeBeyondSightDelayScaling", float.class, float.class, float.class);

        System.out.println("GameApiLinkageTest: PASS");
    }

    private static Class<?> load(ClassLoader loader, String className) throws ClassNotFoundException {
        return Class.forName(className, false, loader);
    }

    private static Method requireMethod(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        return owner.getDeclaredMethod(name, parameters);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
