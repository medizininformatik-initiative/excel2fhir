"""Reproducible synthetic contacts around unchanged Synthea treatment times."""
from datetime import datetime, timedelta
import hashlib
import json
from pathlib import Path
import random

VERSION = 'synthea-movements-v1'
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
        root = row[:10] + [number, 'Einrichtungskontakt', '', '']
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
            period = procedure.get('performedPeriod',{})
            a = dt(period.get('start') or procedure.get('performedDateTime'))
            b = dt(period.get('end')) or (a + timedelta(minutes=45) if a else None)
            if not a or b <= a or a >= end or b <= start:
                skipped.append({'sourceProcedure':procedure['id'],'reason':'Operative time missing or outside source encounter'})
                continue
            windows.append([max(start,a-timedelta(minutes=rng.randint(15,60))),
                            min(end,b+timedelta(minutes=rng.randint(20,90))),[procedure['id']],rule['department']])
        windows.sort(key=lambda w:w[0])
        merged=[]
        for window in windows:
            if merged and window[0]<=merged[-1][1]:
                merged[-1][1]=max(merged[-1][1],window[1]);merged[-1][2].extend(window[2])
            else:merged.append(window)
        default_department = merged[0][3] if merged else 'Innere Medizin'
        normal_kind = 'Normalstationär' if inpatient else 'Untersuchung und Behandlung'
        timeline=[]
        def gap(a,b,after_operation=False):
            if a>=b:return
            hours=(b-a).total_seconds()/3600
            cuts=[a,b]
            if hours>=12 and rng.random()<.7:
                for _ in range(rng.randint(1,min(3,max(1,int(hours//24))))):
                    cuts.append(a+(b-a)*rng.uniform(.15,.85))
            if inpatient and hours>=8 and ((after_operation and rng.random()<.45)
                    or (not merged and hours>=48 and rng.random()<.12)):
                middle=(a+(b-a)*rng.uniform(.15,.4)).replace(microsecond=0)
                timeline.append([a,middle,'Intensivstationär','Intensivmedizin',[]])
                a=middle;cuts=[a]+[c for c in cuts if c>a]
            cuts=sorted(set([a,b]+[c.replace(microsecond=0) for c in cuts if a<c<b]))
            department=default_department
            for left,right in zip(cuts,cuts[1:]):
                if left>=right:continue
                if inpatient and not merged and hours>=48 and rng.random()<.25:
                    department='Geriatrie' if department=='Innere Medizin' else 'Innere Medizin'
                timeline.append([left,right,normal_kind,department,[]])
        cursor=start
        for a,b,ids,department in merged:
            gap(cursor,a,bool(timeline and timeline[-1][2]=='Operation'))
            timeline.append([a,b,'Operation',department,ids]);cursor=b
        gap(cursor,end,bool(merged))
        # Department contacts cover contiguous care-unit segments in that department.
        groups=[]
        for segment in timeline:
            if groups and groups[-1][-1][3]==segment[3]:groups[-1].append(segment)
            else:groups.append([segment])
        care_index=0
        for department_index,group in enumerate(groups,1):
            department_key=f'{number}-A{department_index}'
            department=group[0][3]
            parent=row[:10];parent[2:4]=[group[0][0].isoformat(),group[-1][1].isoformat()]
            parent[5:10]=[department,'','','','']
            parent += [department_key,'Abteilungskontakt','',number]
            output.append(parent);added.append(parent)
            room=rng.randint(101,125);bed=rng.randint(1,2)
            prefix={'Innere Medizin':'IN','Geriatrie':'GE','Intensivmedizin':'ITS',
                    'Allgemeine Chirurgie':'CH','Herzchirurgie':'HC','Urologie':'UR',
                    'Frauenheilkunde und Geburtshilfe':'FG','Orthopädie':'OR'}.get(department,'ST')
            ward=f'Station {prefix}{rng.randint(1,3)}'
            for a,b,kind,_,ids in group:
                care_index+=1
                if kind=='Operation':location=['OP-Bereich',f'OP-Saal {rng.randint(1,6)}','']
                else:
                    if rng.random()<.5:bed=3-bed
                    else:room+=1
                    location=[ward,f'Zimmer {room}',f'Bett {bed}']
                child=row[:10];child[2:4]=[a.isoformat(),b.isoformat()]
                child[5:10]=[department,*location,'']
                child += [f'{number}-V{care_index}','Versorgungsstellenkontakt',kind,department_key]
                output.append(child);added.append(child)
                if ids:operation_sources.append({'contactId':child[10],'sourceProcedures':ids})
        if not timeline:skipped.append({'sourceEncounter':source_id,'reason':'No eligible timeline'})
    return output, {'version':VERSION,'seed':SEED,'operativeMappingSha256':hashlib.sha256(mapping_bytes).hexdigest(),
                    'contacts':added,'skipped':skipped,'operationSources':operation_sources,
                    'assumptions':['Synthetic movement probabilities, not calibrated hospital statistics',
                                   'Source treatment times and encounter classes remain unchanged',
                                   'No clinical timestamp compatibility or cross-patient bed occupancy simulation']}
