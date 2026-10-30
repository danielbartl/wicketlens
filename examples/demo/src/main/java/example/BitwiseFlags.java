package example;

/** Immutable configuration and reviewed source constant flag calculations. */
public final class BitwiseFlags {
    public static final int EDIT = 1 << 0;
    public static final int SAVE = 1 << 1;
    public static final int DEFAULT = EDIT | SAVE;
    private final int flags;

    public BitwiseFlags(int flags) {
        this.flags = flags;
    }

    public boolean canSave() {
        return (flags & SAVE) != 0;
    }

    public int itemIndex() {
        return (flags >>> 1) ^ 3;
    }
}
