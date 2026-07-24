package zombie;

public class SandboxOptions {
    public static final SandboxOptions instance = new SandboxOptions();
    private SandboxOption option;

    public SandboxOption getOptionByName(String name) {
        return this.option;
    }

    public void setOptionForTest(SandboxOption option) {
        this.option = option;
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
