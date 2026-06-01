# excel2fhir

07/14/2023: Repo copied from https://github.com/fmeineke/csv2fhir

## Background / History

The need for FHIR test data was and is urgent. In the POLAR_MI use case (2020-2022), medication elements were also required for the first time.
Here, clinicians were asked to **invent** data sets. For this purpose, an Excel spreadsheet was prepared from which FHIR resources were ultimately generated.
From the beginning, care was taken to create coherent, valid referenced bundles (i.e. including patient, encounter, etc.).
The system was also continuously expanded to include new resource types (e.g. DocumentReference).
The generated resources strive for MII KDS validity - but are only adjusted sporadically here.

## Working method

An ExcelFile template is filled. The java based processor generates FHIR R4 resources.

## Authors 
Early versions were developed by F. Meineke and AG A. Kiel developed.  Later versions, extensions and corrections were made by A. Strübing, with contribution F. Meineke et. al.

