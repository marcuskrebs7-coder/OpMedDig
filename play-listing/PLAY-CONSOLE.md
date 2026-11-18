# Op Med Dig i Google Play – det der skal udfyldes

Konto: **MK Gardiner** (organisation, verificeret). Pakkenavn: `dk.opmeddig`.
Upload-fil: `Play-upload\OpMedDig-<version>.aab` (bygges af `release.ps1`).
Upload-nøgle: `C:\Users\Marcus Krebs\OpMedDig-signing\upload.jks` + `keystore.properties` (adgangskoden står i filen). **Tag backup af hele mappen.** Mistes den, kan nøglen nulstilles via Play-support, men det tager dage.

## 1. Opret app
- Appnavn: **Op Med Dig**
- Standardsprog: **Dansk – da-DK**
- App eller spil: **App**
- Gratis eller betalt: **Gratis** (kan ikke ændres til betalt senere)
- Erklæringer: udviklerprogrampolitikker og amerikanske eksportlove (Marcus accepterer)

## 2. Butiksfortegnelse (dansk)
**Kort beskrivelse (max 80 tegn):**
Vækkeuret du ikke kan snooze: løs opgaver og tag et billede for at slukke

**Fuld beskrivelse:**
Har du svært ved at komme op? Op Med Dig er et vækkeur, der ikke lader dig slukke for alarmen, før du er rigtig vågen.

Sådan slukker du alarmen:
• Løs et antal opgaver: regnestykker, forbind prikkerne, vendespil eller skriv en sætning af
• Gå ud af soveværelset og tag et billede af det sted, du har valgt, fx kaffemaskinen
• Først da stopper alarmen

Det gør den svær at sove videre fra:
• Lyden starter lavt og bliver højere, og lydstyrken er låst, mens den ringer
• Tilbage-knappen og lydknapperne virker ikke på alarmskærmen
• Alarmen ringer også, når telefonen er låst eller på lydløs
• 15 minutter efter ringer den én gang til, med kun én opgave

Indstil det, som det passer dig:
• Flere alarmer med eget tidspunkt, ugedage og kaldenavn
• Vælg selv opgavetyper og hvor mange opgaver
• Vælg alarmlyd, eller brug din egen lydfil
• Dansk og engelsk

Ingen reklamer, ingen konto og ingen dataindsamling. Alt bliver på din telefon.

**Grafik** (mappen `play-listing`):
- App-ikon 512×512: `icon-512.png`
- Feature-grafik 1024×500: `feature-1024x500.png`
- Telefon-skærmbilleder: `screens\da-1-forside.png`, `da-2-ny-alarm.png`, `da-3-prikker.png`, `da-4-skriv.png`

**Kategori:** Værktøjer (Tools) · **Tags:** Alarm, Ur
**Kontakt:** info@mkgardiner.dk · Website: https://mkgardiner.dk

## 3. Butiksfortegnelse (engelsk – en-US, tilføj som oversættelse)
**Short description:** The alarm clock you can't snooze: solve tasks and take a photo to stop it
**Full description:**
Struggle to get up? Op Med Dig is an alarm clock that won't let you turn it off until you're properly awake.

To stop the alarm:
• Solve a set of tasks: math, connect the dots, match pictures or type a sentence
• Leave the bedroom and take a photo of the spot you picked, like the coffee machine
• Only then does the alarm stop

Hard to sleep through:
• The sound starts quiet and gets louder, and the volume is locked while it rings
• The back and volume buttons do nothing on the alarm screen
• It rings even when the phone is locked or on silent
• 15 minutes later it rings once more, with just one task

Make it yours:
• Several alarms with their own time, weekdays and name
• Pick task types and how many tasks
• Choose the alarm sound, or use your own sound file
• Danish and English

No ads, no account and no data collection. Everything stays on your phone.

## 4. App-indhold (Politik › App-indhold)
- **Privatlivspolitik:** https://github.com/marcuskrebs7-coder/OpMedDig/blob/main/PRIVACY.md
- **Annoncer:** Nej, appen indeholder ikke annoncer
- **Appadgang:** Alle funktioner er tilgængelige uden adgangsbegrænsninger (intet login)
- **Indholdsklassificering** (IARC-spørgeskema): kategori "Hjælpeprogram, produktivitet, kommunikation eller andet". Svar **Nej** til alt (vold, sex, sprog, stoffer, hasard, brugerinteraktion, deling af placering, køb). Forventet: PEGI 3 / alle aldre.
- **Målgruppe:** 13 år og ældre (vælg 13–15, 16–17, 18+). Ikke henvendt til børn.
- **Nyhedsapp:** Nej · **Covid-19-app:** Nej · **Offentlig myndighed:** Nej · **Finansielle funktioner:** Ingen · **Sundhed:** Ingen
- **Datasikkerhed:**
  - Indsamler eller deler appen nogen af de påkrævede brugerdatatyper? **Nej**
  - (Billeder og lydfiler behandles kun på enheden og forlader den aldrig. Play-udgaven har ikke engang internettilladelse.)

## 5. Tilladelseserklæringer (Play spørger, når .aab uploades)
- **Præcise alarmer (USE_EXACT_ALARM):** Appen er et vækkeur. Kernefunktion: alarmer der skal ringe på præcis det tidspunkt, brugeren har valgt.
- **Fuldskærmsnotifikationer (USE_FULL_SCREEN_INTENT):** Vækkeur. Alarmskærmen skal vises over låseskærmen, når alarmen ringer.
- **Forgrundstjeneste – mediaPlayback:** Afspiller alarmlyden (sang eller alarmtone) mens alarmen ringer, indtil brugeren har slukket den. Brugeren starter den ved at indstille en alarm; den stopper, når alarmen slukkes. Play kan bede om en kort video: optag skærmen mens en testalarm ringer og slukkes.

## 6. Udgivelse
1. **Test › Intern test**: opret udgivelse, upload .aab, tilføj testerliste (fx "Mig" med marcuskrebs7@gmail.com), udrul. Installér fra opt-in-linket og prøv en alarm.
2. Når den virker: **Produktion › Opret ny udgivelse**, vælg samme build (eller upload igen), lande: Danmark (eller alle), send til gennemgang. Første gennemgang tager typisk 1–7 dage.

## Nye versioner
Hæv `versionCode` og `versionName` i `app\build.gradle.kts`, kør `release.ps1` (lægger GitHub-udgaven op og laver ny .aab i `Play-upload`), og upload .aab'en i Play Console.
