UCUM mapping sources

ValueSet-ucum-common.json comes from the hl7.fhir.r4.core-4.0.1 package and
provides the common UCUM codes and display texts used by UCUM_Codes.map.

concepts.tsv comes from:
http://download.hl7.de/documents/ucum/concepts.tsv
Source page: http://download.hl7.de/documents/ucum/ucumdata.html
It supplies synonyms for a subset of UCUM codes to UCUM_Synonyms_automatic.map.

UcumCodesExtractor.java generates both maps from these inputs.
