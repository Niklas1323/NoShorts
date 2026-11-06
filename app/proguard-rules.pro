# R8-Regeln für F*ck Shorts.
# Superwall bringt eigene Regeln mit (behält com.superwall.**). Superwalls Regel-Engine (supercel) lädt eine
# native Bibliothek über JNA, und JNA arbeitet mit Reflexion: darum ganz behalten.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# Unsere eigenen Klassen: Dienst und Activity stehen im Manifest (behält R8 automatisch). Zur Sicherheit alles von
# uns behalten – das ist wenig Code, der grosse Gewinn kommt aus den Bibliotheken (v. a. Material-Icons).
-keep class app.fshorts.** { *; }

# Lesbare Absturzberichte
-keepattributes SourceFile,LineNumberTable
