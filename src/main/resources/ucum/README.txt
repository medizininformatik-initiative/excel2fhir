The file hl7.fhir.r4.core-4.0.1\package\ValueSet-ucum-common.json is extracted from the hl7.fhir.r4.core-4.0.1.tgz file.
It contains all valid ucum codes and its display text and is used to create the file UCUM_Codes.map.

The file concepts.tsv is downloaded from http://download.hl7.de/documents/ucum/concepts.tsv from the page
http://download.hl7.de/documents/ucum/ucumdata.html.
It contains some (!) UCUM codes of all valid codes and their common synonyms. It is used to build a base
synonym map file UCUM_Synonyms_automatic.map.

The creation of this two maps UCUM_Codes.map and UCUM_Synonyms_automatic.map is done by the class UcumCodesExtractor.java.
