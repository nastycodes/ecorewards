# EcoRewards

[🇬🇧 English version](README.md)

Fabric-Mod für **Minecraft 1.21.11** (Fabric Loader 0.20.1+): Spieler bekommen für **jeden abgebauten Block** und
**jeden getöteten Mob** Geld über die [EcoBal Economy API](https://modrinth.com/mod/ecobal). Ein Level, das mit dem
Fortschritt steigt, multipliziert die Belohnung. Belohnungen pro Block/Mob, die XP-Kurve und der Level-Multiplikator
sind frei einstellbar – im Spiel oder in der Config-Datei.

Rein serverseitig – Spieler brauchen den Mod nicht. Im Einzelspieler funktioniert er auch (dann mit EcoBal im Client
installieren).

## Installation (Server)

In den `mods`-Ordner:

| Mod | Version |
| --- | --- |
| `ecorewards-1.0.0.jar` | 1.0.0 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 0.141.6+1.21.11 oder neuer |
| [EcoBal Economy API](https://modrinth.com/mod/ecobal) | 1.0.1 für 1.21.11 |
| [fabric-permissions-api](https://modrinth.com/mod/fabric-permissions-api) | 0.6.1 (wird von EcoBal verlangt) |
| [Mod Menu](https://modrinth.com/mod/modmenu) | optional, nur Client: Einstellungsbildschirm im Spiel |

## So funktioniert es

- **Belohnung** = Grundwert des Blocks/Mobs × Level-Multiplikator, auf 2 Nachkommastellen gerundet.
- **XP** (Fortschrittspunkte, nicht die Vanilla-Erfahrung) gibt es pro Block/Mob zusätzlich; genug XP ⇒ Level-Up.
- Nach jeder Belohnung erscheint in der Actionbar z. B. `+$2.50 | Level 7 (x1.3) | 120/159 XP`, bei Level-Up eine
  Chatnachricht mit Sound.
- Das Geld wird gesammelt und alle `payoutIntervalSeconds` Sekunden an EcoBal ausgezahlt (beim Ausloggen und
  Server-Stopp sofort). Grund: EcoBal schreibt bei jeder einzelnen Einzahlung seine komplette Kontodatei auf die Platte.
- Mobs zählen, wenn ein Spieler den Todesstoß gibt **oder** sie kurz vorher getroffen hat (in Lava geschubst, vom
  gezähmten Wolf getötet …).

### Schutz gegen Geld-Farmen

Blöcke, die nicht mit der Welt generiert wurden, bringen nichts (`antiExploit.ignoreArtificialBlocks`):

- von Spielern platzierte Blöcke (Platzieren → Abbauen → Platzieren …),
- Cobblestone-/Stein-/Obsidian-/Basalt-Generatoren (Lava + Wasser),
- per Kolben verschobene Blöcke, fallender Sand/Kies, Eis-Farmen, Schneegolem-Spuren, Blöcke von anderen Mods.

Ausnahmen:

- **Natürliches Wachstum** zählt als natürlich: Bäume, Zuckerrohr, Kaktus, Bambus, Melonen, Kürbisse, Amethyst …
  (Liste `antiExploit.naturalGrowthBlocks`).
- **Feldfrüchte** (Weizen, Karotten, Netherwarzen, Kakao, Beeren …) bringen auch selbst gepflanzt Geld – aber nur
  ausgewachsen (`blocks.matureCropsOnly`).
- **Befehle** (`/setblock`, `/fill`, Funktionen, Befehlsblöcke) setzen natürliche Blöcke – praktisch für Admin-Minen,
  die per `/fill` zurückgesetzt werden.
- Ein platzierter Block bleibt künstlich, wenn er sich verwandelt (Stamm entrinden, Erde umgraben, Kupfer oxidiert);
  abbauen löscht die Markierung.

Mobs aus Monster-Spawnern geben nur `mobs.spawnerMultiplier` (Standard 25 %) von Geld und XP.

## Befehle

| Befehl | Recht | Wirkung |
| --- | --- | --- |
| `/ecorewards` oder `/lvl` | alle | Eigenes Level, XP und Multiplikator |
| `/ecorewards info <Spieler>` | OP 2 | Level eines anderen Spielers |
| `/ecorewards setlevel <Spieler> <Level>` | OP 2 | Level setzen (XP auf 0) |
| `/ecorewards addxp <Spieler> <XP>` | OP 2 | XP geben (mit Level-Ups) |
| `/ecorewards reset <Spieler>` | OP 2 | Zurück auf Level 1 |
| `/ecorewards check <x y z>` | OP 2 | Ist der Block natürlich oder künstlich? |
| `/ecorewards natural <von> <bis>` | OP 2 | Bereich als natürlich markieren (z. B. von Hand gebaute Mine, max. 256 Chunks) |
| `/ecorewards reload` | OP 2 | Config neu laden |

## Sprachen

Alle Texte gibt es auf **Englisch** und **Deutsch**. Spieler, die den Mod installiert haben, sehen ihre eigene
Spielsprache. Für alle anderen (auf Servern der Normalfall) entscheidet die Option `language`: `"en_us"` (Standard)
oder `"de_de"`. Für einen deutschen Server also `"language": "de_de"` setzen. Nachrichten, die du nicht geändert hast,
wechseln automatisch mit; selbst angepasste bleiben, wie sie sind.

## Einstellungen

**Im Spiel:** Mit [Mod Menu](https://modrinth.com/mod/modmenu) **Mods → EcoRewards → Einstellungs-Button** öffnen.
Jede Option hat einen Tooltip und einen „Standard“-Button, Eingaben werden vor dem Speichern geprüft, Änderungen gelten
sofort. Listen und Belohnungstabellen werden als Text bearbeitet, ein Eintrag pro Zeile (`minecraft:zombie = 2,5, 3`).
Der Bildschirm bearbeitet die Config des Spiels, in dem du bist – deine eigene Einzelspieler-/LAN-Welt; auf einem
Server die Datei des Servers bearbeiten.

**Config-Datei:** `config/ecorewards.json`, wird beim ersten Start mit Standardwerten angelegt. Nach Änderungen
`/ecorewards reload`.

### Allgemein

| Schlüssel | Standard | Bedeutung |
| --- | --- | --- |
| `rewardInCreative` | `false` | Auch im Kreativmodus belohnen |
| `payoutIntervalSeconds` | `3` | Auszahlungsintervall an EcoBal (`0` = sofort) |
| `language` | `"en_us"` | Server-Sprache für Spieler ohne den Mod: `"en_us"` oder `"de_de"` |

### Level (`levels`)

XP von Level N auf N+1 = `xpBase × xpGrowth^(N−1) + xpIncrement × (N−1)`

| Schlüssel | Standard | Bedeutung |
| --- | --- | --- |
| `maxLevel` | `50` | Höchstes Level |
| `xpBase` | `100` | XP für Level 1 → 2 |
| `xpGrowth` | `1.08` | Faktor pro Level (`1.0` = immer gleich viel) |
| `xpIncrement` | `0` | Zusätzliche feste XP pro Level |
| `xpOverrides` | `{}` | Feste Werte, z. B. `{"10": 5000}` = Level 10 → 11 braucht 5000 XP |

Mit den Standardwerten: Level 2 nach 100 XP, Level 10 nach ~1.250 XP, Level 50 nach ~53.000 XP.

### Level-Multiplikator (`multiplier`)

Multiplikator = `base + perLevel × (Level − 1)`, höchstens `max`

| Schlüssel | Standard | Bedeutung |
| --- | --- | --- |
| `base` | `1.0` | Multiplikator auf Level 1 |
| `perLevel` | `0.05` | Zuwachs pro Level (+5 %) → Level 50 = ×3,45 |
| `max` | `0` | Obergrenze (`0` = keine) |
| `overrides` | `{}` | Feste Werte pro Level, z. B. `{"25": 2.0, "50": 5.0}` |

### Blöcke (`blocks`) und Mobs (`mobs`)

```json
"rewards": {
  "default":                  { "money": 0.05, "xp": 1 },
  "minecraft:ancient_debris": { "money": 50.0, "xp": 40 },
  "#minecraft:diamond_ores":  { "money": 25.0, "xp": 20 },
  "minecraft:villager":       { "money": 0.0,  "xp": 0 }
}
```

- Schlüssel = Block-/Entity-ID (`minecraft:zombie`) oder Tag mit `#` (`#minecraft:logs`, `#minecraft:undead`).
  Mod-Blöcke/-Mobs funktionieren genauso.
- Reihenfolge: exakte ID → erster passender Tag (von oben nach unten) → `default`.
- `default` gilt für **alle** anderen Blöcke/Mobs. Entfernen ⇒ nur gelistete zählen. `{ "money": 0, "xp": 0 }`
  schließt etwas aus.
- Unbekannte IDs (Tippfehler) werden beim Serverstart im Log gemeldet.

| Schlüssel | Standard | Bedeutung |
| --- | --- | --- |
| `blocks.enabled` / `mobs.enabled` | `true` | Ein-/ausschalten |
| `blocks.requireCorrectTool` | `true` | Erz/Stein nur mit passendem Werkzeug |
| `blocks.matureCropsOnly` | `true` | Feldfrüchte nur ausgewachsen |
| `blocks.defaultSkipsInstantBreak` | `true` | `default` gilt nicht für sofort zerstörbare Blöcke (Gras, Blumen, Fackeln); gelistete zählen trotzdem |
| `mobs.spawnerMultiplier` | `0.25` | Faktor für Mobs aus Monster-Spawnern |

### Anti-Exploit (`antiExploit`)

| Schlüssel | Standard | Bedeutung |
| --- | --- | --- |
| `ignoreArtificialBlocks` | `true` | Künstliche Blöcke bringen nichts |
| `naturalGrowthBlocks` | Bäume, Pflanzen … | Blöcke (IDs/Tags), die beim Wachsen als natürlich gelten |

### Nachrichten (`messages`)

Farbcodes `&a`, `&l`, … und Hex-Farben `&#FF8800`. Leerer Text = keine Nachricht.
Platzhalter: `{player}`, `{money}`, `{gainedXp}`, `{level}`, `{multiplier}`, `{xp}`, `{xpNeeded}`, `{progress}`.

| Schlüssel | Bedeutung |
| --- | --- |
| `showRewardActionBar` | Actionbar nach jeder Belohnung |
| `reward` / `rewardXpOnly` | Actionbar mit Geld / nur XP |
| `progress` / `progressMaxLevel` | Inhalt von `{progress}` |
| `levelUp`, `levelUpSound` | Level-Up-Nachricht und Sound |
| `broadcastLevelUp`, `levelUpBroadcast` | Level-Up an alle Spieler melden |
| `info` / `infoOther` | Ausgabe von `/ecorewards` |

## Entwicklung

Braucht ein JDK 21+.

```
gradlew build              # baut build/libs/ecorewards-1.0.0.jar, inkl. Server-Gametests und Übersetzungsprüfung
gradlew runGameTest        # nur die Server-Gametests
gradlew runClientGameTest  # End-to-End-Test im echten Client, Screenshots in build/run/clientGameTest/screenshots
python tools/gen_icon.py . # erzeugt das Icon neu
```

`runGameTest` startet einen Testserver mit EcoBal und prüft u. a.: natürliches Erz zahlt × Multiplikator, ohne
Werkzeug/platziert nichts, Feldfrüchte nur ausgewachsen, Mob-Kills inkl. Spawner-Abzug und indirekter Kills,
Level-Kurve, Obsidian aus Lava + Wasser, Kolben, `/setblock`, Wachstum, dass alle Mixins greifen und dass
unveränderte Nachrichten der Server-Sprache folgen. Der Client-Gametest (Englisch, 1920×1080, mit EcoBal) baut Erze
ab, steigt ein Level auf, tötet einen Wüstenzombie, führt `/lvl` aus und ändert Einstellungen im Bildschirm hinter dem
Mod-Menu-Button – einen Wert, eine Belohnungstabelle, die Server-Sprache und die Abfrage „Änderungen verwerfen?“.
`checkTranslations` (Teil von `build`) bricht ab, wenn `en_us.json` und `de_de.json` nicht dieselben Schlüssel haben.

## Lizenz

[MIT](LICENSE)
