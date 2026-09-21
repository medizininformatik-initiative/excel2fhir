# Validation performance

Large bundles can spend substantial time combining validator messages, especially
when the same message appears at many resource locations. In the bundled
`org.hl7.fhir.validation` 6.9.12 implementation,
`InstanceValidator.addMessagesReplaceExistingIfMoreSevere` searches existing
messages linearly and repeatedly normalizes matching locations. This can produce
quadratic comparison work.

For large inputs, record bundle size, validation duration, JVM settings and
validator versions when investigating performance. A thread dump can identify
whether the message-merging path is active. Initial profile loading also requires
substantial heap; see [validation memory](fhir-packages.md#memory).

Evaluate a validator-library update against representative bundles and compare
complete message sets, severity and ordering as well as runtime. Source comparison
and FHIR validation cover different checks; see [FHIR validation](fhir-validation.md).
