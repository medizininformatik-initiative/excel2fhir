# Validatorlaufzeit bei großen Bundles

Beim vollständigen Synthea-Fall mit 13.607 erzeugten Ressourcen dauerte die
Validierung mehrere Minuten. Ein Thread-Dump zeigte den Hauptthread in
`InstanceValidator.addMessagesReplaceExistingIfMoreSevere` →
`indexOfMatchingMessageAndLocation` → `ValidationMessage.getStrippedLocation`.
Ein anfänglicher Versuch mit vier GB Heap wurde manuell beendet; daraus folgt
nicht, dass Speicher die alleinige Ursache war.

Der Quelltext der tatsächlich eingebundenen Bibliotheken wurde geprüft:
HAPI FHIR 8.12.0 verwendet hier `org.hl7.fhir.validation` und
`org.hl7.fhir.utilities` 6.9.12. Die Zusammenführung sucht für jede neue Meldung
linear in der bestehenden Liste. Bei gleichem Meldungstext normalisiert sie die
Positionsangaben wiederholt. Viele ähnliche Meldungen an verschiedenen
Bundle-Positionen verursachen dadurch quadratischen Vergleichsaufwand.

Eine isolierte lokale Gegenprobe rief diese unveränderte Bibliotheksmethode mit
gleichem Meldungstext und unterschiedlichen Positionen auf. Alle Meldungen blieben
erhalten. Gemessene Laufzeiten (Sekunden, ein Lauf; kein allgemeiner Benchmark):

| Meldungen | Zeit |
| ---: | ---: |
| 500 | 0,033 |
| 1.000 | 0,095 |
| 2.000 | 0,366 |
| 4.000 | 1,422 |
| 8.000 | 5,684 |

Dies belegt einen konkreten Engpass, aber nicht dessen Anteil an der gesamten
Laufzeit. Die außerdem beobachteten kurzlebigen Threadpools wurden damit nicht
als Ursache erklärt. Der Gesamtworkflow protokolliert Laufzeit und Versionen und
verwendet acht GB maximale Heapgröße für große Beispiele.

Eine passende Verbesserung liegt in der Bibliothek: indizierter Vergleich nach
Meldungstext und normalisierter Position unter Erhaltung der bisherigen
Schweregrad- und Reihenfolgeregeln. Das Projekt übernimmt keine private Kopie des
Validators und schaltet keine Prüfungen oder Meldungen für einen besseren
Zeitwert ab. Ein Bibliotheksupdate muss an denselben Bundles mit Vergleich der
vollständigen Meldungen überprüft werden. Dieser Befund ist kein Profil- oder
Terminologiefehler der erzeugten Daten.

Quellstände: Maven-Central-Source-JARs für
`ca.uhn.hapi.fhir:org.hl7.fhir.validation:6.9.12` und
`ca.uhn.hapi.fhir:org.hl7.fhir.utilities:6.9.12`.
Die ausführliche lokale Messnotiz und Gegenprobe liegen im externen
Projekthandover; sie sind keine zusätzliche Produktkonfiguration.
