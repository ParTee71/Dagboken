# Keep Room entities and DAOs
-keep class se.partee71.dagboken.data.room.** { *; }

# Keep domain models for serialization
-keep class se.partee71.dagboken.domain.model.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }

# Google API client
-keep class com.google.api.** { *; }
-keep class com.google.apis.** { *; }
-dontwarn com.google.api.**
-dontwarn com.google.apis.**

# Apache HTTP client (transitively pulled in by Google API client)
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**

# Glance widgets/actions are instantiated by the Glance runtime via reflection
# (GlanceAppWidgetReceiver by manifest class name, ActionCallback subclasses by
# actionRunCallback<T>()'s stored class name). Only referenced via generic type
# parameters or manifest strings, R8 has no other reason to keep them — without
# this rule they get renamed/stripped in release builds, so a widget tap silently
# does nothing (no crash, no log) instead of running the action (#164).
-keep class se.partee71.dagboken.widget.** { *; }

# ...and the same applies to Glance's OWN internals. A tap on a `clickable` element is
# routed through Glance's trampoline components, which its library manifest declares by
# class name (androidx.glance.appwidget.action.ActionTrampolineActivity,
# InvisibleActionTrampolineActivity, ActionCallbackBroadcastReceiver). Keeping only our
# widget package (#164/#165) left those renameable, so `clickable` taps never reached any
# ActionCallback at all — while CheckBox's onCheckedChange, which takes a different
# internal path, kept working. That asymmetry is exactly what we observed.
-keep class androidx.glance.** { *; }
-keep class androidx.glance.appwidget.** { *; }
-dontwarn androidx.glance.**

# Strippa loggning ur releasebygget. Appen hanterar känslig hälsodata och ska aldrig
# skriva något till logcat i release (NFR-13).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
