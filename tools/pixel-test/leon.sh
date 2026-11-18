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
# etrack NAME [E|U]: Oberkante des Profilbilds pro Bild über das ganze Video, nur Änderungen ("-" = nicht da). E=echte (x911), U=unsere (x640)
etrack() { local n=$1 w=${2:-E} x=911 m="255*gt(r(X,Y),150)*lt(g(X,Y),70)*gt(b(X,Y),55)*lt(b(X,Y),135)"; [ "$w" = U ] && x=640
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -vf "crop=70:207:$x:2217,format=gbrp,geq=r='$m':g='$m':b='$m',scale=1:207:flags=area,format=gray" -f rawvideo - | od -An -v -tu1 -w207 | awk '{y="-"; for(i=1;i<=NF;i++) if($i>50){y=2217+i-1;break} print NR-1, y}' > rec/et_${n}_$w.txt
  paste -d' ' <(cut -d' ' -f2 rec/av_$n.txt 2>/dev/null) rec/et_${n}_$w.txt | awk '{ if($3!=p){printf "%d(%s):%s ", $2,$1,$3; p=$3} } END{print ""}'; }
# scrollmix NAME: gemischte Wischer (schnell/langsam/kurz, runter/hoch), Aufnahme + Auswertung
scrollmix() { local n=$1; ytfront || return 1; live $n; recstart ${2:-26}; sleep 0.5
  sw() { adb shell input swipe 540 $1 540 $2 $3; sleep $4; }
  sw 1700 900 150 2; sw 900 1500 150 2; sw 1700 900 150 1.5; sw 1100 1600 900 2; sw 1700 900 150 1; sw 1700 900 150 1
  sw 1300 1500 120 2; sw 1500 1300 120 2; sw 900 1500 150 2; sw 1600 1100 900 2; sw 1100 1600 900 2; sw 1700 900 150 2
  recpull $n >/dev/null; avs $n | grep -E "^E" ; echo "E-Abschnitte: $(grep -c . /dev/null)"; }
# avs NAME: genauer für Scroll-Tests: nur Spalte des echten Profilbilds (x900-990), Farbe eng um (194,23,91); E ab 2 % der Fläche
avs() { local n=$1 m="255*gt(r(X,Y),165)*lt(r(X,Y),225)*lt(g(X,Y),50)*gt(b(X,Y),70)*lt(b(X,Y),115)"
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -filter_complex "[0:v]split=2[a][b];[a]crop=90:207:900:2217,format=gbrp,geq=r='$m':g='$m':b='$m',format=gray,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/_a1.txt[ao];[b]crop=70:70:640:2241,format=gbrp,geq=r='$m':g='$m':b='$m',format=gray,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/_a2.txt[bo]" -map "[ao]" -f null - -map "[bo]" -f null -
  paste -d' ' <(grep -oE 'pts_time:[0-9.]+' rec/_a1.txt | cut -d: -f2) <(grep -oE 'YAVG=[0-9.]+' rec/_a1.txt | cut -d= -f2) <(grep -oE 'YAVG=[0-9.]+' rec/_a2.txt | cut -d= -f2) | awk '{printf "%d %.3f %.4f %.2f\n", NR-1, $1, $2/255, $3/255}' > rec/av_$n.txt
  avsum $n 0.12 0.02; }
# barok neu: unser Profilbild (magenta) muss an seiner festen Stelle stehen (Leiste ganz da, nicht weggescrollt)
barok() { adb exec-out screencap -p > rec/_b.png; local m="255*gt(r(X,Y),165)*lt(r(X,Y),225)*lt(g(X,Y),50)*gt(b(X,Y),70)*lt(b(X,Y),115)"; local v=$(ffmpeg -hide_banner -i rec/_b.png -vf "crop=50:50:650:2251,format=gbrp,geq=r='$m':g='$m':b='$m',format=gray,signalstats,metadata=print:key=lavfi.signalstats.YAVG" -f null - 2>&1 | grep -oE 'YAVG=[0-9.]+' | cut -d= -f2); awk -v v="${v:-0}" 'BEGIN{exit !(v>100)}'; }
# barshow: Liste hochwischen, bis unsere Leiste ganz da ist (höchstens 3×)
barshow() { for i in 1 2 3; do barok && return 0; ytfront || return 1; adb shell input swipe 540 900 540 1500 150; sleep 1.5; done; barok; }
scrollmix() { local n=$1; ytfront || return 1; live $n; recstart ${2:-26}; sleep 0.5
  sw() { adb shell input swipe 300 $1 300 $2 $3; sleep $4; }
  sw 1700 900 150 2; sw 900 1500 150 2; sw 1700 900 150 1.5; sw 1100 1600 900 2; sw 1700 900 150 1; sw 1700 900 150 1
  sw 1300 1500 120 2; sw 1500 1300 120 2; sw 900 1500 150 2; sw 1600 1100 900 2; sw 1100 1600 900 2; sw 1700 900 150 2
  recpull $n >/dev/null; local e=$(avs $n | grep "^E"); echo "${e:-keine echte Leiste}"
  echo "unsere weg/herein: $(grep -a 'Leiste:' rec/$n.log | grep -cE 'fährt runter|unsere mit') / $(grep -a 'Leiste:' rec/$n.log | grep -cE 'herein|sofort')"; }
tabgo() { barshow >/dev/null || { echo "LEISTE NICHT DA"; return 1; }; bartap $1 >/dev/null; sleep 3; adb shell input swipe 300 700 300 1900 150; sleep 2; }
barshow() { for i in 1 2 3; do barok && return 0; ytfront || return 1; adb shell input swipe 120 900 120 1500 150; sleep 1.5; done; barok; }
tabgo() { barshow >/dev/null || { echo "LEISTE NICHT DA"; return 1; }; bartap $1 >/dev/null; sleep 3; adb shell input swipe 120 700 120 1900 150; sleep 2; }
# scrolllong NAME: ~65 s, viel Stillstehen (Videos in der Liste laufen an, YouTube zeigt seine Leiste evtl. von selbst)
scrolllong() { local n=$1; ytfront || return 1; live $n; recstart 70; sleep 0.5
  sw() { adb shell input swipe 300 $1 300 $2 $3; sleep $4; }
  sw 1700 900 150 6; sw 1700 900 150 7; sw 1300 1500 400 5; sw 1500 1250 400 6; sw 900 1500 150 6
  sw 1700 900 150 4; sw 1600 1200 1200 5; sw 1200 1600 1200 6; sw 1700 900 150 8; sw 900 1500 150 4
  recpull $n >/dev/null; local e=$(avs $n | grep "^E"); echo "${e:-keine echte Leiste}"
  echo "runter/herein: $(grep -a 'Leiste:' rec/$n.log | grep -cE 'fährt runter|unsere mit') / $(grep -a 'Leiste:' rec/$n.log | grep -cE 'herein|sofort')"; }
# vt2 NAME METHODE X Y: auf der aktuellen Seite (Referenzfoto) Video antippen, 5 s, verlassen, auswerten
vt2() { local n=$1 m=$2 x=$3 y=$4; ytfront || return 1; adb exec-out screencap -p > rec/${n}_r.png; live $n; recstart 13; sleep 0.5; tapif ${n}_r $x $y || { recpull $n >/dev/null; return 1; }; sleep 5; ytfront || return 1
  case $m in back) adb shell input keyevent KEYCODE_BACK;; edge) adb shell input swipe 3 1300 600 1300 150;; down) adb shell input swipe 540 450 540 1800 200;; esac
  sleep 3.5; recpull $n >/dev/null; local e=$(avs $n | grep '^E'); echo "$n $m: ${e:-keine echte Leiste}"; }
# fastmix NAME: 6× schnell runter/hoch im Wechsel
fastmix() { local n=$1; ytfront || return 1; live $n; recstart ${2:-14}; sleep 0.5; for i in 1 2 3 4 5 6; do adb shell input swipe 120 1700 120 900 120; sleep ${3:-0.45}; adb shell input swipe 120 900 120 1600 120; sleep ${3:-0.45}; done; sleep 2; recpull $n >/dev/null; local e=$(avs $n | grep '^E' | tr '\n' '|'); echo "$n: ${e:-keine echte Leiste}"; }
# rbar NAME: sucht in jedem Bild den Plus-Knopf der ECHTEN Leiste (Kreis r=28 px + Kreuz bei x=675), auch beim Hochfahren
#  (Mitte y 2309..2410). Hell und dunkel (Kontrast zum Hintergrund). Ausgabe: Abschnitte mit echter Leiste + Lage.
rbar() { local n=$1
  ffmpeg -loglevel fatal -i rec/$n.mp4 -fps_mode passthrough -vf "format=gray,crop=110:207:620:2217" -f rawvideo - | od -An -v -tu1 -w22770 | awk '
  function px(x,y){ return $( (y-2217)*110 + (x-620) + 1 ) }
  { found=0; best=""
    for (cy=2309; cy<=2414; cy++) {
      bg=px(675+42, cy); if (bg=="") continue
      ok=1; c=0
      # Ring (8 Punkte), Kreuz (5 Punkte): deutlich anders als Hintergrund
      split("28 0,-28 0,0 28,0 -28,20 20,-20 20,20 -20,-20 -20,0 0,9 0,-9 0,0 9,0 -9", P, ",")
      for (i in P) { split(P[i], q, " "); y=cy+q[2]; if (y>2423) continue; v=px(675+q[1], y); d=v-bg; if (d<0) d=-d; if (d<70) {ok=0; break} c++ }
      if (!ok || c<8) continue
      # Lücken zwischen Ring und Kreuz: wie Hintergrund
      split("14 14,-14 14,14 -14,-14 -14", G, ",")
      for (i in G) { split(G[i], q, " "); y=cy+q[2]; if (y>2423) continue; v=px(675+q[1], y); d=v-bg; if (d<0) d=-d; if (d>45) {ok=0; break} }
      if (ok) { found=1; best=cy; break }
    }
    f=NR-1
    if (found && !on) { on=1; st=f; y0=best }
    if (!found && on) { printf "ECHT Bilder %d-%d (%d) Plus-Mitte erst y=%d\n", st, f-1, f-st, y0; on=0; cnt++ }
  }
  END { if (on) { printf "ECHT Bilder %d-%d (%d) Plus-Mitte erst y=%d\n", st, NR-1, NR-st, y0; cnt++ } printf "Bilder %d, Abschnitte mit echter Leiste: %d\n", NR, cnt+0 }'; }
# rbar NAME: echte Leiste am Plus-Knopf erkennen (Kreis + Kreuz bei x=675), Mitte y 2300..2414, Grösse 45..100 %.
rbar() { local n=$1
  ffmpeg -loglevel fatal -i rec/$n.mp4 -fps_mode passthrough -vf "format=gray,crop=110:207:620:2217" -f rawvideo - | od -An -v -tu1 -w22770 | awk '
  function px(x,y){ if (y>2423 || y<2217) return ""; return $( (y-2217)*110 + (x-620) + 1 ) }
  function test(cy, s,   bg, i, q, v, d, c, ok) {
    bg=px(675+int(42*s+0.5), cy); if (bg=="") return 0
    c=0
    for (i=1;i<=13;i++) { split(P[i], q, " "); v=px(675+int(q[1]*s+(q[1]<0?-0.5:0.5)), cy+int(q[2]*s+(q[2]<0?-0.5:0.5))); if (v=="") continue; d=v-bg; if (d<0) d=-d; if (d<60) return 0; c++ }
    if (c<8) return 0
    for (i=1;i<=4;i++) { split(G[i], q, " "); v=px(675+int(q[1]*s+(q[1]<0?-0.5:0.5)), cy+int(q[2]*s+(q[2]<0?-0.5:0.5))); if (v=="") continue; d=v-bg; if (d<0) d=-d; if (d>45) return 0 }
    return 1 }
  BEGIN { split("28 0,-28 0,0 28,0 -28,20 20,-20 20,20 -20,-20 -20,0 0,9 0,-9 0,0 9,0 -9", P, ","); split("14 14,-14 14,14 -14,-14 -14", G, ","); ns=split("1 0.9 0.8 0.7 0.6 0.5", S, " ") }
  { found=0
    for (k=1;k<=ns && !found;k++) for (cy=2300; cy<=2414 && !found; cy++) if (test(cy, S[k])) { found=1; fy=cy; fs=S[k] }
    f=NR-1
    if (found && !on) { on=1; st=f; y0=fy; s0=fs }
    if (!found && on) { printf "ECHT Bilder %d-%d (%d) erst y=%d Grösse %s\n", st, f-1, f-st, y0, s0; on=0; cnt++ }
  }
  END { if (on) { printf "ECHT Bilder %d-%d (%d) erst y=%d Grösse %s\n", st, NR-1, NR-st, y0, s0; cnt++ } printf "Bilder %d, Abschnitte mit echter Leiste: %d\n", NR, cnt+0 }'; }
# Moderne Leiste (MODERN=1): unser Profilbild in der Mitte (x 515-565), Knöpfe Home 180, You 540, Plus 900
barok() { adb exec-out screencap -p > rec/_b.png; local m="255*gt(r(X,Y),165)*lt(r(X,Y),225)*lt(g(X,Y),50)*gt(b(X,Y),70)*lt(b(X,Y),115)"; local c="crop=50:50:650:2251"; [ "$MODERN" = 1 ] && c="crop=40:40:520:2289"; local v=$(ffmpeg -hide_banner -i rec/_b.png -vf "$c,format=gbrp,geq=r='$m':g='$m':b='$m',format=gray,signalstats,metadata=print:key=lavfi.signalstats.YAVG" -f null - 2>&1 | grep -oE 'YAVG=[0-9.]+' | cut -d= -f2); awk -v v="${v:-0}" 'BEGIN{exit !(v>100)}'; }
rbarL() { local n=$1
  ffmpeg -loglevel fatal -i rec/$n.mp4 -fps_mode passthrough -vf "format=gray,crop=110:207:1525:873" -f rawvideo - | od -An -v -tu1 -w22770 | awk '
  function px(x,y){ if (y>1079 || y<873) return ""; return $( (y-873)*110 + (x-1525) + 1 ) }
  function test(cy, s,   bg, i, q, v, d, c, ok) {
    bg=px(1580+int(42*s+0.5), cy); if (bg=="") return 0
    c=0
    for (i=1;i<=13;i++) { split(P[i], q, " "); v=px(1580+int(q[1]*s+(q[1]<0?-0.5:0.5)), cy+int(q[2]*s+(q[2]<0?-0.5:0.5))); if (v=="") continue; d=v-bg; if (d<0) d=-d; if (d<60) return 0; c++ }
    if (c<8) return 0
    for (i=1;i<=4;i++) { split(G[i], q, " "); v=px(1580+int(q[1]*s+(q[1]<0?-0.5:0.5)), cy+int(q[2]*s+(q[2]<0?-0.5:0.5))); if (v=="") continue; d=v-bg; if (d<0) d=-d; if (d>45) return 0 }
    return 1 }
  BEGIN { split("28 0,-28 0,0 28,0 -28,20 20,-20 20,20 -20,-20 -20,0 0,9 0,-9 0,0 9,0 -9", P, ","); split("14 14,-14 14,14 -14,-14 -14", G, ","); ns=split("1 0.9 0.8 0.7 0.6 0.5", S, " ") }
  { found=0
    for (k=1;k<=ns && !found;k++) for (cy=956; cy<=1070 && !found; cy++) if (test(cy, S[k])) { found=1; fy=cy; fs=S[k] }
    f=NR-1
    if (found && !on) { on=1; st=f; y0=fy; s0=fs }
    if (!found && on) { printf "ECHT Bilder %d-%d (%d) erst y=%d Grösse %s\n", st, f-1, f-st, y0, s0; on=0; cnt++ }
  }
  END { if (on) { printf "ECHT Bilder %d-%d (%d) erst y=%d Grösse %s\n", st, NR-1, NR-st, y0, s0; cnt++ } printf "Bilder %d, Abschnitte mit echter Leiste: %d\n", NR, cnt+0 }'; }
# Sitzung 17./18.11.2026: Zusatz-Hilfen (nach s15.sh laden)
# ghost an|aus: unsere Leiste unsichtbar (nur Test-Version), Trace dazu
ghost() { if [ "$1" = an ]; then adb shell 'setprop log.tag.FShortsGhost VERBOSE; setprop log.tag.FShortsTrace VERBOSE'; else adb shell 'setprop log.tag.FShortsGhost INFO; setprop log.tag.FShortsTrace INFO'; fi; sleep 0.5; }
# mot NAME: pro Bild Bewegung des Inhalts (Unterschied zum Vorbild, y 500-1900) -> rec/mot_NAME.txt (Bild t Wert)
mot() { local n=$1; ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -vf "crop=900:1400:180:500,format=gray,tblend=all_mode=difference,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/_m.txt" -f null -
  paste -d' ' <(grep -oE 'pts_time:[0-9.]+' rec/_m.txt | cut -d: -f2) <(grep -oE 'YAVG=[0-9.]+' rec/_m.txt | cut -d= -f2) | awk '{printf "%d %.3f %.2f\n", NR, $1, $2}' > rec/mot_$n.txt; }
# trk NAME [E|U]: pro Bild Zeit, Inhalts-Bewegung, Oberkante Profilbild (echte E an x911 / unsere U an x640)
trk() { local n=$1 w=${2:-E} x=911 m="255*gt(r(X,Y),150)*lt(g(X,Y),70)*gt(b(X,Y),55)*lt(b(X,Y),135)"; [ "$w" = U ] && x=640
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -vf "crop=70:207:$x:2217,format=gbrp,geq=r='$m':g='$m':b='$m',scale=1:207:flags=area,format=gray" -f rawvideo - | od -An -v -tu1 -w207 | awk '{y="-"; for(i=1;i<=NF;i++) if($i>50){y=2217+i-1;break} print NR-1, y}' > rec/et_${n}_$w.txt
  mot $n; paste -d' ' rec/et_${n}_$w.txt rec/mot_$n.txt | awk '{printf "%d %s %s %s\n", $1, $4, $5, $2}' > rec/trk_${n}_$w.txt; }
# trksum NAME [E|U]: nur Bilder mit Bewegung oder Änderung der Leiste (Bild t Bewegung Lage)
trksum() { awk '{ if ($3>0.4 || $4!=p) printf "%d %.3f %s %s\n", $1,$2,$3,$4; p=$4 }' rec/trk_${1}_${2:-E}.txt; }
# upmix NAME: gemischte Wischer (leicht/langsam/mittel/schnell hoch, schnell runter), Trace, rbar + Ereignisse
upmix() { local n=$1; ytfront || return 1; adb shell setprop log.tag.FShortsTrace VERBOSE; live $n; recstart ${2:-34}; sleep 0.5; : > rec/$n.mk
  sw() { echo "SW $1 $2 $3 $(date +%T.%N|cut -c1-12)" >> rec/$n.mk; adb shell input swipe 120 $1 120 $2 $3; sleep $4; }
  sw 1700 900 120 1.8; sw 1000 1060 400 1.6; sw 1000 1150 600 1.6; sw 900 1200 600 1.6; sw 1700 900 120 1.8
  sw 900 1500 2000 1.6; sw 1700 900 120 1.8; sw 900 1300 300 1.8; sw 1700 900 120 1.8; sw 900 1600 120 2.0; sw 1700 900 120 1.8; sw 900 1200 230 2
  recpull $n >/dev/null; adb shell setprop log.tag.FShortsTrace INFO; rbar $n; grep -a "Leiste:" rec/$n.log | sed 's/.*FuckShorts: //' | grep -vE "normal \(top|Scan" | cut -c1-110; }
# swcont: Superwalls Test-Fenster (nur Test-Versionen) mit "Continue" schliessen (Zustand unverändert lassen)
swcont() { adb shell input swipe 540 1900 540 900 300; sleep 0.8; adb shell input swipe 540 2000 540 700 300; sleep 1.2; adb shell input tap 540 2033; sleep 2; }
# appsw NAME: per Gestenleiste zur vorherigen App und wieder zurück (Aufnahme, rbar)
appsw() { local n=$1; ytfront || return 1; live $n; recstart 9; sleep 0.5; adb shell input swipe 200 2405 900 2405 250; sleep 2.2; echo "vorne: $(front)"; adb shell input swipe 900 2405 200 2405 250; sleep 3; recpull $n >/dev/null; ytfront && rbar $n | tail -2; }
# ovw NAME: App-Übersicht und mit Tipp auf die YouTube-Karte zurück (nur wenn die Übersicht vorne ist)
ovw() { local n=$1; ytfront || return 1; live $n; recstart 9; sleep 0.5; adb shell input keyevent KEYCODE_APP_SWITCH; sleep 2; local f=$(front); echo "vorne: $f"; case "$f" in *launcher*|*Launcher*|*recents*|*Recents*|*systemui*) adb shell input tap 540 1200;; *) adb shell input keyevent KEYCODE_BACK;; esac; sleep 3; recpull $n >/dev/null; ytfront && rbar $n | tail -2; }
# shade NAME: Benachrichtigungen auf und zu
shade() { local n=$1; ytfront || return 1; live $n; recstart 8; sleep 0.5; adb shell input swipe 540 5 540 1400 250; sleep 1.8; adb shell input swipe 540 2300 540 300 250; sleep 2.5; recpull $n >/dev/null; ytfront && rbar $n | tail -2; }
shade () 
{ 
    local n=$1;
    ytfront || return 1;
    live $n;
    recstart 8;
    sleep 0.5;
    adb shell input swipe 540 5 540 1400 250;
    sleep 1.8;
    adb shell input keyevent KEYCODE_BACK;
    sleep 2.5;
    recpull $n > /dev/null;
    ytfront && rbar $n | tail -2
}
# tabt NAME X: Tipp auf unsere Leiste bei x=X (Aufnahme 6 s, rbar, gewählter Tab)
tabt() { local n=$1 x=$2; ytfront || return 1; barshow >/dev/null || { echo "LEISTE NICHT DA"; return 1; }; live $n; recstart 6; sleep 0.4; bartap $x >/dev/null; sleep 3.5; recpull $n >/dev/null; echo "$n x=$x: $(rbar $n | tail -1) | $(grep -a 'Tab gewählt\|Tipp:' rec/$n.log | sed 's/.*FuckShorts: //' | tail -2 | tr '\n' ' ')"; }
# srch NAME: Lupe, Text, Suchen, 2× Zurück (jeder Schritt nur, wenn YouTube vorne ist)
srch() { local n=$1; tofeed >/dev/null; ytfront || return 1; live $n; recstart 16; sleep 0.5; adb shell input tap 1008 240; sleep 1.5
  ytfront || return 1; adb shell input text "klavier"; sleep 1; ytfront || return 1; adb shell input keyevent KEYCODE_ENTER; sleep 3.5
  ytfront || return 1; adb shell input keyevent KEYCODE_BACK; sleep 2; ytfront || return 1; adb shell input keyevent KEYCODE_BACK; sleep 3
  recpull $n >/dev/null; echo "$n: $(rbar $n | tail -1)"; }
# menu NAME X Y: ⋮ bei X,Y antippen (nur wenn unverändert), 2 s, Zurück; rbar
menu() { local n=$1 x=$2 y=$3; ytfront || return 1; adb exec-out screencap -p > rec/${n}_r.png; live $n; recstart 8; sleep 0.4; tapif ${n}_r $x $y || { recpull $n >/dev/null; return 1; }; sleep 2; ytfront || return 1; adb shell input keyevent KEYCODE_BACK; sleep 3; recpull $n >/dev/null; echo "$n: $(rbar $n | tail -1)"; }
menu () 
{ 
    local n=$1 x=$2 y=$3;
    ytfront || return 1;
    adb exec-out screencap -p > rec/${n}_r.png;
    live $n;
    recstart 8;
    sleep 0.4;
    tapif ${n}_r $x $y || { 
        recpull $n > /dev/null;
        return 1
    };
    sleep 2;
    case "$(front)" in 
        *youtube* | *PopupWindow*)
            adb shell input keyevent KEYCODE_BACK
        ;;
        *)
            echo "VORNE: $(front)";
            return 1
        ;;
    esac;
    sleep 3;
    recpull $n > /dev/null;
    echo "$n: $(rbar $n | tail -1)"
}
# bexit NAME [key|edge]: YouTube vom Startbildschirm öffnen, Startseite, Zurück (Taste oder Wischgeste) -> YouTube schliesst. Blatt der Animation.
bexit() { local n=$1 m=${2:-key}; adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell monkey -p com.google.android.youtube -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 3; tofeed >/dev/null; barshow >/dev/null || return 1; ytfront || return 1
  live $n; recstart 6; sleep 0.8; adb shell log -t FuckShorts "MARK back"; if [ "$m" = edge ]; then adb shell input swipe 3 1300 600 1300 150; else adb shell input keyevent KEYCODE_BACK; fi; sleep 3; recpull $n >/dev/null; echo "$n vorne: $(front)"
  local f=$(gap3 $n 3 2>/dev/null | grep -oE 'Zone A: Bild [0-9]+' | grep -oE '[0-9]+$'); f=${f:-60}; ffmpeg -loglevel error -y -i rec/$n.mp4 -vf "select='between(n,$((f-4)),$((f+24)))*not(mod(n-$((f-4)),2))',scale=216:-1,drawtext=text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.6:fontfile=/Windows/Fonts/arial.ttf,tile=8x2" -fps_mode passthrough -frames:v 1 rec/${n}_x.png; echo rec/${n}_x.png; }
# srchback NAME: Lupe, Text, Zurück (Tastatur zu, Vorschläge bleiben), 1,5 s, Zurück (Startseite). Blatt um das Tastatur-Schliessen.
srchback() { local n=$1; tofeed >/dev/null; ytfront || return 1; live $n; recstart 12; sleep 0.5; adb shell input tap 1008 240; sleep 1.5
  ytfront || return 1; adb shell input text "hund"; sleep 1.2; ytfront || return 1; adb shell log -t FuckShorts "MARK zu"; adb shell input keyevent KEYCODE_BACK; sleep 1.8
  ytfront || return 1; adb shell input keyevent KEYCODE_BACK; sleep 3; recpull $n >/dev/null; echo "$n: $(rbar $n | tail -1)"; }
vlx () 
{ 
    local m=$1 n=$2 y=$3;
    ytfront || return 1;
    live $n;
    recstart 9;
    adb shell input tap 700 $y;
    sleep 3.5;
    ytfront || return 1;
    case $m in 
        back)
            adb shell input keyevent KEYCODE_BACK
        ;;
        edge)
            adb shell input swipe 3 1300 600 1300 150
        ;;
        down)
            adb shell input swipe 540 450 540 1800 200
        ;;
    esac;
    sleep 3;
    recpull $n > /dev/null;
    echo "$m: $(rbar $n | tail -1) | $(grep -a 'Leiste:' rec/$n.log | sed 's/.*FuckShorts: //' | grep -c 'Video verlassen') verlassen"
}
homere () 
{ 
    local n=$1;
    ytfront || return 1;
    barshow > /dev/null;
    live $n;
    recstart 9;
    sleep 0.5;
    adb shell input keyevent KEYCODE_HOME;
    sleep 2;
    adb shell monkey -p com.google.android.youtube -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1;
    sleep 3.5;
    recpull $n > /dev/null;
    echo "$n: $(rbar $n | tail -1)"
}
