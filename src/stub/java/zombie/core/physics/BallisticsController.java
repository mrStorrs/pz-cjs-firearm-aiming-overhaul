package zombie.core.physics;

import java.util.HashMap;
import java.util.Map;

public class BallisticsController {
    private final Map<Integer, Integer> cachedTargetedBodyParts = new HashMap<>();
    private final float[] cameraTargets = new float[50];
    private int numberOfCameraTargets;

    public float[] getCameraTargets() {
        return this.cameraTargets;
    }

    public int getNumberOfCameraTargets() {
        return this.numberOfCameraTargets;
    }

    public void setCameraTargetForTest(int targetId, float x, float y, int bodyPart) {
        this.cameraTargets[0] = targetId;
        this.cameraTargets[1] = x;
        this.cameraTargets[3] = y;
        this.cameraTargets[4] = bodyPart;
        this.numberOfCameraTargets = 1;
    }

    public void clearCameraTargetsForTest() {
        this.numberOfCameraTargets = 0;
    }

    public boolean isCameraTarget(int targetId) {
        return this.cachedTargetedBodyParts.containsKey(targetId);
    }

    public int getCachedTargetedBodyPart(int targetId) {
        return this.cachedTargetedBodyParts.getOrDefault(
            targetId,
            RagdollBodyPart.BODYPART_COUNT.ordinal()
        );
    }

    public void setCachedTargetedBodyPart(int targetId, int bodyPart) {
        this.cachedTargetedBodyParts.put(targetId, bodyPart);
    }
}
