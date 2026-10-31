package dev.wicketlens;

import com.intellij.mcpserver.McpSessionInvocationMode;
import com.intellij.mcpserver.settings.McpToolFilterSettings;

/** Access the SDK's JVM-public, Kotlin-internal settings only inside the isolated test host. */
final class McpHostInvocationSettings {
    static McpSessionInvocationMode get() {
        return McpToolFilterSettings.Companion.getInstance().getInvocationMode();
    }

    static void set(McpSessionInvocationMode mode) {
        McpToolFilterSettings.Companion.getInstance().setInvocationMode(mode);
    }
}
