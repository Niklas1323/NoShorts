# Sitzung 14./15.11.2026: Umgebung + Hilfen
export T=/c/Users/A023985/tools
export JAVA_HOME=/c/Users/A023985/tools/jdk-17.0.20.1+1
cd /c/Users/A023985/ptest
. ./init.sh
U=0
# front: vorderes Fenster + Benutzer
front() { adb shell dumpsys window displays 2>/dev/null | grep -m1 -E 'mCurrentFocus=Window' | grep -oE 'u[0-9]+ [^ }]+'; }
shotv() { adb exec-out screencap -p > rec/$1.png; ffmpeg -loglevel fatal -y -i rec/$1.png -vf scale=${2:-540}:-1 rec/$1_s.png; echo rec/$1_s.png; }
# --- Benutzer Leon: Profilbild magenta (194,23,91). Echte Leiste: Bild an Platz 4 (x911-980,y2275-2344), unsere: Platz 3 (x640-710,y2241-2311)
# av NAME: pro Bild Anteil Profilbild-Pixel an beiden Stellen -> rec/av_NAME.txt (Bild t echt unsere), dazu Zusammenfassung
av() { local n=$1 m="255*gt(r(X,Y),150)*lt(g(X,Y),70)*gt(b(X,Y),55)*lt(b(X,Y),135)"
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -filter_complex "[0:v]split=2[a][b];[a]crop=70:149:911:2275,format=gbrp,geq=r='$m':g='$m':b='$m',format=gray,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/_a1.txt[ao];[b]crop=70:70:640:2241,format=gbrp,geq=r='$m':g='$m':b='$m',format=gray,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/_a2.txt[bo]" -map "[ao]" -f null - -map "[bo]" -f null -
  paste -d' ' <(grep -oE 'pts_time:[0-9.]+' rec/_a1.txt | cut -d: -f2) <(grep -oE 'YAVG=[0-9.]+' rec/_a1.txt | cut -d= -f2) <(grep -oE 'YAVG=[0-9.]+' rec/_a2.txt | cut -d= -f2) | awk '{printf "%d %.3f %.2f %.2f\n", NR-1, $1, $2/255, $3/255}' > rec/av_$n.txt
  avsum $n; }
# avsum NAME [SCHWELLE]: Abschnitte E=echte sichtbar, U=unsere sichtbar, -=keine
avsum() { awk -v th=${2:-0.12} -v te=${3:-0.03} '{ s=($3>te)?"E":(($4>th)?"U":"-"); if($3>te && $4>th)s="B"; if(s!=p){ if(p!="")printf "%s Bilder %d-%d (%d, t=%.2f-%.2f)\n",p,st,NR-2,NR-1-st,tt,lt; p=s; st=NR-1; tt=$2 } lt=$2 } END{printf "%s Bilder %d-%d (%d, t=%.2f-%.2f)\n",p,st,NR-1,NR-st,tt,lt; }' rec/av_$1.txt; }
# strip NAME VON BIS [SCHRITT] [SPALTEN]: untere 520 px der Bilder VON..BIS als Blatt mit Bildnummer
strip() { local n=$1 a=$2 b=$3 st=${4:-1} c=${5:-8}; ffmpeg -loglevel error -y -i rec/$n.mp4 -vf "select='between(n,$a,$b)*not(mod(n-$a,$st))',crop=1080:${H:-520}:0:$((2424-${H:-520})),scale=${W:-270}:-1,drawtext=text='%{n}':x=4:y=4:fontsize=22:fontcolor=yellow:box=1:boxcolor=black@0.6:fontfile=/Windows/Fonts/arial.ttf,tile=${c}x$(( ( (b-a)/st + c ) / c ))" -fps_mode passthrough -frames:v 1 rec/${n}_s.png && echo rec/${n}_s.png; }
# full NAME VON BIS [SCHRITT] [SPALTEN]: ganze Bilder verkleinert
full() { local n=$1 a=$2 b=$3 st=${4:-1} c=${5:-8}; ffmpeg -loglevel error -y -i rec/$n.mp4 -vf "select='between(n,$a,$b)*not(mod(n-$a,$st))',scale=${W:-200}:-1,drawtext=text='%{n}':x=4:y=4:fontsize=20:fontcolor=yellow:box=1:boxcolor=black@0.6:fontfile=/Windows/Fonts/arial.ttf,tile=${c}x$(( ( (b-a)/st + c ) / c ))" -fps_mode passthrough -frames:v 1 rec/${n}_f.png && echo rec/${n}_f.png; }
nfr() { ffprobe -v error -count_frames -select_streams v -show_entries stream=nb_read_frames -of csv=p=0 rec/$1.mp4; }
ytfront() { case "$(front)" in "u0 com.google.android.youtube/"*) return 0;; *) echo "NICHT YOUTUBE: $(front)"; return 1;; esac; }
# focus() aus phone.sh liefert hier "null" (zwei mCurrentFocus-Zeilen) -> neu
focus() { adb shell dumpsys window displays 2>/dev/null | grep -m1 -E 'mCurrentFocus=Window' | grep -oE '[a-zA-Z.]+/[^ }]+'; }
VID=aqz-KE-bpKQ
# vlink: Video per Link öffnen und warten
vlink() { adb shell "am start -a android.intent.action.VIEW -d 'https://www.youtube.com/watch?v=$VID&t=$((RANDOM%300+10))' com.google.android.youtube" >/dev/null; sleep ${1:-7}; }
# vleave METHODE NAME [WARTEN]: Video per Link, verlassen (back|edge|down), Aufnahme + Protokoll
vleave() { local m=$1 n=$2; tofeed >/dev/null; live $n; recstart ${4:-16}; vlink ${3:-7}
 ytfront || return
 echo "MARK $m $(date +%T.%N | cut -c1-12)" >> rec/$n.log
 case $m in back) adb shell input keyevent KEYCODE_BACK;; edge) adb shell input swipe 3 1300 600 1300 150;; down) adb shell input swipe 540 450 540 1800 200;; esac
 sleep 4; recpull $n >/dev/null; av $n; grep -a "Leiste:" rec/$n.log | cut -c7-18,43- | tail -${L:-14}; }
# pixs "x y" "x y" ...: mehrere Pixel aus EINEM Bildschirmfoto -> je Zeile "r g b"
pixs() { adb exec-out screencap -p > rec/_p.png; for p in "$@"; do set -- $p; ffmpeg -loglevel fatal -y -i rec/_p.png -vf "crop=1:1:$1:$2,format=rgb24" -f rawvideo - | od -An -tu1 | tr -s ' '; done; }
# hasmini: schwebender Mini-Player unten rechts (weisses X in dunklem Kreis bei 988,1954)?
hasmini() { ytfront >/dev/null || return 1; local r; r=$(pixs "988 1954" "960 1954" "1016 1954" "988 1926" "988 1982" | awk 'NR==1{ok=($1>225&&$2>225&&$3>225)} NR>1{if($1>205||$2>205||$3>205)ok=0} END{print ok+0}'); [ "$r" = 1 ]; }
miniclose() { if hasmini; then adb shell input tap 988 1954; sleep 1.5; echo "Mini-Player zu"; else echo "kein Mini-Player"; fi; }
# vl METHODE NAME: Mini-Player aufklappen (Video-Seite), 3,5 s, verlassen (back|edge|down), Auswertung
vl() { local m=$1 n=$2; hasmini || { echo "KEIN MINI-PLAYER"; return 1; }; live $n; recstart ${3:-9}; adb shell input tap 800 2090; sleep 3.5
 ytfront || return
 case $m in back) adb shell input keyevent KEYCODE_BACK;; edge) adb shell input swipe 3 1300 600 1300 150;; edgeR) adb shell input swipe 1077 1300 480 1300 150;; down) adb shell input swipe 540 450 540 1800 200;; downslow) adb shell input swipe 540 450 540 1800 600;; esac
 sleep 3; recpull $n >/dev/null; av $n; grep -a "Leiste:" rec/$n.log | cut -c7-18,43- | tail -${L:-12}; }
# hasmini neu: X-Form bei (988,1954): Mitte + 4 Diagonalpunkte weiss, 4 Achsenpunkte deutlich dunkler
hasmini() { ytfront >/dev/null || return 1; local r; r=$(pixs "988 1954" "978 1944" "998 1944" "978 1964" "998 1964" "974 1954" "1002 1954" "988 1940" "988 1968" | awk 'NR<=5{if($1<215||$2<215||$3<215)bad=1} NR>5{if(($1+$2+$3)/3>205)bad=1} END{print bad?0:1}'); [ "$r" = 1 ]; }
# st NAME VON BIS [SCHRITT]: untere 330 px, 6 Spalten, gut lesbar
st() { local n=$1 a=$2 b=$3 s=${4:-1} c=${5:-6}; ffmpeg -loglevel error -y -i rec/$n.mp4 -vf "select='between(n,$a,$b)*not(mod(n-$a,$s))',crop=1080:${H:-330}:0:$((2424-${H:-330})),scale=320:-1,drawtext=text='%{n}':x=4:y=4:fontsize=20:fontcolor=yellow:box=1:boxcolor=black@0.6:fontfile=/Windows/Fonts/arial.ttf,tile=${c}x$(( ( (b-a)/s + c ) / c ))" -fps_mode passthrough -frames:v 1 rec/${n}_${a}.png && echo rec/${n}_${a}.png; }
live() { adb shell pkill logcat 2>/dev/null; adb logcat -c; (adb logcat -s FuckShorts > rec/$1.log 2>/dev/null &); sleep .3; }
# supp an|aus: unseren Dienst vorübergehend stilllegen (uiautomator events hält andere Bedienungshilfen an) -> echtes YouTube aufnehmen
supp() { if [ "$1" = an ]; then (adb shell uiautomator events >/dev/null 2>&1 &); sleep 2; else adb shell pkill -f uiautomator; sleep 6; fi; }
# rtop NAME A B: pro Bild oberste Zeile des Profilbilds an der echten Stelle (E) und an unserer Stelle (U); "-" = nicht da. Ruhelage: E=2275, U=2249
rtop() { local n=$1 a=$2 b=$3 m="255*gt(r(X,Y),150)*lt(g(X,Y),70)*gt(b(X,Y),55)*lt(b(X,Y),135)" z
  for z in "E 911" "U 640"; do set -- $z
    ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -vf "select='between(n,$a,$b)',crop=70:324:$2:2100,format=gbrp,geq=r='$m':g='$m':b='$m',scale=1:324:flags=area,format=gray" -f rawvideo - | od -An -v -tu1 -w324 | awk -v z=$1 -v a=$a '{y="-"; for(i=1;i<=NF;i++) if($i>50){y=2100+i-1;break} printf "%s%d:%s ", z, a+NR-1, y} END{print ""}'
  done; }
inst() { adb install -r -d 'C:\Users\A023985\NoShorts\app\build\outputs\apk\debug\app-debug.apk' 2>&1 | tail -1; sleep 3; adb shell 'settings get secure enabled_accessibility_services; dumpsys accessibility | grep -c "label=F\*ck Shorts"'; }
build() { (cd /c/Users/A023985/NoShorts && ./gradlew assembleDebug --offline -q 2>&1 | grep -vE "^$|warning:|w: " | tail -15); }
# plus NAME [zu]: Plus tippen, Kamera abwarten (Fotos-Frage nur mit Zurück schliessen), Kamera mit Zurück (zu=back|edge|x) schliessen
plus() { local n=$1 z=${2:-back}; ytfront || return 1; live $n; recstart 9; sleep 0.5; bartap 945 || return 1; sleep 2.2
  case "$(front)" in *GrantPermissionsActivity*) adb shell input keyevent KEYCODE_BACK; sleep 1.2; echo "(Fotos-Frage mit Zurück geschlossen)";; esac
  case "$(front)" in *CreationModesActivity*) case $z in back) adb shell input keyevent KEYCODE_BACK;; edge) adb shell input swipe 3 1300 600 1300 150;; x) adb shell input tap 84 256;; esac;; *) echo "KAMERA NICHT VORNE: $(front)";; esac
  sleep 2.5; recpull $n >/dev/null; av $n; grep -a "FuckShorts" rec/$n.log | grep -vE "Scan dauerte|Profilbild" | cut -c7-18,43- | tail -${L:-12}; }
# av neu: E = Profilbild-Farbe irgendwo im Leisten-Streifen (y2217-2424) ausser an unserer Stelle (auch seitlich verschobene echte Leiste), U = an unserer Stelle
av() { local n=$1 m="255*gt(r(X,Y),150)*lt(g(X,Y),70)*gt(b(X,Y),55)*lt(b(X,Y),135)"
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -filter_complex "[0:v]split=2[a][b];[a]crop=1080:207:0:2217,drawbox=x=620:y=0:w=110:h=207:color=black:t=fill,format=gbrp,geq=r='$m':g='$m':b='$m',format=gray,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/_a1.txt[ao];[b]crop=70:70:640:2241,format=gbrp,geq=r='$m':g='$m':b='$m',format=gray,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/_a2.txt[bo]" -map "[ao]" -f null - -map "[bo]" -f null -
  paste -d' ' <(grep -oE 'pts_time:[0-9.]+' rec/_a1.txt | cut -d: -f2) <(grep -oE 'YAVG=[0-9.]+' rec/_a1.txt | cut -d= -f2) <(grep -oE 'YAVG=[0-9.]+' rec/_a2.txt | cut -d= -f2) | awk '{printf "%d %.3f %.4f %.2f\n", NR-1, $1, $2/255, $3/255}' > rec/av_$n.txt
  avsum $n 0.12 0.003; }
vt() { local m=$1 n=$2; L=80 vl $m $n | grep -E "^[UE-] |Video verlassen|weggescrollt|geht auf|fährt herein|Pane:" | grep -vE "^U Bilder 0-" | cut -c1-120 | tail -${TL:-8}; }
# fv REF X Y NAME [METHODE] [WARTEN]: Video aus der Liste antippen (nur wenn Zeile wie im Referenzbild), warten, Foto, verlassen, auswerten
fv() { local ref=$1 x=$2 y=$3 n=$4 m=${5:-back} w=${6:-5}; ytfront || return 1; live $n; recstart $((w+7)); tapif $ref $x $y || { recpull $n >/dev/null; return 1; }; sleep $w
  adb exec-out screencap -p > rec/${n}_v.png; ffmpeg -loglevel fatal -y -i rec/${n}_v.png -vf scale=200:-1 rec/${n}_vs.png
  ytfront || return 1
  case $m in back) adb shell input keyevent KEYCODE_BACK;; edge) adb shell input swipe 3 1300 600 1300 150;; down) adb shell input swipe 540 450 540 1800 200;; esac
  sleep 3.2; recpull $n >/dev/null; av $n | grep -vE "^U Bilder 0-"; grep -a "Leiste:\|fährt herein" rec/$n.log | cut -c7-18,43- | cut -c1-110 | tail -${L:-9}; }
# lum NAME: mittlere Helligkeit unserer Leiste (y2225-2400) links/mitte/rechts pro Bild -> rec/lum_NAME.txt (Bild t L M R)
lum() { local n=$1; ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -filter_complex "[0:v]split=3[a][b][c];[a]crop=300:175:0:2225,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/_l1.txt[ao];[b]crop=300:175:390:2225,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/_l2.txt[bo];[c]crop=300:175:780:2225,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/_l3.txt[co]" -map "[ao]" -f null - -map "[bo]" -f null - -map "[co]" -f null -
  paste -d' ' <(grep -oE 'pts_time:[0-9.]+' rec/_l1.txt | cut -d: -f2) <(grep -oE 'YAVG=[0-9.]+' rec/_l1.txt | cut -d= -f2) <(grep -oE 'YAVG=[0-9.]+' rec/_l2.txt | cut -d= -f2) <(grep -oE 'YAVG=[0-9.]+' rec/_l3.txt | cut -d= -f2) | awk '{printf "%d %.3f %.1f %.1f %.1f\n", NR-1,$1,$2,$3,$4}' > rec/lum_$n.txt; }
# lumch NAME: nur Bilder zeigen, in denen sich die Helligkeit ändert
lumch() { awk '{d=($3-p3)+($4-p4)+($5-p5); if(d<0)d=-d; if(NR>1 && d>0.25) printf "%d(%.2f): %s %s %s\n",$1,$2,$3,$4,$5; p3=$3;p4=$4;p5=$5}' rec/lum_$1.txt; }
