package zombie.characters;

import zombie.characters.skills.PerkFactory;
import zombie.core.physics.BallisticsController;
import zombie.core.skinnedmodel.advancedanimation.IAnimationVariableSlot;
import zombie.inventory.InventoryItem;
import zombie.iso.IsoMovingObject;
import zombie.network.fields.hit.HitInfo;
import zombie.util.list.PZArrayList;
import java.util.ArrayList;
import java.util.List;

public class IsoGameCharacter extends IsoMovingObject {
    private InventoryItem primaryHandItem;
    private boolean aiming;
    private float aimingDelay;
    private int aimingLevel;
    private float health = 1.0F;
    private boolean zombie;
    private boolean animal;
    private BallisticsController ballisticsController;
    private final PZArrayList<HitInfo> hitInfoList = new PZArrayList<>();
    private final List<IAnimationVariableSlot> gameVariables = new ArrayList<>();

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

    public float getHealth() {
        return this.health;
    }

    public void setHealth(float health) {
        this.health = health;
    }

    public boolean isZombie() {
        return this.zombie;
    }

    public void setZombie(boolean zombie) {
        this.zombie = zombie;
    }

    public boolean isAnimal() {
        return this.animal;
    }

    public void setAnimal(boolean animal) {
        this.animal = animal;
    }

    public BallisticsController getBallisticsController() {
        return this.ballisticsController;
    }

    public void setBallisticsController(BallisticsController ballisticsController) {
        this.ballisticsController = ballisticsController;
    }

    public Iterable<IAnimationVariableSlot> getGameVariables() {
        return this.gameVariables;
    }

    public void setBooleanVariableForTest(String key, boolean value) {
        this.gameVariables.removeIf(variable -> key.equals(variable.getKey()));
        this.gameVariables.add(new TestAnimationVariableSlot(key, value));
    }

    private record TestAnimationVariableSlot(String key, boolean value) implements IAnimationVariableSlot {
        @Override
        public String getKey() {
            return this.key;
        }

        @Override
        public boolean getValueBool() {
            return this.value;
        }
    }
}
