"""Reproducible synthetic contacts around unchanged Synthea treatment times."""
from datetime import datetime, timedelta
import hashlib
import json
from pathlib import Path
import random

VERSION = 'synthea-movements-v2'
SEED = 20260912
MAPPING = Path(__file__).parent / 'mappings/synthea-operative-procedures.json'


def dt(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00')) if value else None


def enrich(bundle, facility_rows, encounter_numbers):
    mapping_bytes = MAPPING.read_bytes()
    mapping = json.loads(mapping_bytes)['procedures']
    resources = [e['resource'] for e in bundle['entry']]
    refs = {e.get('fullUrl'): e['resource'].get('id') for e in bundle['entry']}
    refs.update({r['resourceType']+'/'+r['id']: r['id'] for r in resources})
    encounters = {r['id']: r for r in resources if r['resourceType']=='Encounter'}
    operations = {}
    for r in resources:
        if r['resourceType'] != 'Procedure': continue
        coding = next((c for c in r.get('code',{}).get('coding',[])
                       if c.get('system')=='http://snomed.info/sct' and c.get('code') in mapping), None)
        if coding:
            operations.setdefault(refs.get(r.get('encounter',{}).get('reference')), []).append((r,mapping[coding['code']]))
    reverse = {number: source for source,number in encounter_numbers.items()}
    output, added, skipped, operation_sources = [], [], [], []
    for row in facility_rows:
        pid, number = row[:2]
        source_id = reverse[number]; source = encounters[source_id]
        root = row[:10] + ['']
        output.append(root)
        rng = random.Random(int.from_bytes(hashlib.sha256(f'{VERSION}|{SEED}|{pid}|{source_id}'.encode()).digest(), 'big'))
        start,end = (dt(source.get('period',{}).get(k)) for k in ('start','end'))
        source_ops = operations.get(source_id, [])
        inpatient = source.get('class',{}).get('code') in ('IMP','SS')
        if not inpatient and not source_ops: continue
        if start is None or end is None or end <= start:
            skipped.append({'sourceEncounter':source_id,'reason':'No positive bounded source period'})
            continue
        windows = []
        for procedure, rule in source_ops:
            a = dt(procedure.get('performedPeriod', {}).get('start') or procedure.get('performedDateTime'))
            if not a or a < start or a >= end:
                skipped.append({'sourceProcedure':procedure['id'], 'reason':'Operative start missing or outside source encounter'})
                continue
            # The procedure end is not an observed OP-contact end. Do not invent
            # preparation/recovery or interrupt the continuing primary stay.
            windows.append([a, procedure['id'], rule['department']])
        windows.sort(key=lambda w:w[0])
        default_department = windows[0][2] if windows else 'Innere Medizin'
        normal_kind = 'Normalstationär' if inpatient else ''
        timeline=[]
        def gap(a,b):
            if a>=b:return
            hours=(b-a).total_seconds()/3600
            cuts=[a,b]
            if hours>=12 and rng.random()<.7:
                for _ in range(rng.randint(1,min(3,max(1,int(hours//24))))):
                    cuts.append(a+(b-a)*rng.uniform(.15,.85))
            if inpatient and hours>=48 and rng.random()<.12:
                middle=(a+(b-a)*rng.uniform(.15,.4)).replace(microsecond=0)
                timeline.append([a,middle,'Intensivstationär','Intensivmedizin',[]])
                a=middle;cuts=[a]+[c for c in cuts if c>a]
            cuts=sorted(set([a,b]+[c.replace(microsecond=0) for c in cuts if a<c<b]))
            department=default_department
            for left,right in zip(cuts,cuts[1:]):
                if left>=right:continue
                if inpatient and not windows and hours>=48 and rng.random()<.25:
                    department='Geriatrie' if department=='Innere Medizin' else 'Innere Medizin'
                timeline.append([left,right,normal_kind,department,[]])
        gap(start, end)
        # Primary stays form one timeline. Secondary contacts are inserted after
        # their owning primary row, with a deliberately absent contact end.
        room=rng.randint(101,125);bed=rng.randint(1,2)
        for index,(a,b,kind,department,_) in enumerate(timeline):
            if index and rng.random()<.5: bed=3-bed
            elif index: room+=1
            prefix={'Innere Medizin':'IN','Geriatrie':'GE','Intensivmedizin':'ITS',
                    'Allgemeine Chirurgie':'CH','Herzchirurgie':'HC','Urologie':'UR',
                    'Frauenheilkunde und Geburtshilfe':'FG','Orthopädie':'OR'}.get(department,'ST')
            primary=row[:10]+[kind]
            primary[2:4]=[a.isoformat(),b.isoformat()]
            primary[5:10]=[department,'Station '+prefix+'1',f'Zimmer {room}',f'Bett {bed}','']
            output.append(primary);added.append(primary)
            for op_start,procedure_id,op_department in windows:
                if not a <= op_start < b: continue
                secondary=row[:10]+['Operation']
                secondary[2:4]=[op_start.isoformat(),'']
                secondary[5:10]=[op_department,'OP-Bereich',f'OP-Saal {rng.randint(1,6)}','','']
                output.append(secondary);added.append(secondary)
                operation_sources.append({'row':len(output)+1,'sourceProcedures':[procedure_id],
                    'contactEnd':'derived from primary stay', 'derivedEnd':b.isoformat()})
        if not timeline:skipped.append({'sourceEncounter':source_id,'reason':'No eligible timeline'})
    return output, {'version':VERSION,'seed':SEED,'operativeMappingSha256':hashlib.sha256(mapping_bytes).hexdigest(),
                    'contacts':added,'skipped':skipped,'operationSources':operation_sources,
                    'assumptions':['Synthetic movement probabilities, not calibrated hospital statistics',
                                   'Source procedure times and encounter classes remain unchanged',
                                   'OP contacts overlap primary stays; missing OP-contact end derives from primary stay end',
                                   'No clinical timestamp compatibility or cross-patient bed occupancy simulation']}
