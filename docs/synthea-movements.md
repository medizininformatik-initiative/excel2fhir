# Synthetische Bewegungen und explizite Kontakte

Synthea-Fälle erhalten beim Excel-Import zusätzliche Kontakte für deutsche
Versorgungsstellen. Vorhandene Patientendaten, Behandlungszeitpunkte und
Einrichtungskontakte bleiben erhalten. Die Anreicherung ist synthetisch und wird
getrennt im Begleitbericht unter `movements` ausgewiesen.

## Fachliche Grundlage

Der [Fall-IG 2026.0.1](https://medizininformatik-initiative.github.io/kerndatensatz-basis/de/StructureDefinition-mii-pr-fall-kontakt-gesundheitseinrichtung.html)
beschreibt eine Hierarchie von Einrichtung, Abteilung und Versorgungsstelle.
`Encounter.partOf` verbindet diese Ebenen. Das dortige OP-Beispiel unterscheidet
OP-Saal, Aufwachbereich und Station. Für die erste Umsetzung werden Vor- und
Nachbereitung im OP-Kontakt zusammengefasst; ein eigener Aufwachraum folgt bei
Bedarf später. `operation` ist eine Kontaktart, keine Kontaktklasse und kein
Prozedurcode. Die Procedure bleibt eine eigene Ressource.

Die Codesysteme sind gemäß den lokal eingebundenen Paketen KDS Basis 2026.0.1 und
de.basisprofil.r4 1.5.4:

- Kontaktebene: `http://fhir.de/CodeSystem/Kontaktebene` mit
  `einrichtungskontakt`, `abteilungskontakt`, `versorgungsstellenkontakt`.
- Kontaktart: `http://fhir.de/CodeSystem/kontaktart-de`, unter anderem
  `normalstationaer`, `intensivstationaer`, `operation`, `ub`, `konsil`.
- Fachabteilung: `http://fhir.de/CodeSystem/dkgev/Fachabteilungsschluessel`.
- Orte: `http://terminology.hl7.org/CodeSystem/location-physical-type` mit
  `wa`, `ro`, `bd`. Station, Zimmer und Bett werden über Location.partOf verbunden.

Beendete Ortsaufenthalte haben `Encounter.location.status = completed`; laufende
haben `active`. Die Zeiträume der Ortsangaben entsprechen dem jeweiligen
Versorgungsstellenkontakt. Konkrete Vorgaben und die unten genannten synthetischen
Annahmen sind getrennt zu verstehen. Es wird keine vollständige klinische oder
Terminologievalidität behauptet.

## Excel vorher und nachher

Vorher leitete der Konverter zusätzliche Kontakte aus Orts- und Abteilungswechseln
in aufeinanderfolgenden Zeilen ab. Dadurch waren Kontaktart und explizite
Elternbeziehung nicht unabhängig bearbeitbar; Kindzeilen konnten Elternzeiträume
verlängern.

Im Blatt **Fall** stehen jetzt vor der Ausfüllhilfe vier zusätzliche Spalten:

| Spalte | Bedeutung |
| --- | --- |
| Kontakt-ID | Innerhalb eines Patienten und Falles eindeutige Kennung dieser Zeile |
| Kontaktebene | Einrichtungskontakt, Abteilungskontakt oder Versorgungsstellenkontakt |
| Kontaktart | Normalstationär, Intensivstationär, Operation, Untersuchung und Behandlung oder Konsil |
| Übergeordneter Kontakt | Kontakt-ID der unmittelbar übergeordneten Zeile im selben Fall |

Bei expliziter Kontaktebene erzeugt jede Zeile genau einen Encounter. Beim
Einrichtungskontakt muss Kontakt-ID gleich Fall-Nr sein; sein Elternfeld ist leer.
Alle Kindzeilen wiederholen die Fall-Nr und haben eigene Kontakt-IDs. Eltern stehen
vor ihren Kindern. Abteilungskontakte gehören zur Einrichtung, Versorgungsstellen
zur Abteilung. Die Kontaktklasse bleibt innerhalb des Falls gleich.

Start und Ende gelten ausschließlich für den Kontakt dieser Zeile. Kindzeilen
ändern Elternzeiträume nicht. Doppelte IDs, fehlende/falsche Eltern, umgekehrte
Zeiträume und Kindzeiträume außerhalb bekannter Elterngrenzen werden abgelehnt.
Ein Bett- oder Zimmerwechsel ist in dieser ersten Darstellung eine neue
Versorgungsstellenzeile. Eine zusammenhängende Abteilungsphase kann mehrere solche
Zeilen enthalten. Der Einrichtungskontakt behält seine bisherige FHIR-ID, sodass
klinische Fallreferenzen stabil bleiben. Neue Kontakt-IDs werden patienten- und
fallbezogen in zulässige FHIR-IDs überführt; Orts-IDs sind auf den konfigurierten
DIZ-Kontext und den Standortpfad bezogen.

Die Auswahllisten stehen zentral in **Codes!BE30:BE32** und **Codes!BF30:BF34**.
Ausfüllhilfen erklären die Beziehungen. Bleiben alle vier neuen Felder leer,
bleibt die bisherige Ableitung für vorhandene Vorlagenbeispiele nutzbar. Es gibt
keinen zusätzlichen Betriebsmodus oder CLI-Schalter.

## Erzeugungsregeln v1

- Der feste Seed 20260912 wird mit Versionskennung, Patienten-ID und Quellkontakt-ID
  kombiniert. Jeder Quellkontakt hat einen eigenen Zufallsstrom. Gleiche Quellen
  und Regeln ergeben dieselben Bewegungen, unabhängig von der Reihenfolge anderer
  Patienten.
- Stationäre/kurzstationäre Kontakte mit positivem, abgeschlossenem Zeitraum
  erhalten Abteilungs- und Versorgungsstellenkontakte. Andere Kontakte werden nur
  bei einer explizit zugeordneten Operation angereichert. Fehlende/offene oder
  ungeeignete Zeiträume werden im Bericht erklärt, nicht durch erfundene
  Entlassungsdaten geschlossen.
- Längere Aufenthalte können mehrere Bett-/Zimmerwechsel und Abteilungsphasen
  enthalten. Bei längeren nichtoperativen Fällen werden auch Intensivphasen und
  anschließende Rückverlegungen variiert. Die Fachabteilung ohne operative Hinweise
  beginnt vorläufig bei Innerer Medizin; Wechsel zur Geriatrie sind unkalibrierte
  Testdatenannahmen, keine aus Synthea abgeleitete klinische Aussage.
- `synthea-operative-procedures.json` enthält eine explizite Teilmenge von 13
  operativen SNOMED-Codes aus dem vorhandenen Synthea-Inventar samt Quellfundstellen
  und vorläufiger Fachabteilungszuordnung. Unbekannte Prozeduren lösen keinen
  OP-Kontakt aus. Dies ist weder vollständige OP-Erkennung noch ein OPS-Mapping.
- Operative Zeiträume erhalten zufällig 15–60 Minuten Vor- und 20–90 Minuten
  Nachbereitung, begrenzt auf den Quellkontakt. Bei einem einzelnen
  performedDateTime werden vorläufig 45 Minuten Eingriffsdauer angenommen.
  Überlappende OP-Fenster werden zu einem Kontakt zusammengefasst; die ursprünglichen
  Prozedur-IDs bleiben im Bericht zuordenbar. Bei mehreren Fachdisziplinen in einem
  zusammengefassten Fenster gewinnt vorläufig die erste.
- Nach Operationen können bei ausreichender verbleibender Aufenthaltsdauer
  Intensivphasen entstehen, gefolgt von einer Rückverlegung. Nicht jeder Fall
  erhält eine Operation, Intensivphase oder Verlegung. Die Wahrscheinlichkeiten
  sind bewusst dokumentierte Testdatenannahmen und keine Krankenhausstatistik.

Klinische Ressourcen referenzieren weiterhin ihren ursprünglichen
Einrichtungskontakt. Ihre Zeitstempel werden nicht verschoben. Auch ein in Synthea
ambulant codierter operativer Kontakt bleibt ambulant; solche klinisch auffälligen
Quellklassifikationen werden nicht stillschweigend korrigiert. Detaillierte
Verträglichkeitsregeln, eine Bettenbelegung über mehrere Patienten und eine
vollständige deutsche Demografie-/Textanpassung sind noch offen.

## Prüfung und Review

Java-Tests prüfen OP-Coding, Elternbeziehungen, Ortsstruktur und Ablehnung
ungültiger Kontaktangaben. Python-Tests prüfen reproduzierbare Varianz,
zeitliche Begrenzung, zusammenhängende Bewegungen, Erhaltung der Quellen und
gezielte OP-Erkennung. Der bestehende Rückvergleich prüft zusätzlich alle
generierten Kontakte, deren Typen, Zeiten, Klassen, Eltern und Orte gegen die
berechneten Erwartungen. Bestehende klinische Rückvergleiche bleiben aktiv.

Für den späteren menschlichen Review: Ist die Fall-Tabelle mit den expliziten
Kontakten verständlich? Lassen sich OP, Intensivphase, Rückverlegung und Ortswechsel
an den Zeilen erkennen? Sind Umfang und Verteilung der Zufallsvarianten für die
gewünschten Testfälle brauchbar?

Geprüfter Stand: 39 Java-Tests und 26 Python-Tests bestanden, Maven-Paketbau
 erfolgreich. Die Vorbereitung aller 18 vorhandenen Patienten erzeugte 58
Abteilungs- und 104 Versorgungsstellenkontakte, darunter 90 normalstationäre,
7 intensivstationäre, 5 OP-Kontakte und 2 Kontakte für Untersuchung/Behandlung.
Drei Patienten (Corinne, George, Tad) wurden vollständig durch Excel→FHIR geprüft:
43 zusätzliche Kontakte, 59 Ortsressourcen über die drei getrennten Ausgaben,
637 Diagnosen und 11.590 Messwertressourcen. Alle drei Rückvergleiche bestanden.

Die zusätzliche lokale Profilprüfung der 14 neuen Kontakte und 23 Orte des
Tad-Beispiels lieferte keine Fehler, aber Warnungen zu fehlenden Narrativen und
zur lokalen Expansion von ActEncounterCode/IMP. Sie ist deshalb kein Nachweis
vollständiger Terminologievalidierung. Die Labor-Korrektur stellt das zusätzliche
Kategoriecoding LOINC 26436-6 wieder her; sie ist separat durch einen Regressionstest
abgesichert.
