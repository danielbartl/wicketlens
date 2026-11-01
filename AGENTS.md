# WicketLens development guidance

Follow the current roadmap in `spec.md` and preserve working plugin behavior.

For every core feature extension, assess whether its new facts or operations
would help an agent through MCP. Record that decision in the implementation notes:
extend an existing tool, add a focused tool, or explain why MCP needs no change.
Presentation-only changes do not require a tool change.

Reuse the existing semantic analyzers and value snapshots for MCP. Keep static
uncertainty and discovery limits explicit, preserve existing response contracts,
and cover applicable project isolation, freshness, cancellation, response bounds,
and read-only behavior in tests. Update tool descriptions and client documentation
when the MCP surface changes.
