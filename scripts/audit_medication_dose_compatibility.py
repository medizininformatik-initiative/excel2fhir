#!/usr/bin/env python3
"""Inventory product candidates against an existing source regimen.

Run without arguments; JSON is written to stdout. Findings assess recorded
mapping evidence, not pharmaceutical equivalence or a patient's treatment.
"""
from collections import Counter
import json
from national_medication_mapping import NationalMedicationMapping


def audit():
    mapping = NationalMedicationMapping()
    findings = []
    for entry in mapping.entries.values():
        product = entry.get('product') or {}
        findings.append({'sourceCode': entry['source']['code'], 'sourceDescriptions': entry['source']['displays'],
                         'candidatePzn': product.get('code'), 'candidateName': product.get('display'),
                         'candidateForm': product.get('doseForm'),
                         'recordedSourceStrengthMg': product.get('sourceNominalStrengthMg'),
                         'recordedTargetStrengthMg': product.get('targetNominalStrengthMg'),
                         'actionWithSourceDosage': 'retain-product' if product else 'no-product-mapping',
                         'sourceDoseReview': entry.get('sourceDoseReview'),
                         'sourceEvidence': product.get('source'),
                         'review': entry.get('review')})
    return {'catalogue': mapping.metadata, 'sourceConcepts': len(findings),
            'actions': dict(Counter(f['actionWithSourceDosage'] for f in findings)),
            'scope': 'Mappings supply test data regardless of medical plausibility; source dosage is preserved. Notes are informational, not exclusion rules.',
            'findings': findings}


if __name__ == '__main__':
    print(json.dumps(audit(), ensure_ascii=False, indent=2))
