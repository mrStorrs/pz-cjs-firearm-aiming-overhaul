package zombie.inventory.types;

import zombie.inventory.InventoryItem;

public final class WeaponPart extends InventoryItem {
    private int aimingTime;
    private int hitChance;

    public int getAimingTime() {
        return this.aimingTime;
    }

    public void setAimingTime(int aimingTime) {
        this.aimingTime = aimingTime;
    }

    public int getHitChance() {
        return this.hitChance;
    }

    public void setHitChance(int hitChance) {
        this.hitChance = hitChance;
    }
}
