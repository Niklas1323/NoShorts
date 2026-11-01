# Ergänzung zu env.sh für das neue YouTube-Design (21.43, echte Leiste: Home, Shorts, Create, You; ohne Texte).
# shortsdet/analyze taugen dort nicht mehr (Shorts-Knopf liegt jetzt, wo unser Abos-Knopf ist). Stattdessen:
#   bardiff NAME [REFBILD] [SCHWELLE] : jedes Bild gegen ein Referenzbild der Leiste. Zuverlässig, auch über Inhalt.
#   barcheck NAME : "weg"/"ECHT" über den Text "Home". Fehlt unsere über buntem Inhalt, merkt es das NICHT (dann bardiff).
# Handy wird evtl. nebenbei benutzt, die Liste verschiebt sich: nie blind tippen, nur tap/bartap/tapif nehmen.
focus() { adb shell dumpsys window | grep -m1 mCurrentFocus | grep -oE '[a-zA-Z.]+/[^ }]+|null'; }
tap() { case "$(focus)" in com.google.android.youtube/*) adb shell input tap $1 $2;; *) echo "TIPP ABGEBROCHEN, vorne: $(focus)"; return 1;; esac; }
barok() { adb exec-out screencap -p > rec/_b.png; local r=$(ffmpeg -hide_banner -i rec/_b.png -vf "crop=70:26:100:2322,signalstats,metadata=print" -f null - 2>&1 | grep -oE 'lavfi.signalstats.Y(MIN|MAX)=[0-9]+' | cut -d= -f2 | paste -sd' '); set -- $r; [ $(( ${2:-0}-${1:-0} )) -ge 40 ]; }
# bartap X: nur wenn YouTube vorne UND unsere Leiste sichtbar (Home 135, Abos 405, You 675, Plus 945)
bartap() { barok || { echo "BARTAP ABGEBROCHEN: unsere Leiste nicht sichtbar"; return 1; }; tap $1 2290; }
# tapif REF X Y: nur wenn die Zeile um Y noch wie im Referenzbild aussieht
tapif() { adb exec-out screencap -p > rec/_c.png; local y0=$(( $3-70 )); local p=$(ffmpeg -hide_banner -i rec/_c.png -i rec/$1.png -filter_complex "[0]crop=1000:140:40:$y0[a];[1]crop=1000:140:40:$y0[b];[a][b]psnr" -f null - 2>&1 | grep -oE 'average:[0-9.a-z]+' | cut -d: -f2); if [ "$p" = inf ] || awk -v a="$p" 'BEGIN{exit !(a>30)}'; then tap $2 $3; else echo "TAPIF ABGEBROCHEN psnr=$p"; return 1; fi; }
# Protokoll mitschreiben (adb logcat -d verliert ältere Zeilen): live NAME, danach grep -a in rec/NAME.log
live() { adb logcat -c; (adb logcat -s FuckShorts > rec/$1.log 2>/dev/null &); sleep .3; }
recstart() { adb shell rm -f /sdcard/r.mp4; (adb shell screenrecord --bit-rate 16000000 --time-limit $1 /sdcard/r.mp4 >rec/sr.out 2>&1 &) ; sleep 1.2; }
recpull() { local i=0; while [ -n "$(adb shell pidof screenrecord 2>/dev/null)" ] && [ $i -lt 200 ]; do sleep 1; i=$((i+1)); done; sleep 1; adb pull /sdcard/r.mp4 rec/$1.mp4 >/dev/null 2>&1 || { sleep 2; adb pull /sdcard/r.mp4 rec/$1.mp4 >/dev/null 2>&1; }; ls -la rec/$1.mp4 | awk '{print $5, $NF}'; }
bardiff() { local n=$1 rf=${2:-100} th=${3:-1.2}
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -vf "select=eq(n\,$rf)" -frames:v 1 rec/ref_$n.png
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -i rec/ref_$n.png -filter_complex "[1:v]format=yuv420p,crop=1080:137:0:2238[r];[0:v]format=yuv420p,crop=1080:137:0:2238[a];[a][r]blend=all_mode=difference:shortest=0:repeatlast=1,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=rec/bd_$n.txt" -fps_mode passthrough -f null -
  paste -d' ' <(grep -oE 'pts_time:[0-9.]+' rec/bd_$n.txt | cut -d: -f2) <(grep -oE 'YAVG=[0-9.]+' rec/bd_$n.txt | cut -d= -f2) | awk -v th=$th 'BEGIN{i=-1} {i++; if($2>m)m=$2; if($2>th){ if(i!=last+1){ if(s!="")print "Bilder "s"-"last" (t="st" s) max="mx; s=i; st=$1; mx=0 } last=i; if($2>mx)mx=$2 } } END{ if(s!="")print "Bilder "s"-"last" (t="st" s) max="mx; print "Bilder:",i+1," groesste Abweichung:",m }' | tail -${4:-25}
}
barcheck() { local n=$1
  ffmpeg -loglevel fatal -y -i rec/$n.mp4 -fps_mode passthrough -filter_complex "[0:v]split=3[a][b][c];[a]crop=70:26:100:2322,signalstats,metadata=print:file=rec/bc1.txt[ao];[b]crop=60:100:175:2250,signalstats,metadata=print:file=rec/bc2.txt[bo];[c]crop=50:50:380:2265,signalstats,metadata=print:file=rec/bc3.txt[co]" -map "[ao]" -f null - -map "[bo]" -f null - -map "[co]" -f null -
  g() { grep -oE "$2=[0-9]+" rec/$1.txt | cut -d= -f2; }
  paste <(grep -oE 'pts_time:[0-9.]+' rec/bc1.txt | cut -d: -f2) <(g bc1 YMIN) <(g bc1 YMAX) <(g bc2 YMIN) <(g bc2 YMAX) <(g bc3 YMIN) <(g bc3 YMAX) | awk 'function fl(k){ if(on[k]){printf "%s Bilder %d-%d (t=%s, %d)\n",k,st[k],NR-2,tt[k],NR-1-st[k]; on[k]=0} }
   function op(k){ if(!on[k]){on[k]=1;st[k]=NR-1;tt[k]=$1} }
   { w=($3-$2<40); r=(w && $5-$4<30 && $7-$6>70); if(w)op("weg"); else fl("weg"); if(r)op("ECHT"); else fl("ECHT"); }
   END{ NR++; fl("weg"); fl("ECHT"); print "Bilder gesamt", NR-1 }'
}
