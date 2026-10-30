# Tablet-Helfer (Galaxy Tab A8, 1200x1920). Laden: cd scratchpad/tab; . tab.sh
export MSYS_NO_PATHCONV=1
mkdir -p rec
. /c/Users/Admin/NoShorts/tools/pixel-test/env.sh 2>/dev/null
logs() { adb logcat -d -v time -s FuckShorts | grep -E "Leiste:|Tipp:|${1:-XXXX}" | cut -c7-200 | uniq | tail -${2:-40}; }
ss() { adb exec-out screencap -p > ${1:-s}.png; }
port() { adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 0; sleep ${1:-3}; }
land() { adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1; sleep ${1:-3}; }
# Echte Leiste erkennen: zwei Stellen, an denen nur die echte Leiste ein Symbol hat (beide kontrastreich),
# plus eine Stelle, die bei beiden Leisten leer (einfarbig) ist. Ausrichtung P oder L.
# P: echte Startseite x312, Erstellen x600, Kontrolle x195-235, Icons y1782-1818
# L: echte Shorts x816, Erstellen x960, Kontrolle x400-440, Icons y1062-1098
realdet() {
  local n=$1 o=${2:-P} a b c y yo=$3
  if [ $o = P ]; then a=292; b=580; c=195; y=1782; else a=796; b=940; c=400; y=1062; fi; [ -n "$yo" ] && y=$yo
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -filter_complex "[0:v]split=3[a][b][c];[a]crop=40:36:$a:$y,signalstats,metadata=print:file=rec/r1.txt[ao];[b]crop=40:36:$b:$y,signalstats,metadata=print:file=rec/r2.txt[bo];[c]crop=40:36:$c:$y,signalstats,metadata=print:file=rec/r3.txt[co]" -map "[ao]" -f null - -map "[bo]" -f null - -map "[co]" -f null -
  local g; g() { grep -o "$1=[0-9]*" rec/$2.txt | cut -d= -f2; }
  paste <(grep -o 'frame:[0-9]* *pts:[0-9]* *pts_time:[0-9.]*' rec/r1.txt | awk '{split($1,a,":");split($3,c,":");print a[2], c[2]}') \
        <(g YMIN r1) <(g YMAX r1) <(g YMIN r2) <(g YMAX r2) <(g YMIN r3) <(g YMAX r3) \
  | awk '{ if ($4-$3>80 && $6-$5>80 && $8-$7<14) { if($1!=last+1){ if(s!="")print s"-"last" ("st"s)"; s=$1; st=$2 } last=$1 } } END{ if(s!="")print s"-"last" ("st"s)" }'
}
# Bilder ansehen: tframes NAME N1 N2 ... (ganze Bilder, klein)
tframes() { local n=$1; shift; local sel=$(printf 'eq(n\\,%s)+' "$@"); sel=${sel%+}
  ffmpeg -loglevel error -y -i rec/$n.mp4 -fps_mode passthrough -vf "select='$sel',scale=360:-2,drawtext=fontfile='C\:/Windows/Fonts/arial.ttf':text='%{n} %{pts\:flt}':fontsize=22:fontcolor=yellow:box=1:boxcolor=black@0.7:x=4:y=40,tile=$#x1" -frames:v 1 rec/${n}_f.png; }
# Leistenstreifen: tstrips NAME START ANZAHL P|L
tstrips() { local n=$1 st=${2:-0} cnt=${3:-24} o=${4:-P} y w
  if [ $o = P ]; then y=1740; w=1200; else y=1020; w=1920; fi
  ffmpeg -loglevel error -y -i rec/$n.mp4 -fps_mode passthrough \
   -vf "select='between(n\,$st\,$(($st+$cnt-1)))',crop=$w:130:0:$y,scale=600:-2,drawtext=fontfile='C\:/Windows/Fonts/arial.ttf':text='%{eif\:n+$st\:d} %{pts\:flt}':fontsize=18:fontcolor=yellow:box=1:boxcolor=black@0.7:x=4:y=4,tile=3x$(( (cnt+2)/3 ))" \
   -frames:v 1 rec/${n}_s$st.png; }
# Unsere Leiste fehlt: Startseite-Symbol unserer Leiste (P x130-170, L x220-260) einfarbig. Gibt Bereiche aus.
oursgone() {
  local n=$1 o=${2:-P} x y
  if [ $o = P ]; then x=130; y=1785; else x=220; y=1065; fi
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -vf "crop=40:30:$x:$y,signalstats,metadata=print:file=rec/o1.txt" -f null -
  paste <(grep -o 'frame:[0-9]* *pts:[0-9]* *pts_time:[0-9.]*' rec/o1.txt | awk '{split($1,a,":");split($3,c,":");print a[2], c[2]}') \
        <(grep -o 'YMIN=[0-9]*' rec/o1.txt | cut -d= -f2) <(grep -o 'YMAX=[0-9]*' rec/o1.txt | cut -d= -f2) \
  | awk '{ if ($4-$3<60) { if($1!=last+1){ if(s!="")print s"-"last" ("st"s)"; s=$1; st=$2 } last=$1 } } END{ if(s!="")print s"-"last" ("st"s)" }'
}
# Sicherheit: Ist gerade ein Video offen (letzter Zustand im Protokoll)? Dann NICHT auf Feed-Stellen tippen.
state() { adb logcat -d -s FuckShorts | grep -oE "Leiste: (Video offen|Video verlassen[^)]*\)?|normal|Video verlassen)" | tail -1; }
videoopen() { state | grep -q "Video offen"; }
# Video sicher verlassen: Wischgeste, notfalls Zurück-Taste; bricht ab, wenn es nicht klappt
leavevideo() { adb shell input swipe 2 1000 450 1000 150; sleep 3; if videoopen; then adb shell input keyevent KEYCODE_BACK; sleep 3; fi; if videoopen; then echo "VIDEO NOCH OFFEN - Abbruch"; return 1; fi; }
# Nur tippen, wenn kein Video offen ist
safetap() { if videoopen; then echo "Video offen, Tipp $1 $2 übersprungen"; return 1; fi; adb shell input tap $1 $2; }
# Sicher auf unsere Leiste tippen: Bricht ab, wenn der Tipp nicht angekommen ist (dann nicht blind weitertippen)
tipcount() { adb logcat -d -s FuckShorts | grep -c 'Tipp: .*Klick'; }
tapbar() { local b=$(tipcount) a; adb shell input tap $1 1810; sleep 0.6; a=$(tipcount); if [ "$a" -le "$b" ]; then sleep 1; a=$(tipcount); fi; if [ "$a" -le "$b" ]; then echo "ABBRUCH: Tipp $1 nicht angekommen"; return 1; fi; }
# Welche App ist vorne?
focus() { adb shell dumpsys window | grep -m1 mCurrentFocus | grep -oE 'permissioncontroller|youtube|launcher|chrome|vending' | head -1; }
