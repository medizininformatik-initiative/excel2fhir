# FHIR validation package baseline

The application targets FHIR R4 (4.0.1). The package set was checked against
https://packages.fhir.org on 2026-09-12. Only stable KDS releases are selected;
2027 ballot packages are not release dependencies.

## Current KDS roots

| Module | Version |
|---|---|
| Base (person, encounter, diagnosis, procedure) | 2026.0.1 |
| Laboratory | 2026.0.3 |
| Medication | 2026.0.1 |
| Consent | 2026.0.0 |

Dependencies are resolved from the published package manifests. Wildcards select
the newest matching stable release (currently de.fhir.medication 1.0.7).
The current KDS roots explicitly require de.basisprofil.r4 1.5.4 / 1.5.x;
1.6.0 is newer but outside those requirements. Likewise, newer standalone IPS,
consent-management, SMART and terminology releases do not automatically replace
versions explicitly required by the KDS dependency graph.

## Loading and reproducibility

`src/main/resources/fhir-packages.txt` is the explicit load order. All files are
read as classpath streams, including when running the shaded JAR. A missing,
unreadable or incompatible package aborts validator initialization instead of
silently continuing with incomplete validation support.

Multiple versions required by dependencies remain available for version-qualified
canonical references. For unversioned references, package versions are loaded in
ascending order so the newest included version of each package takes precedence.
No FHIR R5 package is included in this R4 closure. R5 element extensions use the
published R4 cross-version package instead. That package also contains R5 code
systems under shared canonical URLs (e.g. Encounter.status). It therefore loads
before the R4 core package: unversioned core references must retain R4 semantics,
including the valid R4 encounter status `finished`. Version-qualified R5 resources
remain available to the cross-version extensions.

To update: query the registry, inspect root manifests, resolve all transitive
requirements, replace the package set and update the ordered manifest and table
below. Then run `mvn clean test package` and exercise validation from the packaged
JAR. Profile availability is not proof of SNOMED code validity: a matching licensed
terminology edition/service is still needed for that check.

## Resolved packages

The SHA-256 values identify the downloaded archives, not an independent signature.

| Package | Version | SHA-256 |
|---|---|---|
| de.basisprofil.r4 | 1.5.4 | `9dc5392b5471060db07cf8ace2e8c81e99e3cee1e89f0b5febad362561fb739f` |
| de.einwilligungsmanagement | 2.0.2 | `8570752e595e459dea48af6410c74f0631d3774d2b6ee18b1b7f198c2caea7f2` |
| de.fhir.medication | 1.0.7 | `eeaac78ee4be6231c9632006bf91f2f3844f6decc14a181ba80e0c6262628574` |
| de.ihe-d.terminology | 3.0.1 | `c0e90a5c42a50dbc02eceb4e6121a4b8e0cc208ab3ca6e0102d279d6a681b4b9` |
| de.medizininformatikinitiative.kerndatensatz.base | 2026.0.1 | `173eb412c3dbadf4e9a73c6722de475f39e3ef8bc0526b516a013d8cb9945c79` |
| de.medizininformatikinitiative.kerndatensatz.consent | 2026.0.0 | `3402dffbabee2788dd9dd07c16b4077ed2f7743e7464812138a09e587a24733a` |
| de.medizininformatikinitiative.kerndatensatz.laborbefund | 2026.0.3 | `bc1f943729beda83a4f3ee7b8777f995815577b35fdc55ea0442c5eab5df194d` |
| de.medizininformatikinitiative.kerndatensatz.medikation | 2026.0.1 | `af66a61db14a24b77e15ff577750063a44beb03b802c6e1f70ec8ed865868516` |
| de.medizininformatikinitiative.kerndatensatz.meta | 2026.0.0 | `0722e8040e04f14680775fc710f626fd0312ab95791772b17007fccc6e7c8fb1` |
| hl7.fhir.r4.core | 4.0.1 | `ebd7731df7d36b5b7d39d5fb6c9d77b44bb7fe5742f1a2e87f164738c3289d44` |
| hl7.fhir.uv.crmi | 2.0.0 | `c52b8c919e8740c0a6e5d8df25beb0ad7ccef2ef5d74362c7be8f101ff757d47` |
| hl7.fhir.uv.extensions.r4 | 5.2.0 | `b406e75575f05676559d0759770c5939d023ee72fb2ef38e0b3259328487720a` |
| hl7.fhir.uv.extensions.r4 | 5.3.0 | `dfbc3ac95df91ed845cc6b60920d2875f679361fa0e16f227cee93c4a9ab2104` |
| hl7.fhir.uv.ipa | 1.1.0 | `013b9f91683c496fc09eed93a8798b47dcbfb775587890fa3e11253a2409feef` |
| hl7.fhir.uv.ips | 2.0.0 | `b3964eba08ee699bc121b905c4290641e54dd34f2cf3b5cd3edeb08a40a66979` |
| hl7.fhir.uv.smart-app-launch | 2.0.0 | `627e7a64e7abe130201fbae2ee7d2cb85d7d94972458d302c4d06d59e2c7d084` |
| hl7.fhir.uv.xver-r5.r4 | 0.1.0 | `7ee6f04d78ced803dd567559a0d178bafbd2d3b71db61bbbf6b15c796a1a664d` |
| hl7.terminology.r4 | 6.2.0 | `79404c9cc95491fc0155627cd039c401a6eb4748175328131e91b709a41300e2` |
| hl7.terminology.r4 | 6.5.0 | `a28b638483a11df696ed92198276236d759e41c7a3a8960c9e7e7d0a1185bd06` |
| hl7.terminology.r4 | 7.1.0 | `1cb0cd5601972925fcd04f2c175d9cc63a9ecd9346a91a6e735f1a865dc5fba1` |
| hl7.terminology.r4 | 7.2.0 | `f8c33fd30f8daf78f7496540a841e3fd20585279c0e785fb97394e44d6f24fee` |
