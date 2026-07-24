package com.cjstorrs.firearmaimingoverhaul;

import zombie.SandboxOptions;

public final class FirearmAimSettings {
    public static final double DEFAULT_MAXIMUM_FAR_EXTRA_SECONDS = 5.0;
    public static final double DEFAULT_FAR_PROGRESS_EXPONENT = 1.25;
    public static final double DEFAULT_REFERENCE_GAP_TILES = 10.0;

    private static final String MAXIMUM_FAR_EXTRA_SECONDS_OPTION =
        "CJSFirearmAimingOverhaul.MaximumAimTimeMultiplier";
    private static final String FAR_PROGRESS_EXPONENT_OPTION =
        "CJSFirearmAimingOverhaul.CurveExponent";
    private static final String REFERENCE_GAP_TILES_OPTION =
        "CJSFirearmAimingOverhaul.FullPenaltyDistanceTiles";

    private FirearmAimSettings() {
    }

    public static float getMaximumFarExtraSeconds() {
        return readDoubleOption(
            MAXIMUM_FAR_EXTRA_SECONDS_OPTION,
            DEFAULT_MAXIMUM_FAR_EXTRA_SECONDS,
            0.0,
            12.0
        );
    }

    public static float getFarProgressExponent() {
        return readDoubleOption(
            FAR_PROGRESS_EXPONENT_OPTION,
            DEFAULT_FAR_PROGRESS_EXPONENT,
            0.5,
            3.0
        );
    }

    public static float getReferenceGapTiles() {
        return readDoubleOption(
            REFERENCE_GAP_TILES_OPTION,
            DEFAULT_REFERENCE_GAP_TILES,
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
