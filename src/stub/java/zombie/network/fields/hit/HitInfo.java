package zombie.network.fields.hit;

import zombie.iso.IsoMovingObject;

public class HitInfo {
    public float x;
    public float y;
    public float z;
    public float distSq;
    public int chance;
    private IsoMovingObject object;

    public HitInfo(float distSq) {
        this.distSq = distSq;
    }

    public HitInfo(float distSq, IsoMovingObject object) {
        this.distSq = distSq;
        this.object = object;
    }

    public IsoMovingObject getObject() {
        return this.object;
    }

    public void setObject(IsoMovingObject object) {
        this.object = object;
    }
}
