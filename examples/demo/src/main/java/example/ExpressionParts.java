package example;

/** Pure scalar methods over immutable configuration. */
public class ExpressionParts {
    private final String prefix;
    private final int count;

    public ExpressionParts(String prefix, int count) {
        this.prefix = prefix;
        this.count = count;
    }

    public String id(String suffix) {
        return prefix + suffix;
    }

    public boolean enabled() {
        return count * 2 >= 4;
    }
}
