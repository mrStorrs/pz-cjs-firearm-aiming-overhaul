package zombie.characters;

import zombie.characters.skills.PerkFactory;
import zombie.inventory.InventoryItem;
import zombie.iso.IsoMovingObject;
import zombie.network.fields.hit.HitInfo;
import zombie.util.list.PZArrayList;

public class IsoGameCharacter extends IsoMovingObject {
    private InventoryItem primaryHandItem;
    private boolean aiming;
    private float aimingDelay;
    private int aimingLevel;
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

    public int getPerkLevel(PerkFactory.Perk perk) {
        return perk == PerkFactory.Perks.Aiming ? this.aimingLevel : 0;
    }

    public void setAimingLevel(int aimingLevel) {
        this.aimingLevel = aimingLevel;
    }
}
