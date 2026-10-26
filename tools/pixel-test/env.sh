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

# analyze NAME : pro Bild Anteil heller Pixel (Pixel 9, 1080x2424, Leiste oben bei y=2217):
#   S = Stelle des echten Shorts-Knopfs, E = echter Erstellen-Knopf (bei unserer Leiste beide leer),
#   U = rechter Teil unseres Startseite-Icons (bei der echten Leiste leer, unsere ≈ 20).
analyze() {
  local n=$1 th="lutyuv=y='if(gt(val,165),255,0)',signalstats"
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -filter_complex \
   "[0:v]split=3[a][b][c];\
    [a]crop=70:199:290:2225,$th,metadata=print:key=lavfi.signalstats.YAVG:file=rec/a.txt[ao];\
    [b]crop=90:100:490:2238,$th,metadata=print:key=lavfi.signalstats.YAVG:file=rec/b.txt[bo];\
    [c]crop=18:50:150:2245,$th,metadata=print:key=lavfi.signalstats.YAVG:file=rec/c.txt[co]" \
   -map "[ao]" -f null - -map "[bo]" -f null - -map "[co]" -f null -
  paste <(grep -o 'frame:[0-9]* *pts:[0-9]* *pts_time:[0-9.]*' rec/a.txt | awk '{split($1,a,":");split($3,c,":");printf "%d %.3f\n",a[2],c[2]}') \
        <(grep -o 'YAVG=[0-9.]*' rec/a.txt | cut -d= -f2) <(grep -o 'YAVG=[0-9.]*' rec/b.txt | cut -d= -f2) <(grep -o 'YAVG=[0-9.]*' rec/c.txt | cut -d= -f2) \
   | awk '{printf "%4d %7.3f  S=%5.1f E=%5.1f U=%5.1f%s\n",$1,$2,$3/2.55,$4/2.55,$5/2.55, ($3>5 && $4>5)?"  <<< ECHT?":""}' > rec/$n.ana
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
