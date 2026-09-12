"""Check generated contacts and location relationships after Excel -> FHIR."""
from datetime import datetime
import hashlib
import uuid
from synthea_movements import enrich


def resource_id(patient,kind,source):
    return kind+'-'+str(uuid.UUID(bytes=hashlib.md5(f'{patient}|{kind}|{source}'.encode()).digest(),version=3))


def check_movements(source,target,report):
    movement=report.get('movements')
    if movement is None:return {'contacts':0}
    # Recompute from source and fixed rules, independently of the saved report.
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
    used_locations=set()
    norm=lambda value:datetime.fromisoformat(value.replace('Z','+00:00'))
    patient=pid.replace('_','-')
    for row in expected['contacts']:
        number,key,level,kind,parent=row[1],row[10],row[11],row[12],row[13]
        root=patient+'-E-'+number
        identifier=resource_id(patient,'Encounter',root+'|'+key)
        found=encounters[identifier]
        expected_parent=root if parent==number else resource_id(patient,'Encounter',root+'|'+parent)
        assert found['partOf']['reference']=='Encounter/'+expected_parent
        assert found['subject']['reference']==encounters[root]['subject']['reference']
        assert found['class']==encounters[root]['class']
        assert found['status']=='finished'
        codes={(c['system'],c['code'])for t in found['type']for c in t['coding']}
        assert ('http://fhir.de/CodeSystem/Kontaktebene',{'Abteilungskontakt':'abteilungskontakt','Versorgungsstellenkontakt':'versorgungsstellenkontakt'}[level])in codes
        if kind:
            assert ('http://fhir.de/CodeSystem/kontaktart-de',{'Normalstationär':'normalstationaer','Intensivstationär':'intensivstationaer','Operation':'operation','Untersuchung und Behandlung':'ub'}[kind])in codes
        assert norm(found['period']['start'])==norm(row[2])
        assert norm(found['period']['end'])==norm(row[3])
        bound=encounters[expected_parent]['period']
        assert norm(bound['start'])<=norm(row[2])<=norm(row[3])<=norm(bound['end'])
        previous=None
        actual=found.get('location',[])
        wanted=[(name,code)for name,code in zip(row[6:9],['wa','ro','bd'])if name]
        assert len(actual)==len(wanted)
        for place,(name,code)in zip(actual,wanted):
            reference=place['location']['reference'];used_locations.add(reference.removeprefix('Location/'))
            location=locations[reference.removeprefix('Location/')]
            assert location['name']==name
            assert location['physicalType']['coding'][0]['code']==code
            assert location.get('partOf',{}).get('reference')==previous
            assert place['status']=='completed'
            assert place['period']==found['period']
            previous=reference
    assert used_locations==locations.keys(),'Unexpected or missing generated locations'
    return {'contacts':len(expected['contacts']),'locations':len(locations),'operationContacts':len(expected['operationSources'])}
