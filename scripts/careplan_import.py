"""Project a general CarePlan into one plan row and activity continuation rows."""
from workbook_records import record


def project_plan(plan, patient, encounter, loss):
    def code_fields(concept, prefix, path):
        codings = concept.get('coding', [])
        coding = codings[0] if codings else {}
        if len(codings) > 1:
            loss(path + '.coding[1:]', 'Weitere Codings nicht übernommen')
        return {prefix + 'text': concept.get('text') or coding.get('display', ''),
                prefix + 'code': coding.get('code', ''), prefix + 'codesystem': coding.get('system', ''),
                prefix + 'version': coding.get('version', '')}

    values = {'Patient-ID': patient, 'Fall-Nr': encounter, 'Titel': plan.get('title', ''),
              'Beschreibung': plan.get('description', ''), 'Planstatus': plan.get('status', ''),
              'Absicht': plan.get('intent', ''), 'Erstellungsdatum': plan.get('created', ''),
              'Beginn': plan.get('period', {}).get('start', ''), 'Ende': plan.get('period', {}).get('end', '')}
    categories = plan.get('category', [])
    selected = next((i for i, c in enumerate(categories)
                     if any(v.get('system') == 'http://snomed.info/sct' for v in c.get('coding', []))), 0)
    if categories:
        values.update(code_fields(categories[selected], 'Kategorie', f'category[{selected}]'))
        for i in range(len(categories)):
            if i != selected: loss(f'category[{i}]', 'Zusätzliche Kategorie nicht übernommen')
    rows = [record('Behandlungsplan', values)]
    activity_rows = []
    for i, activity in enumerate(plan.get('activity', [])):
        path = f'activity[{i}]'
        detail = activity.get('detail', {})
        fields = code_fields(detail.get('code', {}), 'Aktivitäts', path + '.detail.code')
        fields.update({'Aktivitätsstatus': detail.get('status', ''),
                       'Aktivitätsstatusgrund': detail.get('statusReason', {}).get('text', ''),
                       'Aktivitätsbeginn': detail.get('scheduledPeriod', {}).get('start', ''),
                       'Aktivitätsende': detail.get('scheduledPeriod', {}).get('end', ''),
                       'Aktivitätsort': detail.get('location', {}).get('display', '')})
        for key in activity.keys() - {'detail'}: loss(path + '.' + key, 'Eigenschaft nicht übernommen')
        for key in detail.keys() - {'code', 'status', 'statusReason', 'scheduledPeriod', 'location'}:
            loss(path + '.detail.' + key, 'Eigenschaft nicht übernommen')
        for parent, kept in [('statusReason', {'text'}), ('location', {'display'}), ('scheduledPeriod', {'start', 'end'})]:
            for key in detail.get(parent, {}).keys() - kept:
                loss(path + '.detail.' + parent + '.' + key, 'Eigenschaft nicht übernommen')
        if any(fields.values()): activity_rows.append(fields)
    if activity_rows:
        rows[0].update(activity_rows[0])
        rows.extend(record('Behandlungsplan', fields) for fields in activity_rows[1:])
    for key in plan.keys() - {'resourceType', 'id', 'subject', 'encounter', 'title', 'description',
                             'status', 'intent', 'created', 'period', 'category', 'activity'}:
        loss(key, 'Eigenschaft nicht übernommen')
    return rows
