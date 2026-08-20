package zombie.inventory.types;

import zombie.characters.IsoGameCharacter;
import zombie.inventory.InventoryItem;

public class HandWeapon extends InventoryItem {
    private boolean aimedFirearm;
    private boolean ranged;
    private int aimingTime;
    private float maximumSightRange;
    private float maximumRange;
    private float rangeModifier = 1.0F;

    public boolean isAimedFirearm() {
        return this.aimedFirearm;
    }

    public void setAimedFirearm(boolean aimedFirearm) {
        this.aimedFirearm = aimedFirearm;
    }

    public boolean isRanged() {
        return this.ranged;
    }

    public void setRanged(boolean ranged) {
        this.ranged = ranged;
    }

    public int getAimingTime() {
        return this.aimingTime;
    }

    public void setAimingTime(int aimingTime) {
        this.aimingTime = aimingTime;
    }

    public float getMaxSightRange(IsoGameCharacter character) {
        return this.maximumSightRange;
    }

    public void setMaxSightRange(float maximumSightRange) {
        this.maximumSightRange = maximumSightRange;
    }

    public float getMaxRange(IsoGameCharacter character) {
        return this.maximumRange;
    }

    public void setMaxRange(float maximumRange) {
        this.maximumRange = maximumRange;
    }

    public float getRangeMod(IsoGameCharacter character) {
        return this.rangeModifier;
    }

    public void setRangeModifier(float rangeModifier) {
        this.rangeModifier = rangeModifier;
    }
}
