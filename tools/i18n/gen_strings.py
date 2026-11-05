#!/usr/bin/env python3
"""Erzeugt app/src/main/res/values*/strings.xml aus den Übersetzungen unten.

Texte hier ändern, dann: python3 tools/i18n/gen_strings.py
Sprachen: en (Standard), de, fr, it, es, pt (Brasilien). de-CH wird aus de erzeugt (ss statt ß, «» statt „“).
"""
import os
import re

LANGS = ["en", "de", "fr", "it", "es", "pt"]
ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res")

S = {}  # key -> {lang: text}
P = {}  # plurals: key -> {lang: (one, other)}
A = {}  # arrays: key -> {lang: [items]}


def s(key, en, de, fr, it, es, pt):
    S[key] = dict(en=en, de=de, fr=fr, it=it, es=es, pt=pt)


def p(key, **langs):
    P[key] = langs


# ---------------------------------------------------------------- System / Dienst
s("a11y_description",
  "F*ck Shorts removes Shorts from the YouTube app. It reads the layout of the YouTube app (buttons, text, positions), notices which app is in front, takes screenshots for colors and your profile picture, places its own bar without Shorts over YouTube's bar, presses Back when a Short opens and hides Shorts shelves. Everything it reads from YouTube stays on your phone. Internet is only used for subscriptions (Google Play, Superwall). It doesn't read passwords, messages or other apps.",
  "F*ck Shorts entfernt Shorts aus der YouTube-App. Die App liest den Aufbau der YouTube-App (Knöpfe, Text, Positionen), merkt, welche App vorne ist, macht Bildschirmfotos für Farben und dein Profilbild, legt eine eigene Leiste ohne Shorts über die Leiste von YouTube, drückt Zurück, wenn ein Short aufgeht, und blendet Shorts-Regale aus. Alles, was sie von YouTube liest, bleibt auf deinem Handy. Internet wird nur für Abos genutzt (Google Play, Superwall). Sie liest keine Passwörter, Nachrichten oder andere Apps.",
  "F*ck Shorts retire les Shorts de l'application YouTube. Elle lit la structure de l'application YouTube (boutons, texte, positions), détecte quelle application est au premier plan, fait des captures d'écran pour les couleurs et ta photo de profil, place sa propre barre sans Shorts par-dessus celle de YouTube, appuie sur Retour quand un Short s'ouvre et masque les sections Shorts. Tout ce qu'elle lit dans YouTube reste sur ton téléphone. Internet sert uniquement aux abonnements (Google Play, Superwall). Elle ne lit ni mots de passe, ni messages, ni autres applications.",
  "F*ck Shorts rimuove gli Shorts dall'app YouTube. Legge la struttura dell'app YouTube (pulsanti, testo, posizioni), rileva quale app è in primo piano, acquisisce screenshot per i colori e la tua immagine del profilo, mette la propria barra senza Shorts sopra quella di YouTube, preme Indietro quando si apre uno Short e nasconde le sezioni Shorts. Tutto ciò che legge da YouTube resta sul tuo telefono. Internet serve solo per gli abbonamenti (Google Play, Superwall). Non legge password, messaggi né altre app.",
  "F*ck Shorts elimina los Shorts de la app de YouTube. Lee la estructura de la app de YouTube (botones, texto, posiciones), detecta qué app está en primer plano, hace capturas de pantalla para los colores y tu foto de perfil, coloca su propia barra sin Shorts sobre la barra de YouTube, pulsa Atrás cuando se abre un Short y oculta las secciones de Shorts. Todo lo que lee de YouTube se queda en tu teléfono. Internet solo se usa para las suscripciones (Google Play, Superwall). No lee contraseñas, mensajes ni otras apps.",
  "O F*ck Shorts remove os Shorts do app do YouTube. Ele lê a estrutura do app do YouTube (botões, texto, posições), percebe qual app está em primeiro plano, faz capturas de tela para as cores e sua foto de perfil, coloca a própria barra sem Shorts sobre a barra do YouTube, toca em Voltar quando um Short abre e oculta as seções de Shorts. Tudo o que ele lê do YouTube fica no seu celular. A internet só é usada para assinaturas (Google Play, Superwall). Ele não lê senhas, mensagens nem outros apps.")
s("shorts_hidden_toast", "Shorts are hidden", "Shorts sind ausgeblendet", "Shorts masqués", "Shorts nascosti",
  "Shorts ocultos", "Shorts ocultos")

# ---------------------------------------------------------------- Sprache
s("language", "Language", "Sprache", "Langue", "Lingua", "Idioma", "Idioma")
s("section_language", "LANGUAGE", "SPRACHE", "LANGUE", "LINGUA", "IDIOMA", "IDIOMA")

# ---------------------------------------------------------------- Onboarding
s("onb_lets_go", "Let's go", "Los geht's", "C'est parti", "Iniziamo", "Vamos", "Vamos lá")
s("onb_tagline", "YouTube without the Shorts.", "YouTube ohne Shorts.", "YouTube sans les Shorts.",
  "YouTube senza gli Shorts.", "YouTube sin los Shorts.", "YouTube sem os Shorts.")
s("onb_next", "Next", "Weiter", "Suivant", "Avanti", "Siguiente", "Próximo")
s("onb_shorts_gone", "Shorts gone.", "Shorts weg.", "Fini les Shorts.", "Shorts spariti.", "Adiós, Shorts.",
  "Shorts fora.")
s("onb_long_stay", "Long videos stay.", "Lange Videos bleiben.", "Les vidéos longues restent.",
  "I video lunghi restano.", "Los vídeos largos se quedan.", "Os vídeos longos ficam.")
s("onb_before", "BEFORE", "VORHER", "AVANT", "PRIMA", "ANTES", "ANTES")
s("onb_after", "AFTER", "NACHHER", "APRÈS", "DOPO", "DESPUÉS", "DEPOIS")
s("preview_home", "Home", "Startseite", "Accueil", "Home", "Inicio", "Início")
s("preview_subs", "Subs", "Abos", "Abos", "Iscrizioni", "Suscrip.", "Inscrições")
s("preview_you", "You", "Du", "Vous", "Tu", "Tú", "Você")
s("turnon_agree", "Agree & open settings", "Zustimmen & einschalten", "Accepter et activer", "Accetta e attiva",
  "Aceptar y activar", "Concordar e ativar")
s("turnon_continue", "Continue", "Weiter", "Continuer", "Continua", "Continuar", "Continuar")
s("turnon_open_settings", "Open settings", "Einstellungen öffnen", "Ouvrir les réglages", "Apri le impostazioni",
  "Abrir ajustes", "Abrir configurações")
s("have_code", "Have a code?", "Hast du einen Code?", "Tu as un code ?", "Hai un codice?", "¿Tienes un código?",
  "Tem um código?")
s("turnon_declined", "Without this permission F*ck Shorts can't do anything.",
  "Ohne diese Berechtigung kann F*ck Shorts nichts tun.",
  "Sans cette autorisation, F*ck Shorts ne peut rien faire.",
  "Senza questa autorizzazione F*ck Shorts non può fare nulla.",
  "Sin este permiso, F*ck Shorts no puede hacer nada.",
  "Sem essa permissão, o F*ck Shorts não consegue fazer nada.")
s("not_now", "Not now", "Jetzt nicht", "Pas maintenant", "Non ora", "Ahora no", "Agora não")
s("turnon_title", "Turn it on", "Einschalten", "Active-le", "Attivalo", "Actívalo", "Ative")
s("turnon_sub", "3 taps in Android settings.", "3 Tipps in den Android-Einstellungen.",
  "3 touches dans les réglages Android.", "3 tocchi nelle impostazioni di Android.",
  "3 toques en los ajustes de Android.", "3 toques nas configurações do Android.")
s("step_open", "Open %1$s", "Öffne %1$s", "Ouvre %1$s", "Apri %1$s", "Abre %1$s", "Abra %1$s")
s("accessibility", "Accessibility", "Bedienungshilfen", "Accessibilité", "Accessibilità", "Accesibilidad",
  "Acessibilidade")
s("step_tap", "Tap %1$s", "Tippe auf %1$s", "Touche %1$s", "Tocca %1$s", "Toca %1$s", "Toque em %1$s")
s("step_switch", "Switch it on", "Schalte es ein", "Active-le", "Attivalo", "Actívalo", "Ative")
s("switch_greyed", "Switch greyed out?", "Schalter ausgegraut?", "Interrupteur grisé ?", "Interruttore grigio?",
  "¿Interruptor en gris?", "Botão está cinza?")
s("locked_title", "Switch locked?", "Schalter gesperrt?", "Interrupteur bloqué ?", "Interruttore bloccato?",
  "¿Interruptor bloqueado?", "Botão bloqueado?")
s("locked_sub", "Happens when the app isn't from the Play Store.",
  "Passiert, wenn die App nicht aus dem Play Store ist.",
  "Ça arrive quand l'appli ne vient pas du Play Store.",
  "Succede quando l'app non viene dal Play Store.",
  "Pasa cuando la app no es de Play Store.",
  "Acontece quando o app não é da Play Store.")
s("app_info", "App info", "App-Info", "Infos sur l'appli", "Info app", "Información de la app",
  "Informações do app")
s("locked_step2", "Tap ⋮ top right", "Tippe oben rechts auf ⋮", "Touche ⋮ en haut à droite",
  "Tocca ⋮ in alto a destra", "Toca ⋮ arriba a la derecha", "Toque em ⋮ no canto superior direito")
s("allow_restricted", "Allow restricted settings", "Eingeschränkte Einstellungen zulassen",
  "Autoriser les paramètres restreints", "Consenti impostazioni con limitazioni", "Permitir ajustes restringidos",
  "Permitir configurações restritas")
s("close", "Close", "Schließen", "Fermer", "Chiudi", "Cerrar", "Fechar")

# ---------------------------------------------------------------- Hinweis (Play-Richtlinie, genau übersetzen!)
s("disc_title", "Before you agree", "Bevor du zustimmst", "Avant d'accepter", "Prima di accettare",
  "Antes de aceptar", "Antes de concordar")
s("disc_summary",
  "F*ck Shorts needs Android's Accessibility permission. It is not an assistive tool for people with disabilities. It uses this permission only to remove Shorts from the YouTube app: it reads YouTube's screen, acts in YouTube for you, and keeps that data on your phone.",
  "F*ck Shorts braucht die Android-Berechtigung für Bedienungshilfen. Die App ist kein Hilfsmittel für Menschen mit Behinderung. Sie nutzt diese Berechtigung nur, um Shorts aus der YouTube-App zu entfernen: Sie liest den Bildschirm von YouTube, handelt in YouTube für dich und behält diese Daten auf deinem Handy.",
  "F*ck Shorts a besoin de l'autorisation Accessibilité d'Android. Ce n'est pas un outil d'assistance pour les personnes en situation de handicap. Elle utilise cette autorisation uniquement pour retirer les Shorts de l'application YouTube : elle lit l'écran de YouTube, agit dans YouTube à ta place et garde ces données sur ton téléphone.",
  "F*ck Shorts ha bisogno dell'autorizzazione Accessibilità di Android. Non è uno strumento di assistenza per persone con disabilità. Usa questa autorizzazione solo per rimuovere gli Shorts dall'app YouTube: legge lo schermo di YouTube, agisce in YouTube al posto tuo e tiene questi dati sul tuo telefono.",
  "F*ck Shorts necesita el permiso de Accesibilidad de Android. No es una herramienta de asistencia para personas con discapacidad. Usa este permiso solo para quitar los Shorts de la app de YouTube: lee la pantalla de YouTube, actúa en YouTube por ti y guarda esos datos en tu teléfono.",
  "O F*ck Shorts precisa da permissão de Acessibilidade do Android. Ele não é uma ferramenta de assistência para pessoas com deficiência. Ele usa essa permissão só para remover os Shorts do app do YouTube: lê a tela do YouTube, age no YouTube por você e mantém esses dados no seu celular.")
s("show_details", "Show details", "Details anzeigen", "Afficher les détails", "Mostra dettagli", "Mostrar detalles",
  "Mostrar detalhes")
s("hide_details", "Hide details", "Details ausblenden", "Masquer les détails", "Nascondi dettagli",
  "Ocultar detalles", "Ocultar detalhes")
s("disc_reads", "What it reads", "Was die App liest", "Ce qu'elle lit", "Cosa legge", "Qué lee", "O que ele lê")
s("disc_r1",
  "The layout of the YouTube app: buttons, text (e.g. video titles) and where they are on screen.",
  "Den Aufbau der YouTube-App: Knöpfe, Text (z. B. Videotitel) und wo sie auf dem Bildschirm sind.",
  "La structure de l'application YouTube : boutons, texte (p. ex. titres des vidéos) et leur position à l'écran.",
  "La struttura dell'app YouTube: pulsanti, testo (ad es. titoli dei video) e la loro posizione sullo schermo.",
  "La estructura de la app de YouTube: botones, texto (p. ej., títulos de vídeos) y dónde están en la pantalla.",
  "A estrutura do app do YouTube: botões, texto (por exemplo, títulos de vídeos) e onde eles ficam na tela.")
s("disc_r2",
  "Which app is in front. For notifications and the keyboard only where they are, not what's in them.",
  "Welche App vorne ist. Bei Benachrichtigungen und der Tastatur nur, wo sie sind, nicht was darin steht.",
  "Quelle application est au premier plan. Pour les notifications et le clavier, seulement leur position, pas leur contenu.",
  "Quale app è in primo piano. Per le notifiche e la tastiera solo dove si trovano, non il loro contenuto.",
  "Qué app está en primer plano. De las notificaciones y el teclado solo dónde están, no lo que contienen.",
  "Qual app está em primeiro plano. Das notificações e do teclado, só onde estão, não o que há neles.")
s("disc_r3", "That you touch the screen, not where.", "Dass du den Bildschirm berührst, nicht wo.",
  "Que tu touches l'écran, pas où.", "Che tocchi lo schermo, non dove.", "Que tocas la pantalla, no dónde.",
  "Que você toca na tela, não onde.")
s("disc_r4",
  "Screenshots, to copy colors and your profile picture into its own bar. Only those are kept; the rest of the picture is thrown away right away.",
  "Bildschirmfotos, um Farben und dein Profilbild in die eigene Leiste zu übernehmen. Nur das wird behalten; der Rest des Bildes wird sofort verworfen.",
  "Des captures d'écran, pour reprendre les couleurs et ta photo de profil dans sa propre barre. Seuls ces éléments sont gardés ; le reste de l'image est supprimé aussitôt.",
  "Screenshot, per copiare i colori e la tua immagine del profilo nella propria barra. Solo questi vengono conservati; il resto dell'immagine viene scartato subito.",
  "Capturas de pantalla, para copiar los colores y tu foto de perfil en su propia barra. Solo se guarda eso; el resto de la imagen se descarta al instante.",
  "Capturas de tela, para copiar as cores e sua foto de perfil para a própria barra. Só isso é guardado; o resto da imagem é descartado na hora.")
s("disc_does", "What it does in YouTube", "Was die App in YouTube tut", "Ce qu'elle fait dans YouTube",
  "Cosa fa in YouTube", "Qué hace en YouTube", "O que ele faz no YouTube")
s("disc_d1",
  "Places its own bar without the Shorts button over YouTube's bar. Tapping it taps the matching YouTube button.",
  "Legt eine eigene Leiste ohne Shorts-Knopf über die Leiste von YouTube. Ein Tipp darauf tippt den passenden YouTube-Knopf.",
  "Place sa propre barre sans le bouton Shorts par-dessus celle de YouTube. Un appui dessus appuie sur le bouton YouTube correspondant.",
  "Mette la propria barra senza il pulsante Shorts sopra quella di YouTube. Toccarla tocca il pulsante di YouTube corrispondente.",
  "Coloca su propia barra sin el botón de Shorts sobre la barra de YouTube. Al tocarla, toca el botón de YouTube correspondiente.",
  "Coloca a própria barra sem o botão Shorts sobre a barra do YouTube. Tocar nela toca no botão correspondente do YouTube.")
s("disc_d2",
  "Presses Back as soon as a Short opens. A Short opened from a link may play; swipe on and the app swipes back.",
  "Drückt Zurück, sobald ein Short aufgeht. Ein Short, den du über einen Link öffnest, darf laufen; wischst du weiter, wischt die App zurück.",
  "Appuie sur Retour dès qu'un Short s'ouvre. Un Short ouvert depuis un lien peut être lu ; si tu passes au suivant, l'appli revient en arrière.",
  "Preme Indietro appena si apre uno Short. Uno Short aperto da un link può essere riprodotto; se scorri al successivo, l'app torna indietro.",
  "Pulsa Atrás en cuanto se abre un Short. Un Short abierto desde un enlace se puede ver; si deslizas al siguiente, la app vuelve atrás.",
  "Toca em Voltar assim que um Short abre. Um Short aberto por um link pode tocar; se você deslizar para o próximo, o app volta.")
s("disc_d3",
  "Hides Shorts shelves automatically: taps ⋮ → \"Not interested\" or \"Hide\" on them. This changes your YouTube recommendations.",
  "Blendet Shorts-Regale automatisch aus: tippt dort auf ⋮ → „Kein Interesse“ oder „Ausblenden“. Das verändert deine YouTube-Empfehlungen.",
  "Masque automatiquement les sections Shorts : appuie sur ⋮ → « Pas intéressé » ou « Masquer ». Cela modifie tes recommandations YouTube.",
  "Nasconde automaticamente le sezioni Shorts: tocca ⋮ → «Non mi interessa» o «Nascondi». Questo cambia i tuoi consigli su YouTube.",
  "Oculta automáticamente las secciones de Shorts: toca ⋮ → «No me interesa» u «Ocultar». Esto cambia tus recomendaciones de YouTube.",
  "Oculta automaticamente as seções de Shorts: toca em ⋮ → \"Não tenho interesse\" ou \"Ocultar\". Isso muda suas recomendações do YouTube.")
s("disc_data", "Your data", "Deine Daten", "Tes données", "I tuoi dati", "Tus datos", "Seus dados")
s("disc_p1",
  "Everything it reads from YouTube stays on your phone and is never sent anywhere.",
  "Alles, was die App von YouTube liest, bleibt auf deinem Handy und wird nie irgendwohin gesendet.",
  "Tout ce qu'elle lit dans YouTube reste sur ton téléphone et n'est jamais envoyé nulle part.",
  "Tutto ciò che legge da YouTube resta sul tuo telefono e non viene mai inviato da nessuna parte.",
  "Todo lo que lee de YouTube se queda en tu teléfono y nunca se envía a ningún sitio.",
  "Tudo o que ele lê do YouTube fica no seu celular e nunca é enviado para lugar nenhum.")
s("disc_p2",
  "Stored on your phone only: a crop of your profile picture, the position of YouTube's bar, your settings and counters.",
  "Nur auf deinem Handy gespeichert: ein Ausschnitt deines Profilbilds, die Lage der YouTube-Leiste, deine Einstellungen und Zähler.",
  "Enregistrés uniquement sur ton téléphone : un extrait de ta photo de profil, la position de la barre YouTube, tes réglages et tes compteurs.",
  "Salvati solo sul tuo telefono: un ritaglio della tua immagine del profilo, la posizione della barra di YouTube, le tue impostazioni e i contatori.",
  "Guardado solo en tu teléfono: un recorte de tu foto de perfil, la posición de la barra de YouTube, tus ajustes y contadores.",
  "Salvo só no seu celular: um recorte da sua foto de perfil, a posição da barra do YouTube, suas configurações e contadores.")
s("disc_p2_debug",
  "Test version only: up to 12 small troubleshooting files about YouTube's screen layout.",
  "Nur in Test-Versionen: bis zu 12 kleine Fehler-Dateien über den Bildschirmaufbau von YouTube.",
  "Version de test uniquement : jusqu'à 12 petits fichiers de diagnostic sur la structure de l'écran de YouTube.",
  "Solo nella versione di prova: fino a 12 piccoli file di diagnostica sulla struttura dello schermo di YouTube.",
  "Solo en la versión de prueba: hasta 12 pequeños archivos de diagnóstico sobre la estructura de la pantalla de YouTube.",
  "Só na versão de teste: até 12 pequenos arquivos de diagnóstico sobre a estrutura da tela do YouTube.")
s("disc_p3",
  "Subscriptions are handled by Google Play and our payment provider Superwall. They receive purchase and device info (e.g. device model, language, app version, IP address, an app-specific device ID), never anything from YouTube.",
  "Abos laufen über Google Play und unseren Zahlungsdienst Superwall. Sie erhalten Kauf- und Geräteinfos (z. B. Gerätemodell, Sprache, App-Version, IP-Adresse, eine App-eigene Geräte-ID), nie etwas von YouTube.",
  "Les abonnements sont gérés par Google Play et notre prestataire de paiement Superwall. Ils reçoivent des infos d'achat et d'appareil (p. ex. modèle de l'appareil, langue, version de l'appli, adresse IP, un identifiant d'appareil propre à l'appli), jamais rien de YouTube.",
  "Gli abbonamenti sono gestiti da Google Play e dal nostro fornitore di pagamenti Superwall. Ricevono informazioni sull'acquisto e sul dispositivo (ad es. modello del dispositivo, lingua, versione dell'app, indirizzo IP, un ID dispositivo specifico dell'app), mai nulla da YouTube.",
  "Las suscripciones las gestionan Google Play y nuestro proveedor de pagos Superwall. Reciben datos de compra y del dispositivo (p. ej., modelo del dispositivo, idioma, versión de la app, dirección IP, un ID de dispositivo propio de la app), nunca nada de YouTube.",
  "As assinaturas são gerenciadas pelo Google Play e pelo nosso provedor de pagamentos Superwall. Eles recebem informações de compra e do dispositivo (por exemplo, modelo do aparelho, idioma, versão do app, endereço IP, um ID de dispositivo específico do app), nunca nada do YouTube.")
s("disc_p4",
  "It doesn't read passwords or messages, and it doesn't look into other apps.",
  "Die App liest keine Passwörter oder Nachrichten und schaut nicht in andere Apps.",
  "Elle ne lit ni mots de passe ni messages, et ne regarde pas dans les autres applis.",
  "Non legge password né messaggi e non guarda dentro altre app.",
  "No lee contraseñas ni mensajes, y no mira dentro de otras apps.",
  "Ele não lê senhas nem mensagens e não olha dentro de outros apps.")
s("disc_p5",
  "You can stop it anytime: turn it off in Android Settings → Accessibility, or uninstall the app.",
  "Du kannst sie jederzeit stoppen: in den Android-Einstellungen → Bedienungshilfen ausschalten oder die App deinstallieren.",
  "Tu peux l'arrêter à tout moment : désactive-la dans Réglages Android → Accessibilité, ou désinstalle l'appli.",
  "Puoi fermarla in qualsiasi momento: disattivala in Impostazioni di Android → Accessibilità, oppure disinstalla l'app.",
  "Puedes detenerla cuando quieras: desactívala en Ajustes de Android → Accesibilidad o desinstala la app.",
  "Você pode pará-lo a qualquer momento: desative em Configurações do Android → Acessibilidade ou desinstale o app.")
s("disc_policy", "Read the full privacy policy", "Ganze Datenschutzerklärung lesen (Englisch)",
  "Lire la politique de confidentialité complète (en anglais)",
  "Leggi l'informativa completa sulla privacy (in inglese)",
  "Leer la política de privacidad completa (en inglés)",
  "Ler a política de privacidade completa (em inglês)")

# ---------------------------------------------------------------- Testzeit-Seite (zurzeit aus) und Abo beendet
s("start_trial", "Start 3 days free", "3 Tage gratis starten", "Commencer 3 jours gratuits", "Inizia 3 giorni gratis",
  "Empezar 3 días gratis", "Começar 3 dias grátis")
s("restore_purchases", "Restore purchases", "Käufe wiederherstellen", "Restaurer les achats", "Ripristina acquisti",
  "Restaurar compras", "Restaurar compras")
s("trial_last_step", "Last step:", "Letzter Schritt:", "Dernière étape :", "Ultimo passo:", "Último paso:",
  "Último passo:")
s("trial_start_free", "start your free trial.", "starte deine Gratis-Testzeit.", "commence ton essai gratuit.",
  "inizia la prova gratuita.", "empieza tu prueba gratis.", "comece seu teste grátis.")
s("trial_today", "Today", "Heute", "Aujourd'hui", "Oggi", "Hoy", "Hoje")
s("trial_today_text", "Shorts are gone. Everything is unlocked.", "Shorts sind weg. Alles ist freigeschaltet.",
  "Les Shorts disparaissent. Tout est débloqué.", "Gli Shorts spariscono. Tutto è sbloccato.",
  "Los Shorts desaparecen. Todo está desbloqueado.", "Os Shorts somem. Tudo é liberado.")
s("trial_day2", "Day 2", "Tag 2", "Jour 2", "Giorno 2", "Día 2", "Dia 2")
s("trial_day2_text", "You get your time back.", "Du bekommst deine Zeit zurück.", "Tu récupères ton temps.",
  "Ti riprendi il tuo tempo.", "Recuperas tu tiempo.", "Você recupera seu tempo.")
s("trial_day3", "Day 3", "Tag 3", "Jour 3", "Giorno 3", "Día 3", "Dia 3")
s("trial_day3_text", "Your subscription starts. Cancel before in Google Play and you pay nothing.",
  "Dein Abo beginnt. Kündigst du vorher in Google Play, zahlst du nichts.",
  "Ton abonnement commence. Annule avant dans Google Play et tu ne paies rien.",
  "Inizia l'abbonamento. Annulla prima su Google Play e non paghi nulla.",
  "Empieza tu suscripción. Cancela antes en Google Play y no pagas nada.",
  "Sua assinatura começa. Cancele antes no Google Play e você não paga nada.")
s("expired_1", "Your subscription", "Dein Abo", "Ton abonnement", "Il tuo abbonamento", "Tu suscripción",
  "Sua assinatura")
s("expired_2", "has ended.", "ist abgelaufen.", "est terminé.", "è scaduto.", "ha terminado.", "terminou.")
s("expired_text",
  "Shorts are back in YouTube. Resubscribe to block them again. Your settings and stats are still here.",
  "Shorts sind zurück in YouTube. Schließ ein neues Abo ab, um sie wieder zu blockieren. Deine Einstellungen und Zahlen sind noch da.",
  "Les Shorts sont de retour dans YouTube. Réabonne-toi pour les bloquer à nouveau. Tes réglages et tes statistiques sont toujours là.",
  "Gli Shorts sono tornati su YouTube. Abbonati di nuovo per bloccarli. Le tue impostazioni e statistiche sono ancora qui.",
  "Los Shorts han vuelto a YouTube. Vuelve a suscribirte para bloquearlos otra vez. Tus ajustes y estadísticas siguen aquí.",
  "Os Shorts voltaram ao YouTube. Assine de novo para bloqueá-los outra vez. Suas configurações e estatísticas continuam aqui.")
s("resubscribe", "Resubscribe", "Wieder abonnieren", "Se réabonner", "Abbonati di nuovo", "Volver a suscribirse",
  "Assinar de novo")

# ---------------------------------------------------------------- Startseite
s("nav_status", "Status", "Status", "Statut", "Stato", "Estado", "Status")
s("nav_settings", "Settings", "Einstellungen", "Réglages", "Impostazioni", "Ajustes", "Configurações")
s("nav_pro", "Pro", "Pro", "Pro", "Pro", "Pro", "Pro")
s("protection", "Protection", "Schutz", "Protection", "Protezione", "Protección", "Proteção")
s("protection_on", "on", "an", "activée", "attiva", "activada", "ativada")
s("protection_off", "off", "aus", "désactivée", "disattivata", "desactivada", "desativada")
s("protection_paused", "paused", "pausiert", "en pause", "in pausa", "en pausa", "pausada")
s("status_ready", "You can turn protection off now. Tap the shield.",
  "Du kannst den Schutz jetzt ausschalten. Tippe auf das Schild.",
  "Tu peux désactiver la protection maintenant. Touche le bouclier.",
  "Ora puoi disattivare la protezione. Tocca lo scudo.",
  "Ya puedes desactivar la protección. Toca el escudo.",
  "Agora você pode desativar a proteção. Toque no escudo.")
s("status_enable", "Turn on F*ck Shorts in Accessibility.", "Schalte F*ck Shorts in den Bedienungshilfen ein.",
  "Active F*ck Shorts dans Accessibilité.", "Attiva F*ck Shorts in Accessibilità.",
  "Activa F*ck Shorts en Accesibilidad.", "Ative o F*ck Shorts em Acessibilidade.")
s("status_paused", "Shorts are allowed. Tap the shield to turn protection back on.",
  "Shorts sind erlaubt. Tippe auf das Schild, um den Schutz wieder einzuschalten.",
  "Les Shorts sont autorisés. Touche le bouclier pour réactiver la protection.",
  "Gli Shorts sono consentiti. Tocca lo scudo per riattivare la protezione.",
  "Los Shorts están permitidos. Toca el escudo para volver a activar la protección.",
  "Os Shorts estão liberados. Toque no escudo para reativar a proteção.")
s("status_wait", "You can turn it off in %1$s.", "Ausschalten geht in %1$s.", "Tu pourras la désactiver dans %1$s.",
  "Potrai disattivarla tra %1$s.", "Podrás desactivarla en %1$s.", "Você poderá desativar em %1$s.")
s("status_free", "YouTube is Shorts-free.", "YouTube ist Shorts-frei.", "YouTube est sans Shorts.",
  "YouTube è senza Shorts.", "YouTube está libre de Shorts.", "O YouTube está sem Shorts.")
s("keep_on", "Keep protection on", "Schutz anlassen", "Garder la protection", "Mantieni la protezione",
  "Mantener la protección", "Manter a proteção")
s("shorts_closed", "Shorts closed for you", "Shorts für dich geschlossen", "Shorts fermés pour toi",
  "Shorts chiusi per te", "Shorts cerrados por ti", "Shorts fechados para você")
s("time_saved", "Time saved (estimate)", "Gewonnene Zeit (geschätzt)", "Temps gagné (estimation)",
  "Tempo risparmiato (stima)", "Tiempo ahorrado (estimado)", "Tempo economizado (estimativa)")
s("open_youtube", "Open YouTube", "YouTube öffnen", "Ouvrir YouTube", "Apri YouTube", "Abrir YouTube",
  "Abrir o YouTube")
s("turn_on_protection", "Turn on protection", "Schutz einschalten", "Activer la protection", "Attiva la protezione",
  "Activar la protección", "Ativar a proteção")
s("dur_seconds", "%1$d sec", "%1$d Sek.", "%1$d s", "%1$d s", "%1$d s", "%1$d s")
s("dur_minutes", "%1$d min", "%1$d Min.", "%1$d min", "%1$d min", "%1$d min", "%1$d min")
s("dur_hours", "%1$d h", "%1$d Std.", "%1$d h", "%1$d h", "%1$d h", "%1$d h")
s("dur_hours_minutes", "%1$d h %2$d min", "%1$d Std. %2$d Min.", "%1$d h %2$d min", "%1$d h %2$d min",
  "%1$d h %2$d min", "%1$d h %2$d min")
s("approx", "≈ %1$s", "≈ %1$s", "≈ %1$s", "≈ %1$s", "≈ %1$s", "≈ %1$s")
s("not_yet", "Not yet", "Noch nicht", "Pas encore", "Non ancora", "Todavía no", "Ainda não")
s("waiting_text",
  "You can turn protection off in %1$s. The timer keeps running even when the app is closed.",
  "Du kannst den Schutz in %1$s ausschalten. Der Timer läuft weiter, auch wenn die App zu ist.",
  "Tu pourras désactiver la protection dans %1$s. Le minuteur continue même quand l'appli est fermée.",
  "Potrai disattivare la protezione tra %1$s. Il timer continua anche quando l'app è chiusa.",
  "Podrás desactivar la protección en %1$s. El temporizador sigue aunque la app esté cerrada.",
  "Você poderá desativar a proteção em %1$s. O timer continua mesmo com o app fechado.")
s("ok", "OK", "OK", "OK", "OK", "OK", "OK")
s("last_chance", "Last chance.", "Letzte Chance.", "Dernière chance.", "Ultima possibilità.", "Última oportunidad.",
  "Última chance.")
s("confirm_note", "Shorts will play again. Your bar without the Shorts button stays.",
  "Shorts laufen dann wieder. Deine Leiste ohne Shorts-Knopf bleibt.",
  "Les Shorts seront de nouveau lus. Ta barre sans bouton Shorts reste.",
  "Gli Shorts torneranno a essere riprodotti. La tua barra senza il pulsante Shorts resta.",
  "Los Shorts volverán a reproducirse. Tu barra sin el botón de Shorts se queda.",
  "Os Shorts voltarão a tocar. Sua barra sem o botão Shorts continua.")
s("turn_off_protection", "Turn off protection", "Schutz ausschalten", "Désactiver la protection",
  "Disattiva la protezione", "Desactivar la protección", "Desativar a proteção")
s("wait_kicker", "Wait.", "Warte.", "Attends.", "Aspetta.", "Espera.", "Espere.")
s("quote_note",
  "Still want to turn it off? Then you have to sit through %1$s first. The urge will be gone before the timer is.",
  "Willst du immer noch ausschalten? Dann musst du zuerst %1$s abwarten. Der Drang ist weg, bevor der Timer abgelaufen ist.",
  "Tu veux toujours la désactiver ? Alors tu dois d'abord attendre %1$s. L'envie sera passée avant la fin du minuteur.",
  "Vuoi ancora disattivarla? Allora prima devi aspettare %1$s. La voglia passerà prima che finisca il timer.",
  "¿Todavía quieres desactivarla? Entonces primero tienes que esperar %1$s. Las ganas se te pasarán antes que el temporizador.",
  "Ainda quer desativar? Então você vai ter que esperar %1$s primeiro. A vontade passa antes de o timer acabar.")
s("start_timer", "Start %1$s timer", "Timer über %1$s starten", "Lancer le minuteur de %1$s",
  "Avvia il timer di %1$s", "Iniciar temporizador de %1$s", "Iniciar timer de %1$s")

A["shorts_quotes"] = dict(
    en=["The feed never ends. Your day does.",
        "One Short becomes ten. Ten become an hour you'll never get back.",
        "You won't remember a single one of them tomorrow.",
        "You're not bored. You're being farmed.",
        "This is exactly the moment you installed this app for.",
        "Future you is begging you not to do this.",
        "\"Just five minutes\" is how they get you. Every single time.",
        "Your attention is the product. Stop giving it away for free.",
        "Close this. Go do the thing you're avoiding.",
        "Nobody on their deathbed wished they'd watched more Shorts."],
    de=["Der Feed endet nie. Dein Tag schon.",
        "Aus einem Short werden zehn. Aus zehn eine Stunde, die du nie zurückbekommst.",
        "Morgen erinnerst du dich an keinen einzigen davon.",
        "Dir ist nicht langweilig. Du wirst gemolken.",
        "Genau für diesen Moment hast du diese App installiert.",
        "Dein zukünftiges Ich fleht dich an, es nicht zu tun.",
        "„Nur fünf Minuten“ – so kriegen sie dich. Jedes einzelne Mal.",
        "Deine Aufmerksamkeit ist das Produkt. Hör auf, sie zu verschenken.",
        "Schließ das hier. Mach das, was du gerade vor dir herschiebst.",
        "Niemand hat sich auf dem Sterbebett gewünscht, mehr Shorts geschaut zu haben."],
    fr=["Le fil ne s'arrête jamais. Ta journée, si.",
        "Un Short en devient dix. Dix deviennent une heure que tu ne récupéreras jamais.",
        "Demain, tu ne te souviendras d'aucun d'entre eux.",
        "Tu ne t'ennuies pas. On t'exploite.",
        "C'est exactement pour ce moment-là que tu as installé cette appli.",
        "Ton futur toi te supplie de ne pas faire ça.",
        "« Juste cinq minutes » : c'est comme ça qu'ils t'ont. À chaque fois.",
        "Ton attention est le produit. Arrête de la donner gratuitement.",
        "Ferme ça. Va faire ce que tu repousses.",
        "Personne, sur son lit de mort, n'a regretté de ne pas avoir regardé plus de Shorts."],
    it=["Il feed non finisce mai. La tua giornata sì.",
        "Uno Short diventa dieci. Dieci diventano un'ora che non riavrai mai.",
        "Domani non te ne ricorderai nemmeno uno.",
        "Non ti stai annoiando. Ti stanno sfruttando.",
        "È esattamente per questo momento che hai installato quest'app.",
        "Il te del futuro ti supplica di non farlo.",
        "«Solo cinque minuti»: è così che ti fregano. Ogni singola volta.",
        "La tua attenzione è il prodotto. Smetti di regalarla.",
        "Chiudi qui. Vai a fare quello che stai rimandando.",
        "Nessuno sul letto di morte ha mai desiderato aver guardato più Shorts."],
    es=["El feed nunca se acaba. Tu día sí.",
        "Un Short se convierte en diez. Diez, en una hora que nunca recuperarás.",
        "Mañana no recordarás ni uno solo.",
        "No estás aburrido. Te están exprimiendo.",
        "Instalaste esta app justo para este momento.",
        "Tu yo del futuro te suplica que no lo hagas.",
        "«Solo cinco minutos»: así es como te atrapan. Siempre.",
        "Tu atención es el producto. Deja de regalarla.",
        "Cierra esto. Ve a hacer eso que estás evitando.",
        "Nadie en su lecho de muerte deseó haber visto más Shorts."],
    pt=["O feed nunca acaba. Seu dia, sim.",
        "Um Short vira dez. Dez viram uma hora que você nunca vai ter de volta.",
        "Amanhã você não vai lembrar de nenhum deles.",
        "Você não está entediado. Está sendo explorado.",
        "Foi exatamente para este momento que você instalou este app.",
        "Seu eu do futuro está implorando para você não fazer isso.",
        "\"Só cinco minutinhos\": é assim que eles te pegam. Toda santa vez.",
        "Sua atenção é o produto. Pare de entregá-la de graça.",
        "Feche isto. Vá fazer aquilo que você está evitando.",
        "Ninguém no leito de morte desejou ter assistido mais Shorts."],
)

s("day_1", "Day 1. Don't quit on day one.", "Tag 1. Gib nicht am ersten Tag auf.",
  "Jour 1. N'abandonne pas dès le premier jour.", "Giorno 1. Non mollare il primo giorno.",
  "Día 1. No te rindas el primer día.", "Dia 1. Não desista no primeiro dia.")
s("day_2", "Day 2. You made it through yesterday. Don't undo it today.",
  "Tag 2. Gestern hast du es geschafft. Mach es heute nicht kaputt.",
  "Jour 2. Tu as tenu hier. Ne gâche pas tout aujourd'hui.",
  "Giorno 2. Ieri ce l'hai fatta. Non rovinare tutto oggi.",
  "Día 2. Ayer lo lograste. No lo deshagas hoy.",
  "Dia 2. Ontem você conseguiu. Não jogue isso fora hoje.")
s("day_3", "Day 3. The first days are the hardest. You're almost through them.",
  "Tag 3. Die ersten Tage sind die härtesten. Du hast sie fast geschafft.",
  "Jour 3. Les premiers jours sont les plus durs. Tu y es presque.",
  "Giorno 3. I primi giorni sono i più duri. Ci sei quasi.",
  "Día 3. Los primeros días son los más duros. Ya casi los superas.",
  "Dia 3. Os primeiros dias são os mais difíceis. Você está quase lá.")
s("day_4", "Day 4. Your brain is starting to adjust. Don't reset it now.",
  "Tag 4. Dein Gehirn gewöhnt sich langsam um. Setz es jetzt nicht zurück.",
  "Jour 4. Ton cerveau commence à s'adapter. Ne le remets pas à zéro maintenant.",
  "Giorno 4. Il tuo cervello inizia ad adattarsi. Non azzerarlo adesso.",
  "Día 4. Tu cerebro empieza a adaptarse. No lo reinicies ahora.",
  "Dia 4. Seu cérebro está começando a se adaptar. Não zere tudo agora.")
s("day_5", "Day 5. Five days without the endless scroll. Don't throw them away.",
  "Tag 5. Fünf Tage ohne endloses Scrollen. Wirf sie nicht weg.",
  "Jour 5. Cinq jours sans défilement infini. Ne les gâche pas.",
  "Giorno 5. Cinque giorni senza scroll infinito. Non buttarli via.",
  "Día 5. Cinco días sin scroll infinito. No los tires.",
  "Dia 5. Cinco dias sem rolagem infinita. Não jogue isso fora.")
s("day_6", "Day 6. One more day and it's a full week. Don't quit now.",
  "Tag 6. Noch ein Tag, dann ist es eine ganze Woche. Gib jetzt nicht auf.",
  "Jour 6. Encore un jour et ça fera une semaine entière. N'abandonne pas maintenant.",
  "Giorno 6. Ancora un giorno ed è una settimana intera. Non mollare ora.",
  "Día 6. Un día más y será una semana entera. No te rindas ahora.",
  "Dia 6. Mais um dia e é uma semana inteira. Não desista agora.")
s("day_week", "Day %1$d. Don't quit in your first week Shorts-free.",
  "Tag %1$d. Gib nicht in deiner ersten Woche ohne Shorts auf.",
  "Jour %1$d. N'abandonne pas pendant ta première semaine sans Shorts.",
  "Giorno %1$d. Non mollare nella tua prima settimana senza Shorts.",
  "Día %1$d. No te rindas en tu primera semana sin Shorts.",
  "Dia %1$d. Não desista na sua primeira semana sem Shorts.")
s("day_two_weeks", "Day %1$d. Two weeks in, this is becoming a habit. Don't break it.",
  "Tag %1$d. Nach zwei Wochen wird es zur Gewohnheit. Brich sie nicht.",
  "Jour %1$d. Après deux semaines, ça devient une habitude. Ne la casse pas.",
  "Giorno %1$d. Dopo due settimane sta diventando un'abitudine. Non spezzarla.",
  "Día %1$d. Tras dos semanas, ya es un hábito. No lo rompas.",
  "Dia %1$d. Depois de duas semanas, isso está virando hábito. Não quebre.")
s("day_month", "Day %1$d. Don't quit in your first month.", "Tag %1$d. Gib nicht in deinem ersten Monat auf.",
  "Jour %1$d. N'abandonne pas pendant ton premier mois.", "Giorno %1$d. Non mollare nel tuo primo mese.",
  "Día %1$d. No te rindas en tu primer mes.", "Dia %1$d. Não desista no seu primeiro mês.")
s("day_months", "%1$d months Shorts-free. One tap and you're back at zero.",
  "%1$d Monate ohne Shorts. Ein Tipp und du bist wieder bei null.",
  "%1$d mois sans Shorts. Un seul appui et tu repars de zéro.",
  "%1$d mesi senza Shorts. Un tocco e torni a zero.",
  "%1$d meses sin Shorts. Un toque y vuelves a cero.",
  "%1$d meses sem Shorts. Um toque e você volta à estaca zero.")
s("day_half_year", "Half a year Shorts-free. Don't trade it for a few clips.",
  "Ein halbes Jahr ohne Shorts. Tausch das nicht gegen ein paar Clips.",
  "Six mois sans Shorts. Ne les échange pas contre quelques clips.",
  "Sei mesi senza Shorts. Non scambiarli per qualche clip.",
  "Medio año sin Shorts. No lo cambies por unos cuantos clips.",
  "Meio ano sem Shorts. Não troque isso por alguns vídeos.")
s("day_year", "Over a year Shorts-free. Don't let one tap undo it.",
  "Über ein Jahr ohne Shorts. Lass das nicht von einem Tipp kaputt machen.",
  "Plus d'un an sans Shorts. Ne laisse pas un seul appui tout défaire.",
  "Più di un anno senza Shorts. Non lasciare che un tocco lo annulli.",
  "Más de un año sin Shorts. No dejes que un toque lo deshaga.",
  "Mais de um ano sem Shorts. Não deixe um toque desfazer isso.")
p("progress_blocked",
  en=("%1$d Short blocked, %2$s of your life back.", "%1$d Shorts blocked, %2$s of your life back."),
  de=("%1$d Short blockiert, %2$s deines Lebens zurückgewonnen.",
      "%1$d Shorts blockiert, %2$s deines Lebens zurückgewonnen."),
  fr=("%1$d Short bloqué. Tu as regagné %2$s de ta vie.", "%1$d Shorts bloqués. Tu as regagné %2$s de ta vie."),
  it=("%1$d Short bloccato. Hai recuperato %2$s della tua vita.",
      "%1$d Short bloccati. Hai recuperato %2$s della tua vita."),
  es=("%1$d Short bloqueado. Recuperaste %2$s de tu vida.", "%1$d Shorts bloqueados. Recuperaste %2$s de tu vida."),
  pt=("%1$d Short bloqueado. Você recuperou %2$s da sua vida.",
      "%1$d Shorts bloqueados. Você recuperou %2$s da sua vida."),
)

# ---------------------------------------------------------------- Wieder einschalten
s("welcome_back", "Welcome back.", "Willkommen zurück.", "Te revoilà.", "Eccoti di nuovo.", "Hola de nuevo.",
  "Olá de novo.")
s("relock_title", "Make quitting hard next time.", "Mach das Aufgeben nächstes Mal schwer.",
  "Rends l'abandon difficile la prochaine fois.", "Rendi difficile mollare la prossima volta.",
  "Ponte difícil rendirte la próxima vez.", "Dificulte a desistência da próxima vez.")
s("relock_text",
  "The last time you gave in, the timer was all that stood in your way. Pick how long you'll have to wait before you can turn it off again.",
  "Als du das letzte Mal schwach geworden bist, stand nur der Timer im Weg. Wähle, wie lange du warten musst, bevor du wieder ausschalten kannst.",
  "La dernière fois que tu as craqué, seul le minuteur te barrait la route. Choisis combien de temps tu devras attendre avant de pouvoir la désactiver à nouveau.",
  "L'ultima volta che hai ceduto, solo il timer ti ha fermato. Scegli quanto dovrai aspettare prima di poterla disattivare di nuovo.",
  "La última vez que cediste, solo el temporizador se interponía. Elige cuánto tendrás que esperar antes de poder desactivarla otra vez.",
  "Da última vez que você cedeu, só o timer estava no caminho. Escolha quanto tempo vai ter que esperar antes de poder desativar de novo.")
s("relock_warn_short", "%1$d minutes is a speed bump, not a wall. You'll be back here in no time.",
  "%1$d Minuten sind eine Bodenwelle, keine Mauer. Du bist im Nu wieder hier.",
  "%1$d minutes, c'est un dos-d'âne, pas un mur. Tu seras de retour ici en un rien de temps.",
  "%1$d minuti sono un dosso, non un muro. Tornerai qui in un attimo.",
  "%1$d minutos son un badén, no un muro. Volverás aquí en nada.",
  "%1$d minutos são uma lombada, não um muro. Você vai estar de volta aqui rapidinho.")
s("relock_warn_mid", "Better. But be honest: will %1$d minutes really stop you next time?",
  "Besser. Aber sei ehrlich: Halten dich %1$d Minuten nächstes Mal wirklich auf?",
  "Mieux. Mais sois honnête : %1$d minutes vont vraiment t'arrêter la prochaine fois ?",
  "Meglio. Ma sii sincero: %1$d minuti ti fermeranno davvero la prossima volta?",
  "Mejor. Pero sé sincero: ¿de verdad te van a frenar %1$d minutos la próxima vez?",
  "Melhor. Mas seja sincero: %1$d minutos vão mesmo te segurar da próxima vez?")
s("relock_warn_long", "Now that's a wall. Future you will thank you.",
  "Das ist eine Mauer. Dein zukünftiges Ich wird dir danken.",
  "Ça, c'est un mur. Ton futur toi te remerciera.",
  "Questo sì che è un muro. Il te del futuro ti ringrazierà.",
  "Eso sí es un muro. Tu yo del futuro te lo agradecerá.",
  "Isso sim é um muro. Seu eu do futuro vai agradecer.")
s("custom_minutes", "Custom (minutes)", "Eigene (Minuten)", "Personnalisé (minutes)", "Personalizzato (minuti)",
  "Personalizado (minutos)", "Personalizado (minutos)")
s("at_least_minutes", "At least %1$d minutes.", "Mindestens %1$d Minuten.", "Au moins %1$d minutes.",
  "Almeno %1$d minuti.", "Al menos %1$d minutos.", "Pelo menos %1$d minutos.")
s("turn_back_on", "Turn protection back on", "Schutz wieder einschalten", "Réactiver la protection",
  "Riattiva la protezione", "Volver a activar la protección", "Reativar a proteção")
s("keep_shorts_on", "Keep Shorts on for now", "Shorts vorerst erlauben", "Garder les Shorts pour l'instant",
  "Lascia gli Shorts per ora", "Dejar los Shorts por ahora", "Deixar os Shorts por enquanto")

# ---------------------------------------------------------------- Einstellungen
s("section_subscription", "SUBSCRIPTION", "ABO", "ABONNEMENT", "ABBONAMENTO", "SUSCRIPCIÓN", "ASSINATURA")
s("pro_unlocked_code", "Unlocked with a code", "Mit Code freigeschaltet", "Débloqué avec un code",
  "Sbloccato con un codice", "Desbloqueado con un código", "Liberado com um código")
s("pro_active", "Active", "Aktiv", "Actif", "Attivo", "Activo", "Ativo")
s("manage_subscription", "Manage subscription", "Abo verwalten", "Gérer l'abonnement", "Gestisci abbonamento",
  "Gestionar suscripción", "Gerenciar assinatura")
s("manage_subscription_sub", "Switch to yearly or cancel", "Auf jährlich wechseln oder kündigen",
  "Passer à l'annuel ou annuler", "Passa all'annuale o annulla", "Cambiar a anual o cancelar",
  "Mudar para anual ou cancelar")
s("redeem_code", "Redeem code", "Code einlösen", "Utiliser un code", "Riscatta codice", "Canjear código",
  "Resgatar código")
s("section_appearance", "APPEARANCE", "DARSTELLUNG", "APPARENCE", "ASPETTO", "APARIENCIA", "APARÊNCIA")
s("theme_system", "System", "System", "Système", "Sistema", "Sistema", "Sistema")
s("theme_dark", "Dark", "Dunkel", "Sombre", "Scuro", "Oscuro", "Escuro")
s("theme_light", "Light", "Hell", "Clair", "Chiaro", "Claro", "Claro")
s("section_about", "ABOUT", "INFO", "À PROPOS", "INFO", "ACERCA DE", "SOBRE")
s("rate_app", "Rate the app", "App bewerten", "Noter l'appli", "Valuta l'app", "Valorar la app", "Avaliar o app")
s("privacy_permissions", "Privacy & permissions", "Datenschutz & Berechtigungen", "Confidentialité et autorisations",
  "Privacy e autorizzazioni", "Privacidad y permisos", "Privacidade e permissões")
s("privacy_policy", "Privacy policy", "Datenschutzerklärung", "Politique de confidentialité",
  "Informativa sulla privacy", "Política de privacidad", "Política de privacidade")
s("support_id", "Support ID", "Support-ID", "ID d'assistance", "ID assistenza", "ID de soporte", "ID de suporte")
s("support_id_hint", "Include it if you email us about your data. Tap to copy.",
  "Gib sie an, wenn du uns wegen deiner Daten schreibst. Tippen zum Kopieren.",
  "Indique-le si tu nous écris au sujet de tes données. Touche pour copier.",
  "Indicalo se ci scrivi per i tuoi dati. Tocca per copiare.",
  "Inclúyelo si nos escribes sobre tus datos. Toca para copiar.",
  "Inclua-o se nos escrever sobre seus dados. Toque para copiar.")
s("support_id_copied", "Support ID copied", "Support-ID kopiert", "ID d'assistance copié", "ID assistenza copiato",
  "ID de soporte copiado", "ID de suporte copiado")
s("version", "Version %1$s", "Version %1$s", "Version %1$s", "Versione %1$s", "Versión %1$s", "Versão %1$s")
s("not_affiliated", "Not affiliated with YouTube or Google.", "Keine Verbindung zu YouTube oder Google.",
  "Non affilié à YouTube ni à Google.", "Non affiliato a YouTube o Google.", "Sin relación con YouTube ni Google.",
  "Sem vínculo com o YouTube ou o Google.")
s("run_setup_again", "Run setup again", "Einrichtung wiederholen", "Relancer la configuration",
  "Ripeti la configurazione", "Repetir la configuración", "Refazer a configuração")
s("code_hint", "Code", "Code", "Code", "Codice", "Código", "Código")
s("redeem", "Redeem", "Einlösen", "Valider", "Riscatta", "Canjear", "Resgatar")
s("privacy", "Privacy", "Datenschutz", "Confidentialité", "Privacy", "Privacidad", "Privacidade")
s("back", "Back", "Zurück", "Retour", "Indietro", "Atrás", "Voltar")

# ---------------------------------------------------------------- Meldungen (MainActivity)
s("paywall_no_placement", "Paywall not shown: placement \"%1$s\" isn't in a Superwall campaign.",
  "Paywall nicht gezeigt: Placement „%1$s“ ist in keiner Superwall-Kampagne.",
  "Paywall non affiché : le placement « %1$s » n'est dans aucune campagne Superwall.",
  "Paywall non mostrato: il placement «%1$s» non è in nessuna campagna Superwall.",
  "Paywall no mostrado: el placement «%1$s» no está en ninguna campaña de Superwall.",
  "Paywall não exibido: o placement \"%1$s\" não está em nenhuma campanha do Superwall.")
s("paywall_no_audience", "Paywall not shown: no audience matches in the campaign.",
  "Paywall nicht gezeigt: Keine Zielgruppe der Kampagne passt.",
  "Paywall non affiché : aucune audience de la campagne ne correspond.",
  "Paywall non mostrato: nessun pubblico della campagna corrisponde.",
  "Paywall no mostrado: ninguna audiencia de la campaña coincide.",
  "Paywall não exibido: nenhum público da campanha corresponde.")
s("paywall_holdout", "Paywall not shown: you're in the campaign's holdout group.",
  "Paywall nicht gezeigt: Du bist in der Kontrollgruppe der Kampagne.",
  "Paywall non affiché : tu fais partie du groupe témoin de la campagne.",
  "Paywall non mostrato: sei nel gruppo di controllo della campagna.",
  "Paywall no mostrado: estás en el grupo de control de la campaña.",
  "Paywall não exibido: você está no grupo de controle da campanha.")
s("already_pro", "You already have Pro.", "Du hast schon Pro.", "Tu as déjà Pro.", "Hai già Pro.", "Ya tienes Pro.",
  "Você já tem o Pro.")
s("paywall_skipped", "Paywall not shown.", "Paywall nicht gezeigt.", "Paywall non affiché.", "Paywall non mostrato.",
  "Paywall no mostrado.", "Paywall não exibido.")
s("paywall_error", "Paywall error: %1$s", "Paywall-Fehler: %1$s", "Erreur du paywall : %1$s",
  "Errore del paywall: %1$s", "Error del paywall: %1$s", "Erro no paywall: %1$s")
s("restore_failed", "Couldn't check purchases. Try again.",
  "Käufe konnten nicht geprüft werden. Versuch es nochmal.",
  "Impossible de vérifier les achats. Réessaie.", "Impossibile verificare gli acquisti. Riprova.",
  "No se pudieron comprobar las compras. Inténtalo de nuevo.", "Não foi possível verificar as compras. Tente de novo.")
s("restore_done", "Purchases restored", "Käufe wiederhergestellt", "Achats restaurés", "Acquisti ripristinati",
  "Compras restauradas", "Compras restauradas")
s("restore_none", "No active subscription found", "Kein aktives Abo gefunden", "Aucun abonnement actif trouvé",
  "Nessun abbonamento attivo trovato", "No se encontró ninguna suscripción activa",
  "Nenhuma assinatura ativa encontrada")
s("code_unlocked", "Unlocked. F*ck Shorts is free for you.", "Freigeschaltet. F*ck Shorts ist für dich gratis.",
  "Débloqué. F*ck Shorts est gratuit pour toi.", "Sbloccato. F*ck Shorts è gratis per te.",
  "Desbloqueado. F*ck Shorts es gratis para ti.", "Liberado. O F*ck Shorts é grátis para você.")
s("code_fast_timer", "Timer is now 5 seconds.", "Der Timer dauert jetzt 5 Sekunden.",
  "Le minuteur dure maintenant 5 secondes.", "Ora il timer dura 5 secondi.",
  "Ahora el temporizador dura 5 segundos.", "Agora o timer dura 5 segundos.")
s("code_invalid", "This code isn't valid.", "Dieser Code ist ungültig.", "Ce code n'est pas valide.",
  "Questo codice non è valido.", "Este código no es válido.", "Este código não é válido.")
s("pro_with_code", "Pro is unlocked with a code", "Pro ist mit einem Code freigeschaltet",
  "Pro est débloqué avec un code", "Pro è sbloccato con un codice", "Pro está desbloqueado con un código",
  "O Pro está liberado com um código")
s("youtube_missing", "YouTube isn't installed", "YouTube ist nicht installiert", "YouTube n'est pas installé",
  "YouTube non è installato", "YouTube no está instalado", "O YouTube não está instalado")
s("link_failed", "Couldn't open the link", "Link konnte nicht geöffnet werden", "Impossible d'ouvrir le lien",
  "Impossibile aprire il link", "No se pudo abrir el enlace", "Não foi possível abrir o link")


# ---------------------------------------------------------------- Ausgabe
NBSP = "\u00a0"


def french(text):
    # Französische Typografie: geschütztes Leerzeichen vor ? ! : ; und in « ».
    text = re.sub(r" ([?!:;»])", NBSP + r"\1", text)
    return text.replace("« ", "«" + NBSP)


def swiss(text):
    return text.replace("ß", "ss").replace("„", "«").replace("“", "»")


def esc(text):
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if text[:1] in "@?":
        text = "\\" + text
    return text


def build(lang, fix=lambda t: t):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           "<!-- Erzeugt von tools/i18n/gen_strings.py – nicht von Hand ändern. -->",
           "<resources>"]
    if lang == "en":
        out.append('    <string name="app_name" translatable="false">F*ck Shorts</string>')
    for key, texts in S.items():
        out.append(f'    <string name="{key}">{esc(fix(texts[lang]))}</string>')
    for key, langs in P.items():
        one, other = langs[lang]
        out.append(f'    <plurals name="{key}">')
        out.append(f'        <item quantity="one">{esc(fix(one))}</item>')
        if lang in ("fr", "it", "es", "pt"):
            out.append(f'        <item quantity="many">{esc(fix(other))}</item>')
        out.append(f'        <item quantity="other">{esc(fix(other))}</item>')
        out.append("    </plurals>")
    for key, langs in A.items():
        out.append(f'    <string-array name="{key}">')
        for item in langs[lang]:
            out.append(f"        <item>{esc(fix(item))}</item>")
        out.append("    </string-array>")
    out.append("</resources>")
    return "\n".join(out) + "\n"


def write(folder, content):
    path = os.path.join(ROOT, folder)
    os.makedirs(path, exist_ok=True)
    with open(os.path.join(path, "strings.xml"), "w", encoding="utf-8") as f:
        f.write(content)


for key, texts in S.items():
    assert set(texts) == set(LANGS), key
    n = len(re.findall(r"%\d\$[sd]", texts["en"]))
    for lang in LANGS:
        assert len(re.findall(r"%\d\$[sd]", texts[lang])) == n, (key, lang)
for key, langs in A.items():
    assert all(len(langs[l]) == len(langs["en"]) for l in LANGS), key

write("values", build("en"))
for lang in LANGS[1:]:
    write(f"values-{lang}", build(lang, french if lang == "fr" else (lambda t: t)))
write("values-de-rCH", build("de", swiss))
print("ok:", len(S), "Texte,", len(P), "Plurale,", len(A), "Listen")
