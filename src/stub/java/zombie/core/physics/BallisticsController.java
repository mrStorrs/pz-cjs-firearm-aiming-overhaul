package zombie.core.physics;

import java.util.HashMap;
import java.util.Map;

public class BallisticsController {
    private final Map<Integer, Integer> cachedTargetedBodyParts = new HashMap<>();

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
