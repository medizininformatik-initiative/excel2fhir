"""Check implicit contact hierarchy, parallel OP stays and real location values."""
from datetime import datetime
from collections import Counter
from synthea_movements import enrich


def check_movements(source,target,report):
    movement=report.get('movements')
    if movement is None:return {'contacts':0}
    from synthea_to_excel import CLASSES
    pid=report['sourcePatient'];facility=[]
    for e in source['entry']:
        r=e['resource']
        if r['resourceType']=='Encounter':
            facility.append([pid,report['encounterNumbers'][r['id']], '', '', CLASSES[r['class']['code']], '', '', '', '', ''])
    _,expected=enrich(source,facility,report['encounterNumbers'])
    assert movement==expected,'Movement report or rules changed'
    resources=[e['resource']for e in target['entry']]
    encounters={r['id']:r for r in resources if r['resourceType']=='Encounter'}
    locations={r['id']:r for r in resources if r['resourceType']=='Location'}
    norm=lambda value:datetime.fromisoformat(value.replace('Z','+00:00')) if value else None
    def level(r):
        return next((c['code'] for t in r.get('type',[]) for c in t['coding'] if c.get('system')=='http://fhir.de/CodeSystem/Kontaktebene'), None)
    kinds={'Normalstationär':'normalstationaer','Intensivstationär':'intensivstationaer','Operation':'operation','Untersuchung und Behandlung':'ub','Konsil':'konsil'}
    wanted=Counter();department_phases=0;previous_case=None;previous_department=None;primary_end=None
    for row in expected['contacts']:
        number,kind=row[1],row[10]
        secondary=kind in ('Operation','Untersuchung und Behandlung','Konsil')
        if not secondary:
            if number!=previous_case or row[5]!=previous_department: department_phases+=1
            previous_case,previous_department=number,row[5]
            primary_end=row[3]
        wanted[(number,norm(row[2]),norm(row[3] or primary_end),kinds.get(kind,''),tuple(row[6:9]))]+=1
    found=Counter();used=set()
    for encounter in encounters.values():
        if level(encounter) in (None,'einrichtungskontakt'):continue
        parent=encounters[encounter['partOf']['reference'].removeprefix('Encounter/')]
        assert encounter['subject']==parent['subject'] and encounter['class']==parent['class']
        assert norm(parent['period']['start'])<=norm(encounter['period']['start'])
        assert norm(encounter['period']['end'])<=norm(parent['period']['end'])
        if level(encounter)!='versorgungsstellenkontakt':continue
        root=parent if level(parent)=='einrichtungskontakt' else encounters[parent['partOf']['reference'].removeprefix('Encounter/')]
        number=next(n for n in report['encounterNumbers'].values() if root['id']==report.get('outputPatient', pid.replace('_','-'))+'-E-'+n)
        names={};previous=None
        for place in encounter.get('location',[]):
            reference=place['location']['reference'];used.add(reference.removeprefix('Location/'))
            location=locations[reference.removeprefix('Location/')]
            typ=location['physicalType']['coding'][0]['code'];names[typ]=location['name']
            assert location.get('partOf',{}).get('reference')==previous
            assert place['period']==encounter['period'] and place['status']=='completed'
            previous=reference
        kind=next((c['code'] for t in encounter['type'] for c in t['coding'] if c.get('system')=='http://fhir.de/CodeSystem/kontaktart-de'),'')
        found[(number,norm(encounter['period']['start']),norm(encounter['period']['end']),kind,tuple(names.get(k,'') for k in ('wa','ro','bd')))]+=1
    assert found==wanted, {'missing':list((wanted-found).items())[:2],'unexpected':list((found-wanted).items())[:2]}
    assert sum(level(e)=='abteilungskontakt' for e in encounters.values())==department_phases
    assert used==locations.keys(),'Unexpected or missing locations'
    return {'contacts':sum(wanted.values())+department_phases,'locations':len(locations),'operationContacts':len(expected['operationSources'])}
