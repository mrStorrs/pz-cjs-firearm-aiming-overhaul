package zombie.iso;

public class IsoMovingObject {
    private static int nextId;
    private final int id;

    public IsoMovingObject() {
        this.id = nextId++;
    }

    public IsoMovingObject(int id) {
        this.id = id;
    }

    public int getID() {
        return this.id;
    }
}
