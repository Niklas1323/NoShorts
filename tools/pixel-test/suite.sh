# Testfolge (zuerst env.sh laden). Jeder Test nimmt auf und meldet verdächtige Bilder.
# Vorher: Handy entsperrt, Hochformat fest (settings put system accelerometer_rotation 0),
# wach halten (svc power stayon usb). Danach beides zurückstellen.

report() { analyze $1 >/dev/null; echo "== $1: echte Leiste?"; ranges $1; echo "== $1: unsere weg"; gone $1; }
# YouTube frisch starten (Chrome-Tabs von Werbung schliessen), warten bis Werbung geladen ist
fresh() { adb shell am force-stop com.android.chrome; adb shell am force-stop com.google.android.youtube; sleep 1; yt 8; }
# Nach einem Dump verbindet sich unser Dienst neu: warten (nicht tippen/wischen, das öffnet sonst Werbung)
settle() { sleep 4; }

videopos() { dump tv; tr '>' '\n' < rec/tv.xml | grep -E 'content-desc="[^"]*Aufrufe' | grep -v "Gesponsert" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | paste - - - - | awk '$2>300 && $2<1700 && $4-$2>300 {print int(($1+$3)/2), int($2+($4-$2)/3); exit}'; }
menupos() { dump tm; tr '>' '\n' < rec/tm.xml | grep -E 'content-desc="Aktionsmenü' | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | paste - - - - | awk '$4<2100 && $2>400 {print int(($1+$3)/2), int(($2+$4)/2); exit}'; }

t_scroll() {
  fresh; adb logcat -c; recstart 32
  adb shell input swipe 540 1800 540 800 300; sleep 10
  adb shell input swipe 540 1800 540 600 200; sleep 3
  adb shell input swipe 540 900 540 1300 300; sleep 3
  adb shell input swipe 540 1800 540 700 250; sleep 3
  adb shell input swipe 540 800 540 2000 150; sleep 1; adb shell input swipe 540 800 540 2000 150; sleep 3
  recpull scroll; logall | tail -20; report scroll
}

t_menu() {
  fresh; local c=$(menupos); [ -z "$c" ] && { echo "kein ⋮"; return; }; echo "⋮ bei $c"; settle
  adb logcat -c; recstart 14
  tap $c; sleep 2.5; adb shell input keyevent KEYCODE_BACK; sleep 2
  tap $c; sleep 2.5; adb shell input keyevent KEYCODE_BACK; sleep 2
  recpull menu; logall | tail -20; report menu
}

# Plus → Kamera, schliessen mit X, Zurück-Geste, Zurück-Taste, X
t_cam() {
  fresh; adb logcat -c; recstart 34
  tap 945 2290; sleep 3.5; tap 104 331; sleep 3
  tap 945 2290; sleep 3.5; adb shell input swipe 2 1300 500 1300 150; sleep 3
  tap 945 2290; sleep 3.5; adb shell input keyevent KEYCODE_BACK; sleep 3
  tap 945 2290; sleep 3.5; tap 104 331; sleep 3
  recpull cam; logall | tail -30; report cam
}

t_video() {
  fresh; local c=$(videopos); [ -z "$c" ] && { echo "kein Video gefunden"; return; }; echo "Video bei $c"; settle
  adb logcat -c; recstart 22
  tap $c; sleep 4; adb shell input keyevent KEYCODE_BACK; sleep 3.5
  tap $c; sleep 4; adb shell input swipe 540 500 540 1800 250; sleep 3.5
  recpull video; logall | tail -20; report video
}

# Abos → Mein YouTube → Startseite, 5 Runden (sollte "15" ausgewählt melden)
t_tabs() {
  fresh; adb logcat -c; recstart 50
  for i in 1 2 3 4 5; do tap 405 2290; sleep 2.5; tap 675 2290; sleep 2.5; tap 135 2290; sleep 2.5; done
  recpull tabs; adb logcat -d -s FuckShorts | grep -c "ausgewählt"; adb logcat -d -s FuckShorts | grep "Tipp:" | grep -v "Klick ok\|ausgewählt"; report tabs
}

# Abos und Mein YouTube scrollen, dann Suche
t_pages() {
  fresh; adb logcat -c; recstart 44
  tap 405 2290; sleep 2.5; adb shell input swipe 540 1800 540 800 300; sleep 3; adb shell input swipe 540 900 540 1700 300; sleep 2.5
  tap 675 2290; sleep 2.5; adb shell input swipe 540 1800 540 800 300; sleep 3; adb shell input swipe 540 900 540 1700 300; sleep 2.5
  tap 135 2290; sleep 2.5
  tap 1010 240; sleep 2.5; adb shell input text "katzen"; sleep 1; adb shell input keyevent KEYCODE_ENTER; sleep 4
  adb shell input keyevent KEYCODE_BACK; sleep 2.5
  recpull pages; logall | tail -20; report pages
}
