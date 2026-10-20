"""Named-field expectations for the complete general CarePlan sheet contract."""
from check_output_selection import event_id


def expected_plans(rows, patient):
    plans = []
    def concept(row, prefix):
        result = {}
        if row[prefix + 'text']: result['text'] = row[prefix + 'text']
        coding = {key: row[prefix + field] for key, field in
                  [('code', 'code'), ('system', 'codesystem'), ('version', 'version')] if row[prefix + field]}
        if coding: result['coding'] = [coding]
        return result
    for number, row in enumerate(rows, 1):
        if row['Patient-ID']:
            plan = {'resourceType': 'CarePlan', 'id': event_id(patient, 'CarePlan', str(number))}
            for key, field in [('title', 'Titel'), ('description', 'Beschreibung'), ('status', 'Planstatus'),
                               ('intent', 'Absicht'), ('created', 'Erstellungsdatum')]:
                if row[field]: plan[key] = row[field]
            period = {key: row[field] for key, field in [('start', 'Beginn'), ('end', 'Ende')] if row[field]}
            if period: plan['period'] = period
            category = concept(row, 'Kategorie')
            if category: plan['category'] = [category]
            plans.append(plan)
        detail = {}
        code = concept(row, 'Aktivitäts')
        if code: detail['code'] = code
        if row['Aktivitätsstatus']: detail['status'] = row['Aktivitätsstatus']
        if row['Aktivitätsstatusgrund']: detail['statusReason'] = {'text': row['Aktivitätsstatusgrund']}
        if row['Aktivitätsort']: detail['location'] = {'display': row['Aktivitätsort']}
        period = {key: row[field] for key, field in [('start', 'Aktivitätsbeginn'), ('end', 'Aktivitätsende')] if row[field]}
        if period: detail['scheduledPeriod'] = period
        if detail: plan.setdefault('activity', []).append({'detail': detail})
    return plans


def check_careplans(rows, resources, patient):
    expected = {p['id']: p for p in expected_plans(rows, patient)}
    actual = {p['id']: {k: v for k, v in p.items() if k in
              {'resourceType', 'id', 'title', 'description', 'status', 'intent', 'created', 'period', 'category', 'activity'}}
              for p in resources if p['resourceType'] == 'CarePlan'}
    assert expected == actual, {'expectedCarePlans': expected, 'actualCarePlans': actual}
