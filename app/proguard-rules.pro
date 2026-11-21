# openhongguo ships its own rules for the Go core (consumer-rules.pro).

# The app reads additive SDK diagnostics fields reflectively.
-keep class dev.openhongguo.OpenhongguoException { *; }
