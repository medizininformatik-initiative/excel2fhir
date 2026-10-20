"""Keep source regimens independent of representative German product choices."""
from copy import deepcopy


def has_source_dosage(value):
    if isinstance(value, dict):
        return any(has_source_dosage(v) for k, v in value.items() if k not in ('sequence', 'id'))
    if isinstance(value, list):
        return any(has_source_dosage(v) for v in value)
    return value is not None and value != ''  # Zero and false are explicit facts.


def preserve_source_dosage(decision, source_dosage):
    """Keep mapped test data and preserve every supplied dosage fact in provenance."""
    if not has_source_dosage(source_dosage):
        return decision
    result = deepcopy(decision)
    result['sourceDosage'] = deepcopy(source_dosage)
    result['dosePolicyApplied'] = 'preserve-source'
    if result.get('target'):
        result['productApproximation'] = {
            'basis': (result.get('sourceDoseReview') or {}).get('note', 'Deutscher Vertreter für synthetische Testdaten.'),
            'pharmaceuticalEquivalenceAsserted': False}
    result['enrichment'] = None
    return result
