package com.cjstorrs.firearmaimingoverhaul;

import zombie.SandboxOptions;

public final class FirearmAimSettings {
    public static final double DEFAULT_MAXIMUM_MULTIPLIER = 4.0;
    public static final double DEFAULT_CURVE_EXPONENT = 1.5;
    public static final double DEFAULT_FULL_PENALTY_DISTANCE_TILES = 10.0;

    private static final String MAXIMUM_MULTIPLIER_OPTION =
        "CJSFirearmAimingOverhaul.MaximumAimTimeMultiplier";
    private static final String CURVE_EXPONENT_OPTION =
        "CJSFirearmAimingOverhaul.CurveExponent";
    private static final String FULL_PENALTY_DISTANCE_OPTION =
        "CJSFirearmAimingOverhaul.FullPenaltyDistanceTiles";

    private FirearmAimSettings() {
    }

    public static float getMaximumMultiplier() {
        return readDoubleOption(MAXIMUM_MULTIPLIER_OPTION, DEFAULT_MAXIMUM_MULTIPLIER, 1.0, 10.0);
    }

    public static float getCurveExponent() {
        return readDoubleOption(CURVE_EXPONENT_OPTION, DEFAULT_CURVE_EXPONENT, 0.5, 3.0);
    }

    public static float getFullPenaltyDistanceTiles() {
        return readDoubleOption(
            FULL_PENALTY_DISTANCE_OPTION,
            DEFAULT_FULL_PENALTY_DISTANCE_TILES,
            1.0,
            30.0
        );
    }

    private static float readDoubleOption(String name, double fallback, double minimum, double maximum) {
        SandboxOptions.SandboxOption option = SandboxOptions.instance.getOptionByName(name);
        double value = fallback;
        if (option instanceof SandboxOptions.DoubleSandboxOption doubleOption) {
            value = doubleOption.getValue();
        }

        return (float)Math.max(minimum, Math.min(maximum, value));
    }
}
