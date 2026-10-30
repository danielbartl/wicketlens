package example;

/** Compile-time constants read without running application code. */
public final class ConstantIds {
    public static final String TITLE = "title";
    public static final String FORM = "form";
    public static final String NAME = "name";
    public static final String PREFIX = "item";
    public static final int INDEX = 2;
    public static final boolean SHOW_FORM = INDEX > 0;

    private ConstantIds() {}
}
