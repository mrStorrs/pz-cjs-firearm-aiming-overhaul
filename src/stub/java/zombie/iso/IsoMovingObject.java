package zombie.iso;

public class IsoMovingObject {
    private static int nextId;
    private final int id;
    private float x;
    private float y;

    public IsoMovingObject() {
        this.id = nextId++;
    }

    public IsoMovingObject(int id) {
        this.id = id;
    }

    public int getID() {
        return this.id;
    }

    public float getX() {
        return this.x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return this.y;
    }

    public void setY(float y) {
        this.y = y;
    }
}
