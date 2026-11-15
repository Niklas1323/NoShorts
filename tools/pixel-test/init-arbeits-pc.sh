export T=/c/Users/A023985/tools
cd /c/Users/A023985/ptest
. ../NoShorts/tools/pixel-test/env.sh >/dev/null 2>&1
. ../NoShorts/tools/pixel-test/phone.sh
# gap NAME: erstes Bild, auf dem sich die Seite (Zone A y1300-2100) ändert, und erstes Bild, auf dem sich unsere Leiste (Zone B y2240-2370) ändert; Referenz = Bild 5
gap() { local n=$1
  for z in "A 1300 800" "B 2240 130"; do set -- $z
    ffmpeg -loglevel fatal -y -i rec/$n.mp4 -vf "select=eq(n\,5)" -frames:v 1 rec/_ref.png
    ffmpeg -loglevel fatal -y -i rec/$n.mp4 -i rec/_ref.png -filter_complex "[1:v]format=yuv420p,crop=1080:$3:0:$2[r];[0:v]format=yuv420p,crop=1080:$3:0:$2[a];[a][r]blend=all_mode=difference:shortest=0:repeatlast=1,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/g$1.txt" -fps_mode passthrough -f null - 
    paste -d' ' <(grep -oE 'pts_time:[0-9.]+' rec/g$1.txt | cut -d: -f2) <(grep -oE 'YAVG=[0-9.]+' rec/g$1.txt | cut -d= -f2) | awk -v z=$1 -v nm=$n '$2>3{print "Zone " z " erste Änderung: Bild " NR-1 " t=" $1; exit}'
  done
}
gap2 () 
{ 
    local n=$1 ref=${2:-100};
    ffmpeg -loglevel fatal -y -i rec/$n.mp4 -vf "select=eq(n\,$ref)" -frames:v 1 rec/_ref.png;
    for z in "A 300 1100" "B 2240 130";
    do
        set -- $z;
        ffmpeg -loglevel fatal -y -i rec/$n.mp4 -i rec/_ref.png -filter_complex "[1:v]format=yuv420p,crop=1080:$3:0:$2[r];[0:v]format=yuv420p,crop=1080:$3:0:$2[a];[a][r]blend=all_mode=difference:shortest=0:repeatlast=1,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/g$1.txt" -fps_mode passthrough -f null -;
        paste -d' ' <(grep -oE 'pts_time:[0-9.]+' rec/g$1.txt | cut -d: -f2) <(grep -oE 'YAVG=[0-9.]+' rec/g$1.txt | cut -d= -f2) | awk -v z=$1 -v r=$ref 'NR>r && $2>2.5{print "Zone " z " erste Änderung nach Bild " r ": Bild " NR-1 " t=" $1; exit}';
    done
}
# pix X Y : RGB eines Pixels im frischen Bildschirmfoto
pix() { adb exec-out screencap -p > rec/_p.png; ffmpeg -loglevel fatal -y -i rec/_p.png -vf "crop=1:1:$1:$2,format=rgb24" -f rawvideo - | od -An -tu1 | tr -s ' '; }
# miniclose: schliesst den Mini-Player, falls sein roter Fortschrittsstrich (y~2190) da ist
miniclose() { for y in 2186 2188 2190 2192; do set -- $(pix 600 $y); if [ "${1:-0}" -gt 200 ] && [ "${2:-255}" -lt 90 ]; then tap 986 1950; sleep 2; echo "Mini-Player zu"; return 0; fi; done; echo "kein Mini-Player"; }
# vrun NAME: Mini-Player zu, aufnehmen, Video antippen, 4 s, Zurück, auswerten
vrun() { local n=$1; miniclose; recstart 10; sleep 1.5; adb shell input tap 540 600; sleep 4.5; adb shell input keyevent KEYCODE_BACK; sleep 2; recpull $n >/dev/null; gap2 $n 120; }
# onfeed: Startliste vorne? (rotes YouTube-Logo oben links)
onfeed() { case "$(focus)" in com.google.android.youtube/*) ;; *) return 1;; esac; set -- $(pix 58 232); [ "${1:-0}" -gt 200 ] && [ "${2:-255}" -lt 60 ] && [ "${3:-255}" -lt 60 ]; }
# tofeed: bis zu 4× Zurück, bis die Startliste da ist (Mini-Player danach schliessen)
tofeed() { for i in 1 2 3 4; do onfeed && { miniclose; return 0; }; case "$(focus)" in com.google.android.youtube/*) adb shell input keyevent KEYCODE_BACK; sleep 1.5;; *) yt 3;; esac; done; onfeed; }
vrun() { local n=$1; tofeed || { echo "KEINE LISTE"; return 1; }; recstart 10; sleep 1.5; adb shell input tap 540 600; sleep 4.5; adb shell input keyevent KEYCODE_BACK; sleep 2; recpull $n >/dev/null; gap2 $n 120; }
# gap3 NAME [REF]: Zeitpunkt, an dem die Seite die Mitte abdeckt (A, grosse Änderung), und an dem unsere Leiste sich bewegt (B, Beschriftungen)
gap3() { local n=$1 ref=${2:-3}
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -vf "select=eq(n\,$ref)" -frames:v 1 rec/_ref.png
  for z in "A 0 1100 1080 700 14" "B 0 2290 300 60 5"; do set -- $z
    ffmpeg -loglevel fatal -y -i rec/$n.mp4 -i rec/_ref.png -filter_complex "[1:v]format=yuv420p,crop=$4:$5:$2:$3[r];[0:v]format=yuv420p,crop=$4:$5:$2:$3[a];[a][r]blend=all_mode=difference:shortest=0:repeatlast=1,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/h$1.txt" -fps_mode passthrough -f null -
    paste -d' ' <(grep -oE 'pts_time:[0-9.]+' rec/h$1.txt | cut -d: -f2) <(grep -oE 'YAVG=[0-9.]+' rec/h$1.txt | cut -d= -f2) | awk -v z=$1 -v th=$6 -v r=$ref 'NR>r && $2>th{print "Zone " z ": Bild " NR-1 " t=" $1; exit}'
  done
}
# vleave METHODE NAME: Video per Link öffnen (8 s), verlassen (back|edge|down), Blatt der letzten Bilder
vleave() { local m=$1 n=$2; tofeed >/dev/null; recstart 16; adb shell "am start -a android.intent.action.VIEW -d 'https://www.youtube.com/watch?v=6YMh40vQSgU&t=$RANDOM' com.google.android.youtube" >/dev/null; sleep 8
 case "$(focus)" in com.google.android.youtube/*) ;; *) echo "nicht YouTube"; return;; esac
 case $m in back) adb shell input keyevent KEYCODE_BACK;; edge) adb shell input swipe 3 1300 700 1300 120;; down) adb shell input swipe 540 500 540 1700 180;; esac
 sleep 4; recpull $n >/dev/null; }
# sheetend NAME: Blatt der unteren Hälfte, Bilder N-70..N-14 jedes 3.
sheetend() { local n=$1; local N=$(ffprobe -v error -count_frames -select_streams v -show_entries stream=nb_read_frames -of csv=p=0 rec/$n.mp4); ffmpeg -loglevel error -y -i rec/$n.mp4 -vf "select='between(n,$((N-80)),$((N-8)))*not(mod(n,4))',crop=1080:700:0:1724,scale=270:-1,tile=6x3" -frames:v 1 rec/${n}_e.png; }
# realcount NAME: Bilder, auf denen das Home-Symbol (x90-170,y2255-2315) hell ist, aber die Beschriftung (x80-190,y2335-2362) fehlt = echte Leiste (ungefähr; Bilder prüfen!)
realcount() { local n=$1
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -filter_complex "[0:v]split=2[a][b];[a]crop=80:60:90:2255,signalstats,metadata=print:file=rec/rc1.txt[ao];[b]crop=110:27:80:2335,signalstats,metadata=print:file=rec/rc2.txt[bo]" -map "[ao]" -f null - -map "[bo]" -f null -
  paste -d' ' <(grep -oE 'pts_time:[0-9.]+' rec/rc1.txt | cut -d: -f2) <(grep -oE 'YMAX=[0-9]+' rec/rc1.txt | cut -d= -f2) <(grep -oE 'YMAX=[0-9]+' rec/rc2.txt | cut -d= -f2) <(grep -oE 'YMIN=[0-9]+' rec/rc1.txt | cut -d= -f2) | awk '{ if ($2>100 && $3<60) {c++; if(!f)f=NR-1; last=$1} } END{print "echt-verdächtig:", c+0, "Bilder, erstes Bild", f, "bis t=" last}'
}
realcount2() { local n=$1
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -filter_complex "[0:v]split=2[a][b];[a]crop=80:60:90:2255,signalstats,metadata=print:file=rec/rc1.txt[ao];[b]crop=110:27:80:2335,signalstats,metadata=print:file=rec/rc2.txt[bo]" -map "[ao]" -f null - -map "[bo]" -f null -
  paste -d' ' <(grep -oE 'pts_time:[0-9.]+' rec/rc1.txt | cut -d: -f2) <(grep -oE 'YMAX=[0-9]+' rec/rc1.txt | cut -d= -f2) <(grep -oE 'YMAX=[0-9]+' rec/rc2.txt | cut -d= -f2) | awk '{t[NR]=$1;a[NR]=$2;b[NR]=$3} END{c=0; for(i=1;i<=NR;i++){ if(a[i]>100 && b[i]<60){ ok=0; for(j=i+1;j<=NR && t[j]-t[i]<0.25;j++) if(b[j]>200) ok=1; if(ok){c++; if(!f)f=i-1; l=t[i]} } } print "echt-verdächtig:", c, "Bilder, ab Bild", f, "t=" l}'
}
