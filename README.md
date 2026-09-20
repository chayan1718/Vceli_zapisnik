# Včelí zápisník 🐝

Android aplikace pro **hlasový zápis prohlídek úlů**. U úlu nadiktujete, co vidíte,
a appka z řeči vytáhne jednotlivé položky a uloží je jako strukturovaný záznam.

Příklad — řeknete:

> „Úl číslo pět, zavíčkovaný plod na dvou rámcích, zásoby tři rámky, matka nalezena v pořádku.“

a appka z toho udělá:

| Položka | Hodnota |
|---|---|
| Úl | 5 |
| Zavíčkovaný plod | 2 rámky |
| Zásoby | 3 rámky |
| Matka nalezena | ano |
| Stav matky | v pořádku |

Než se záznam uloží, uvidíte **náhled** a můžete cokoli opravit.

---

## Co je v této verzi nového

- **Souvislý diktát** – klepnete na **Diktovat**, appka průběžně zobrazuje, co
  slyší (živý náhled textu), a klepnutím na **Zastavit** diktování ukončíte.
  Není potřeba systémové okno. Když souvislé rozpoznávání na telefonu není,
  appka se automaticky vrátí ke klasickému oknu pro diktování.
- **Fotka a poloha u záznamu** – k prohlídce můžete připojit **fotku** (např.
  plástu) a automaticky se doplní **GPS souřadnice** stanoviště (pokud povolíte
  polohu). Obojí se ukládá i do CSV.
- **Přehled podle úlů** – druhá záložka **Přehled úlů** ukazuje každý úl se
  souhrnem (počet záznamů, poslední prohlídka, poslední zásoby a plod).
  Klepnutím na úl zobrazíte jen jeho záznamy.

---

## Jak to funguje

Appka řeší úlohu ve dvou krocích:

1. **Řeč → text.** Použije se vestavěné rozpoznávání řeči Androidu v češtině
   (`RecognizerIntent`, jazyk `cs-CZ`). Zobrazí se známé okno pro diktování
   a vrátí přepis.
2. **Text → položky.** Přepis zpracuje parser (`InspectionParser`), který
   v textu hledá klíčová slova (plod, zásoby, matka, krmení…) a poblíž nich
   číslovky. Výsledek naplní formulář, který potvrdíte.

Parser pracuje **offline a zdarma** — u úlu nepotřebujete signál. Text si předem
převádí na malá písmena bez diakritiky, takže rozbor funguje i když rozpoznávání
vrátí slova bez háčků a čárek.

### Volitelně: přesnější rozbor přes AI

Pokud chcete, aby appka zvládla i hodně volné formulace, je připravená třída
`LlmParser`, která pošle přepis jazykovému modelu a ten vrátí vyplněné položky.
Zapíná se doplněním API klíče (viz `LlmParser.kt`). Když je vypnutá nebo je
telefon offline, automaticky se použije offline parser. Klíč **nedávejte natvrdo
do kódu ve sdíleném repozitáři** — použijte `local.properties` / `BuildConfig`
nebo backendový proxy.

---

## Co appka zaznamenává

Datový model (`Inspection.kt`) pokrývá čtyři skupiny:

**Základ** — zavíčkovaný plod, otevřený plod, zásoby (vše v rámcích), matka
(nalezena / stav), vajíčka.

**Síla a zdraví** — obsednuté uličky, mírnost, příznaky nemocí (varroáza, mor
plodu, zvápenatění, nosema), spad roztočů.

**Zásahy a krmení** — rozšíření/zúžení, mezistěny, medník/nástavek, krmení
(sirup/cukr + množství), léčení (kyselina mravenčí/šťavelová, Gabon, Formidol…).

**Med a odběry** — odhad/výnos medu v kg, odebrané rámky, úkoly do příště.

**Fotka a poloha** — fotka pořízená u úlu a GPS souřadnice stanoviště.

Ke každému záznamu se ukládá i **původní přepis řeči** a **volná poznámka**.

### Oprávnění, o která si appka řekne

- **Mikrofon** – pro diktování (nutné).
- **Poloha** – aby se k záznamu doplnily GPS souřadnice (volitelné).
- Fotoaparát se spouští systémovou aplikací, takže appka nepotřebuje zvláštní
  oprávnění k fotoaparátu.

---

## Instalace do telefonu

Aplikaci je potřeba nejdřív **sestavit** (appka zatím není na Google Play).
Potřebujete k tomu počítač s **Android Studiem**. Telefon musí mít **Android 8.0
(API 26)** nebo novější. Máte dvě možnosti.

### Možnost A — nejjednodušší: telefon připojený k počítači (přes Android Studio)

1. Na počítači nainstalujte **Android Studio** (zdarma, Windows/macOS/Linux) ze
   stránky `developer.android.com/studio`.
2. Rozbalte tento projekt a v Android Studiu zvolte **Open** → vyberte složku
   `VceliZapisnik`. Při prvním otevření si Android Studio samo stáhne Gradle a
   potřebné knihovny (chvíli to trvá, je potřeba internet).
3. V telefonu zapněte **Vývojářská nastavení** a **Ladění přes USB**:
   *Nastavení → O telefonu → 7× klepněte na „Číslo sestavení"*, tím se odemkne
   *Nastavení → Systém → Pro vývojáře*, kde zapnete **Ladění USB**.
4. Připojte telefon k počítači USB kabelem, na telefonu potvrďte dotaz „Povolit
   ladění USB".
5. V Android Studiu nahoře vyberte svůj telefon a klepněte na **Run ▶**.
   Aplikace se sama nainstaluje a spustí.

### Možnost B — vyrobit APK a přenést do telefonu

1. V projektu spusťte sestavení APK (v Android Studiu *Build → Build APK*, nebo
   z příkazové řádky ve složce projektu):

   ```
   ./gradlew assembleDebug
   ```

   Hotové APK najdete v `app/build/outputs/apk/debug/app-debug.apk`.
2. Přeneste soubor `app-debug.apk` do telefonu (kabelem, e‑mailem, přes Disk
   Google apod.).
3. V telefonu soubor otevřete. Android se zeptá, zda **povolit instalaci z
   tohoto zdroje** — potvrďte (u prohlížeče/správce souborů zapnete „Instalovat
   neznámé aplikace"). Pak dejte **Instalovat**.

> Poznámka: Jde o tzv. *debug* APK pro vlastní použití. Pro zveřejnění na Google
> Play by bylo potřeba APK/AAB **podepsat** vlastním klíčem (*Build → Generate
> Signed Bundle / APK*) a nahrát v Google Play Console — to je samostatný krok
> nad rámec tohoto základu.

### Po instalaci

Pro diktování mějte v telefonu **hlasové zadávání Google** (většinou je
předinstalované; jinak nainstalujte aplikaci *Google* z Obchodu Play). Při
prvním diktování si appka vyžádá **mikrofon**, případně **polohu**. Chcete‑li
diktovat i **bez internetu**, stáhněte si offline balíček češtiny:
*Nastavení → Systém → Jazyky a zadávání → Hlasové zadávání → Rozpoznávání řeči
offline → Čeština*.

---

## Použití

1. Klepněte na **Nadiktovat** a řekněte prohlídku jednou větou.
2. Zkontrolujte náhled, případně upravte pole. Prázdná pole se neuloží.
3. **Uložit** → záznam se přidá do seznamu (nejnovější nahoře).
4. Ikonou stažení v horní liště **exportujete všechny záznamy do CSV**
   (oddělovač `;`, kódování UTF‑8 — otevře se přímo v Excelu) a můžete je
   odeslat mailem, do Disku apod.

### Tipy na formulace, které parser spolehlivě chápe

- „úl číslo **pět**“ / „úl **12**“ / „úlek číslo sedm“
- „zavíčkovaný plod na **dvou** rámcích“, „otevřený plod na **jednom** rámku“
- „zásoby **tři** rámky“, „zásoby **dva a půl** rámku“
- „matka **nalezena** v pořádku“ / „matka **klade**“ / „matku jsem **neviděl**“
- „**mírné** včelstvo“, „nakrmeno **dvěma litry** sirupu“, „**varroáza**“
- „vytočeno **patnáct kilo** medu“, „příště **rozšířit**“

Číslovky rozumí slovem i číslicí, do dvaceti a s „a půl“. Když parser něco mine,
prostě to v náhledu dopište — a formulaci si postupně osaháte.

---

## Struktura projektu

```
app/src/main/java/cz/vcely/zapisnik/
├─ MainActivity.kt            – vstupní bod, diktát, foto, oprávnění, export
├─ data/                      – Room databáze (Hive, Inspection, DAO)
├─ parser/
│  ├─ Draft.kt                – rozpracovaný záznam (text → Inspection)
│  ├─ InspectionParser.kt     – offline český parser (jádro appky)
│  └─ LlmParser.kt            – volitelný rozbor přes AI
├─ speech/SpeechRecognizerManager.kt – souvislý diktát s živým náhledem
├─ location/LocationHelper.kt        – odečet GPS polohy
├─ export/CsvExporter.kt      – export do CSV
└─ ui/                        – Compose obrazovky (Záznamy, Přehled), ViewModel
```

---

## Co dál (nápady na další rozšíření)

- **Grafy vývoje** úlu v čase (zásoby, plod) a upozornění na úkoly „do příště".
- **Zálohování** do cloudu a export do Excelu/PDF (teď je CSV pro Excel).
- **Mapa stanovišť** z uložených GPS souřadnic.
- **Vlastní slovník** pojmů — parser lze snadno doplnit o další klíčová slova
  v `InspectionParser.kt`.

---

Vytvořeno jako funkční základ (MVP). Parser byl otestován na typických větách
včetně zadání „úl číslo pět, zavíčkovaný plod na dvou rámcích, zásoby tři rámky,
matka nalezena v pořádku“.
