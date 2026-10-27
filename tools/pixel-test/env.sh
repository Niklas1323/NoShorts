# Test-Werkzeuge für das Pixel (Git Bash unter Windows). Benutzung:
#   cd <Arbeitsordner>; mkdir -p rec; . <repo>/tools/pixel-test/env.sh; . <repo>/tools/pixel-test/suite.sh; t_scroll
# T = Ordner mit platform-tools und ffmpeg (ohne Admin-Rechte als ZIP entpackt).
T=${T:-$HOME/tools}
export PATH="$T/platform-tools:$T/ffmpeg-master-latest-win64-gpl/bin:$PATH"
export MSYS_NO_PATHCONV=1
mkdir -p rec

shot() { adb exec-out screencap -p > "rec/$1.png"; }
# ACHTUNG: uiautomator schaltet unseren Dienst ~2 s ab. Nie während einer Aufnahme!
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "rec/$1.xml" >/dev/null 2>&1; }
tap() { adb shell input tap $1 $2; }
yt() { adb shell am start -n com.google.android.youtube/com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity >/dev/null 2>&1; sleep ${1:-3}; }
logall() { adb logcat -d -s FuckShorts | grep -E "Leiste:|Tipp:"; }
# center NAME REGEX : Mitte des ersten Elements, dessen content-desc oder text passt ("x y")
center() { tr '>' '\n' < rec/$1.xml | grep -E "(content-desc|text)=\"$2\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | paste -sd' ' | awk '{if(NF==4)print int(($1+$3)/2), int(($2+$4)/2)}'; }

# recstart SEKUNDEN : Aufnahme im Hintergrund; recpull NAME : warten bis fertig, abholen
recstart() { adb shell rm -f /sdcard/r.mp4; (adb shell screenrecord --bit-rate 16000000 --time-limit $1 /sdcard/r.mp4 >/dev/null 2>&1 &) ; sleep 1.2; }
recpull() { local i=0; while adb shell pidof screenrecord >/dev/null 2>&1 && [ $i -lt 60 ]; do sleep 1; i=$((i+1)); done; sleep 0.5; adb pull /sdcard/r.mp4 rec/$1.mp4 >/dev/null 2>&1; ls -la rec/$1.mp4 | awk '{print $5, $NF}'; }

# analyze NAME : pro Bild Anteil "Zeichen"-Pixel (Pixel 9, 1080x2424, Leiste oben bei y=2217):
#   S = Stelle des echten Shorts-Knopfs, E = echter Erstellen-Knopf (bei unserer Leiste beide leer),
#   U = rechter Teil unseres Startseite-Icons (bei der echten Leiste leer, unsere ≈ 20).
#   Hell und dunkel: Pro Bild wird der Hintergrund der Leiste gemessen (B, Streifen über den Icons).
#   Dunkler Hintergrund -> helle Pixel zählen, heller Hintergrund -> dunkle Pixel zählen.
#   "~" = Streifen nicht einfarbig (dort ist keine Leiste, z. B. Video-Seite). ECHT nur bei einfarbigem Streifen,
#   Zeichen bei S und E und leerem U. Ein schmaler Streifen der echten Leiste fällt so NICHT auf: strips ansehen!
analyze() {
  local n=$1 hi="lutyuv=y='if(gt(val,165),255,0)',signalstats" lo="lutyuv=y='if(lt(val,90),255,0)',signalstats"
  local m="metadata=print:key=lavfi.signalstats.YAVG:file=rec"
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -filter_complex \
   "[0:v]split=7[a][b][c][d][e][f][g];\
    [a]crop=70:199:290:2225,$hi,$m/a.txt[ao];\
    [b]crop=90:100:490:2238,$hi,$m/b.txt[bo];\
    [c]crop=18:50:150:2245,$hi,$m/c.txt[co];\
    [d]crop=70:199:290:2225,$lo,$m/d.txt[do];\
    [e]crop=90:100:490:2238,$lo,$m/e.txt[eo];\
    [f]crop=18:50:150:2245,$lo,$m/f.txt[fo];\
    [g]crop=1000:10:40:2224,signalstats,metadata=print:file=rec/g.txt[go]" \
   -map "[ao]" -f null - -map "[bo]" -f null - -map "[co]" -f null - \
   -map "[do]" -f null - -map "[eo]" -f null - -map "[fo]" -f null - -map "[go]" -f null -
  local v; v() { grep -o 'YAVG=[0-9.]*' rec/$1.txt | cut -d= -f2; }
  paste <(grep -o 'frame:[0-9]* *pts:[0-9]* *pts_time:[0-9.]*' rec/a.txt | awk '{split($1,a,":");split($3,c,":");printf "%d %.3f\n",a[2],c[2]}') \
        <(v a) <(v b) <(v c) <(v d) <(v e) <(v f) <(v g) \
        <(grep -o 'YLOW=[0-9.]*' rec/g.txt | cut -d= -f2) <(grep -o 'YHIGH=[0-9.]*' rec/g.txt | cut -d= -f2) \
   | awk '{ light=($9>128); s=light?$6:$3; e=light?$7:$4; u=light?$8:$5; flat=($11-$10<=12);
            printf "%4d %7.3f  S=%5.1f E=%5.1f U=%5.1f B=%3d%s%s\n",$1,$2,s/2.55,e/2.55,u/2.55,$9, flat?"":" ~",
              (s>5 && e>5 && u<5 && flat)?"  <<< ECHT?":"" }' > rec/$n.ana
  grep -c ECHT rec/$n.ana
}
# Bereiche mit (vielleicht) echter Leiste / ohne unsere Leiste. Immer mit frames/strips nachsehen!
ranges() { awk '/ECHT/{ if($1!=last+1){ if(s!="")print s"-"last" ("st"s)"; s=$1; st=$2 } last=$1 } END{ if(s!="")print s"-"last" ("st"s)" }' rec/$1.ana; }
gone() { awk '{u=substr($0,index($0,"U=")+2)+0; if(u<10){ if($1!=last+1){ if(s!="")print s"-"last" ("st"s)"; s=$1; st=$2 } last=$1 } } END{ if(s!="")print s"-"last" ("st"s)" }' rec/$1.ana; }

# strips NAME [START] [ANZAHL] [Y] [H] : Ausschnitt jedes Bildes als Raster, mit Bildnummer und Zeit
strips() {
  local n=$1 st=${2:-0} cnt=${3:-48} y=${4:-2094} h=${5:-330}
  ffmpeg -loglevel error -y -i rec/$n.mp4 -fps_mode passthrough \
   -vf "select='between(n\,$st\,$(($st+$cnt-1)))',crop=1080:$h:0:$y,scale=540:-2,drawtext=fontfile='C\:/Windows/Fonts/arial.ttf':text='%{eif\:n+$st\:d} %{pts\:flt}':fontsize=22:fontcolor=yellow:box=1:boxcolor=black@0.7:x=4:y=4,tile=4x$(( (cnt+3)/4 ))" \
   -frames:v 1 rec/${n}_s$st.png
}
# frames NAME N1 N2 ... : ganze Bilder nebeneinander
frames() { local n=$1; shift; local sel=$(printf 'eq(n\\,%s)+' "$@"); sel=${sel%+}
  ffmpeg -loglevel error -y -i rec/$n.mp4 -fps_mode passthrough -vf "select='$sel',scale=400:-2,drawtext=fontfile='C\:/Windows/Fonts/arial.ttf':text='%{pts\:flt}':fontsize=24:fontcolor=yellow:box=1:boxcolor=black@0.7:x=4:y=40,tile=$#x1" -frames:v 1 rec/${n}_f.png; }

# Shorts-Symbol der echten Leiste suchen (x 275–325 in der Icon-Reihe; bei unserer Leiste leer). Daneben (x 190–240)
# ist bei beiden leer und muss einfarbig sein. Gibt verdächtige Bildbereiche aus – immer mit strips ansehen.
shortsdet() {
  ffmpeg -loglevel fatal -y -i rec/$1.mp4 -fps_mode passthrough -filter_complex "[0:v]split=2[a][b];[a]crop=50:55:275:2258,signalstats,metadata=print:file=rec/sd1.txt[ao];[b]crop=50:55:190:2258,signalstats,metadata=print:file=rec/sd2.txt[bo]" -map "[ao]" -f null - -map "[bo]" -f null -
  paste <(grep -o 'frame:[0-9]* *pts:[0-9]* *pts_time:[0-9.]*' rec/sd1.txt | awk '{split($1,a,":");split($3,c,":");print a[2], c[2]}') \
        <(grep -o 'YMIN=[0-9]*' rec/sd1.txt | cut -d= -f2) <(grep -o 'YMAX=[0-9]*' rec/sd1.txt | cut -d= -f2) \
        <(grep -o 'YMIN=[0-9]*' rec/sd2.txt | cut -d= -f2) <(grep -o 'YMAX=[0-9]*' rec/sd2.txt | cut -d= -f2) \
  | awk '{ if ($4-$3>90 && $6-$5<14) { if($1!=last+1){ if(s!="")print s"-"last" ("st"s)"; s=$1; st=$2 } last=$1 } } END{ if(s!="")print s"-"last" ("st"s)" }'
}
