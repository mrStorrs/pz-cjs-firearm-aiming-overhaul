package zombie.characters;

import zombie.inventory.InventoryItem;
import zombie.network.fields.hit.HitInfo;
import zombie.util.list.PZArrayList;

public class IsoGameCharacter {
    private InventoryItem primaryHandItem;
    private boolean aiming;
    private float aimingDelay;
    private final PZArrayList<HitInfo> hitInfoList = new PZArrayList<>();

    public InventoryItem getPrimaryHandItem() {
        return this.primaryHandItem;
    }

    public void setPrimaryHandItem(InventoryItem primaryHandItem) {
        this.primaryHandItem = primaryHandItem;
    }

    public boolean isAiming() {
        return this.aiming;
    }

    public void setAiming(boolean aiming) {
        this.aiming = aiming;
    }

    public float getAimingDelay() {
        return this.aimingDelay;
    }

    public void setAimingDelay(float aimingDelay) {
        this.aimingDelay = aimingDelay;
    }

    public PZArrayList<HitInfo> getHitInfoList() {
        return this.hitInfoList;
    }
}
