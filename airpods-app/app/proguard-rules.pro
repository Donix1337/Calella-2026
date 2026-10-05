# App classes are small; keep them intact so receivers, services and the widget
# survive shrinking without surprises.
-keep class dev.pods.app.** { *; }
