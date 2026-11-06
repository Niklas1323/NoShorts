# Nur Play-Store-Version: alle Logcat-Ausgaben entfernen. Sie enthalten teils Texte aus der YouTube-App
# (Knöpfe, Titel), und die sollen das Gerät nicht einmal im Systemprotokoll erreichen. Test-Versionen behalten sie.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static boolean isLoggable(java.lang.String, int);
}
