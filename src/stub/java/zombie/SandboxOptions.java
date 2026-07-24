package zombie;

import java.util.HashMap;
import java.util.Map;

public class SandboxOptions {
    public static final SandboxOptions instance = new SandboxOptions();
    private final Map<String, SandboxOption> options = new HashMap<>();

    public SandboxOption getOptionByName(String name) {
        return this.options.get(name);
    }

    public void setOptionForTest(String name, SandboxOption option) {
        this.options.put(name, option);
    }

    public void clearOptionsForTest() {
        this.options.clear();
    }

    public interface SandboxOption {
    }

    public static class DoubleSandboxOption implements SandboxOption {
        private final double value;

        public DoubleSandboxOption(double value) {
            this.value = value;
        }

        public double getValue() {
            return this.value;
        }
    }
}
