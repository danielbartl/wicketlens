package example;

/** Closed source factories preserve the concrete provider and its configuration. */
public class ProviderFactories {
    public static ComponentParts create(String headingId) {
        return configured(new DispatchParts(), headingId);
    }

    private static ComponentParts configured(ComponentParts delegate, String headingId) {
        return new ConfiguredParts(delegate, headingId);
    }

    public static class Builder {
        public ComponentParts create(String headingId) {
            return ProviderFactories.create(headingId);
        }
    }
}
