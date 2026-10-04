# Keep-regler för release-minify (skill data-privacy-security, NFR-3).
# Hilt och Compose har egna konsumentregler. Regler för kotlinx.serialization och
# Firestore-modeller läggs till här när de börjar användas (etapp 2).

# Strippa loggning ur releasebygget – appen hanterar hälsodata (NFR-13).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
