#!/usr/bin/env python3
"""Synthea R4 bundle -> existing Excel template. Usage: script INPUT.json OUTPUT.xlsx.
Only Patient, Encounter and Condition are imported; unhandled source fields
are identified in a separate loss report. Requires LibreOffice, Java and Python 3.
"""
import base64
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import time
from workbook_xml import read_sheets

ROOT = Path(__file__).resolve().parents[1]
SNOMED = 'http://snomed.info/sct'
CLINICAL = dict(zip(['active','recurrence','relapse','inactive','remission','resolved'],
                    ['Aktiv','Rezidiv','Rückfall','Inaktiv','Remission','Abgeklungen']))
VERIFICATION = dict(zip(['unconfirmed','provisional','differential','confirmed','refuted','entered-in-error'],
                        ['Unbestätigt','Vorläufig','Differentialdiagnose','Bestätigt','Widerlegt','Irrtümlich erfasst']))
# EMER follows the MII representation: AMB plus explicit admission reason 7.
CLASSES = {'EMER':'ambulant', 'AMB':'ambulant', 'IMP':'stationaer', 'HH':'home health', 'VR':'virtual', 'SS':'short stay', 'PRENC':'pre-admission'}


def prepare(bundle):
    entries = bundle.get('entry', [])
    if bundle.get('resourceType') != 'Bundle':
        raise ValueError('Expected a Synthea FHIR R4 Bundle')
    patients = [e['resource'] for e in entries if e.get('resource', {}).get('resourceType') == 'Patient']
    if len(patients) != 1:
        raise ValueError('Expected exactly one Patient per Synthea bundle')
    patient = patients[0]
    pid = patient['id']
    index = {}
    for e in entries:
        r = e.get('resource', {})
        if r.get('id'):
            index[r['resourceType'] + '/' + r['id']] = r
            if e.get('fullUrl'): index[e['fullUrl']] = r
    rows = {'Person':[], 'Fall':[], 'Diagnose':[]}
    losses = []
    def loss(r, path, reason):
        losses.append({'resourceType':r['resourceType'], 'id':r.get('id'), 'path':path, 'reason':reason})
    def fields(r, handled):
        # Complete top-level property accounting, with explicit nested losses below.
        for key in r.keys() - set(handled) - {'resourceType','id'}:
            loss(r, key, 'Generator unterstützt Sachverhalt noch nicht')
    def ref(r, field, expected):
        target = index.get(r.get(field, {}).get('reference'))
        if target is None or target['resourceType'] != expected:
            raise ValueError(f"Unresolved {r['resourceType']}/{r.get('id')}.{field}")
        if expected == 'Patient' and target['id'] != pid:
            raise ValueError('Cross-patient reference')
        return target
    name = patient.get('name', [{}])[0]
    born = datetime.strptime(patient['birthDate'], '%Y-%m-%d').strftime('%d.%m.%Y 00:00')
    rows['Person'].append([pid, ' '.join(name.get('given', [])), name.get('family',''), '', born,
                           {'male':'männlich','female':'weiblich','other':'divers','unknown':'unbekannt'}[patient['gender']]])
    fields(patient, {'name','birthDate','gender'})
    for key in name.keys() - {'given','family'}: loss(patient, 'name[0].'+key, 'Generator unterstützt Sachverhalt noch nicht')
    if len(patient.get('name', [])) > 1: loss(patient, 'name[1:]', 'Generator unterstützt Sachverhalt noch nicht')
    encounter_numbers = {}
    encounter_mappings = []
    for e in entries:
        r = e.get('resource', {})
        if r.get('resourceType') != 'Encounter': continue
        ref(r, 'subject', 'Patient')
        if r.get('class', {}).get('system') != 'http://terminology.hl7.org/CodeSystem/v3-ActCode':
            raise ValueError('Unsupported encounter class system')
        code = r.get('class', {}).get('code')
        if code not in CLASSES:
            raise ValueError(f'Encounter class {code} is not supported by the current Excel generator; no relabelling applied')
        nr = str(len(encounter_numbers) + 1)
        encounter_numbers[r['id']] = nr
        def local_time(value):
            return datetime.fromisoformat(value.replace('Z','+00:00')).astimezone().strftime('%d.%m.%Y %H:%M:%S') if value else ''
        period = r.get('period', {})
        rows['Fall'].append([pid,nr,local_time(period.get('start')),local_time(period.get('end')),CLASSES[code],
                             '', '', '', '', 'Notfall' if code == 'EMER' else ''])
        if code == 'EMER':
            encounter_mappings.append({'sourceId':r['id'], 'sourceClass':'EMER', 'targetClass':'AMB',
                                      'admissionReasonFourthComponent':'7',
                                      'rule':'MII KDS Basis 2026.0.1: emergency encounter'})
        fields(r, {'subject','class','period'})
        for key in r.get('class', {}).keys() - {'system','code'}:loss(r,'class.'+key,'Generator unterstützt Sachverhalt noch nicht')
        for key in period.keys() - {'start','end'}:loss(r,'period.'+key,'Generator unterstützt Sachverhalt noch nicht')
        loss(r, 'id', 'Bewusst neu vergebene Fallnummer; Zuordnung im Bericht')
        loss(r, 'period', 'Zeitpunkte erhalten; Darstellung in lokaler Zeitzone')
    source_conditions = 0
    condition_rows = {}
    for e in entries:
        r = e.get('resource', {});typ = r.get('resourceType')
        if typ in ['Patient','Encounter']: continue
        if typ != 'Condition':
            loss(r, '$', 'Bewusst nicht übernommen: erstes Diagnosepaket')
            continue
        source_conditions += 1
        condition_rows[r['id']] = len(rows['Diagnose']) + 2
        ref(r, 'subject', 'Patient')
        nr = ''
        if 'encounter' in r: nr = encounter_numbers[ref(r, 'encounter', 'Encounter')['id']]
        coding = r.get('code', {}).get('coding', [])
        if not coding: raise ValueError('Condition without coding; no empty clinical shell emitted')
        chosen = []
        for c in coding:
            if c.get('system') == SNOMED and not c.get('version'):
                selection = 'SNOMED CT (Version nicht angegeben)'
            elif c.get('system') == 'http://fhir.de/CodeSystem/bfarm/icd-10-gm' and c.get('version') in map(str,range(2009,2027)):
                selection = 'ICD-10-GM ' + c['version']
            else: raise ValueError('Unsupported coding system/version: ' + str(c))
            if not c.get('code'): raise ValueError('Source coding without code')
            chosen.append((c['code'],selection))
            for key in c.keys() - {'system','version','code'}:loss(r,'code.coding.'+key,'Bezeichner übernommen, Coding-Metadaten nicht separat darstellbar')
        if len(chosen)>2 or len({c.get('system')for c in coding}) != len(coding):
            raise ValueError('More codings than supported by the diagnosis sheet/profile')
        while len(chosen)<2:chosen.append(('',''))
        label = r.get('code',{}).get('text') or coding[0].get('display','')
        def status(field, system, mapping):
            cc=r.get(field,{})
            if not cc:return ''
            cs=cc.get('coding',[])
            if len(cs)!=1 or cs[0].get('system')!=system or cs[0].get('code')not in mapping:
                raise ValueError('Unsupported '+field)
            return mapping[cs[0]['code']]
        rows['Diagnose'].append([pid,nr,label,*chosen[0],*chosen[1],r.get('recordedDate',''),
                                r.get('onsetDateTime',''),r.get('abatementDateTime',''),
                                status('clinicalStatus','http://terminology.hl7.org/CodeSystem/condition-clinical',CLINICAL),
                                status('verificationStatus','http://terminology.hl7.org/CodeSystem/condition-ver-status',VERIFICATION),''])
        fields(r, {'subject','encounter','code','recordedDate','onsetDateTime','abatementDateTime','clinicalStatus','verificationStatus'})
        for key in r.get('code',{}).keys() - {'coding','text'}:loss(r,'code.'+key,'Generator unterstützt Sachverhalt noch nicht')
        for field in ['clinicalStatus','verificationStatus']:
            for key in r.get(field,{}).keys() - {'coding'}:loss(r,field+'.'+key,'Generator unterstützt Sachverhalt noch nicht')
            for i,c in enumerate(r.get(field,{}).get('coding',[])):
                for key in c.keys() - {'system','code'}:loss(r,f'{field}.coding[{i}].'+key,'Generator unterstützt Sachverhalt noch nicht')
        for field in ['subject','encounter']:
            for key in r.get(field,{}).keys() - {'reference'}:loss(r,field+'.'+key,'Generator unterstützt Sachverhalt noch nicht')
    if not source_conditions:raise ValueError('No diagnoses: this package must not emit an administrative shell')
    return rows, {'sourcePatient':pid,'encounterNumbers':encounter_numbers,'sourceConditions':source_conditions,
                  'importedConditions':len(rows['Diagnose']),'conditionRows':condition_rows,'losses':losses,
                  'encounterMappings':encounter_mappings,
                  'limitations':['Technical diagnosis case, not a complete clinical story',
                                 'Existing Patient converter inserts DAR for missing address',
                                 'Encounter status is derived from period; source identifiers are regenerated',
                                 'No terminology mapping or KDS conformance validation performed']}


def write_workbook(rows, output):
    template = ROOT/'FHIR_Testdatengenerator_Vorlage.xlsx'
    sheets = read_sheets(template)
    ops = []
    def op(*args):ops.append('\t'.join(map(str,args)))
    def put(sheet,cell,value):op('set',sheet,cell,base64.b64encode(str(value).encode()).decode())
    for name, cells in sheets.items():
        if name in ['Codes','Konvertierungsoptionen']:continue
        last=max([int(''.join(filter(str.isdigit,k)))for k in cells]+[2])
        hint = next((cell[:-1] for cell,value in cells.items()
                     if cell.endswith('1') and cell[:-1].isalpha() and value == 'Erklärung/Ausfüllhilfe'), None)
        if hint is None:
            raise ValueError('Missing template help boundary on sheet ' + name)
        data_end = chr(ord(hint) - 1)
        op('clear',name,f'A2:{data_end}{last}')
    for name, values in rows.items():
        if len(values)>1030:raise ValueError('Case exceeds the prepared 1030 input rows')
        for i,row in enumerate(values,2):
            for j,value in enumerate(row):put(name,chr(65+j)+str(i),value)
        # Imported codes, IDs and FHIR dates are text, never floating point values.
        if values:op('text',name,f'A2:{chr(64+len(values[0]))}{len(values)+1}')
    option_sheet='Konvertierungsoptionen'
    last=max(int(''.join(filter(str.isdigit,k)))for k in sheets[option_sheet])
    op('clear',option_sheet,f'A1:Z{last}')
    put(option_sheet,'A1','SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER = true')
    put(option_sheet,'A2','SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION = false')
    put(option_sheet,'A3','VALIDATE_STRICT = true')
    office=Path('/Applications/LibreOffice.app/Contents')
    if office.exists():soffice=office/'MacOS/soffice';jars=office/'Resources/java/*'
    else:
        executable=shutil.which('libreoffice') or shutil.which('soffice')
        if not executable:raise RuntimeError('LibreOffice is required')
        soffice=Path(executable);jars=Path('/usr/share/java/*')
    with tempfile.TemporaryDirectory(prefix='synthea-excel-') as temp:
        temp=Path(temp);commands=temp/'edits.tsv';commands.write_text('\n'.join(ops)+'\n')
        candidate=temp/'case.xlsx';shutil.copy2(template,candidate)
        with socket.socket()as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
        process=subprocess.Popen([str(soffice),'-env:UserInstallation='+ (temp/'profile').as_uri(),
                '--headless','--norestore','--nodefault',f'--accept=socket,host=localhost,port={port};urp;StarOffice.ComponentContext'],
                stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
        try:
            for attempt in range(100):
                try:
                    with socket.create_connection(('localhost',port),timeout=.2):break
                except OSError:
                    if process.poll() is not None:raise RuntimeError('LibreOffice failed to start')
                    time.sleep(.1)
            else:raise RuntimeError('LibreOffice startup timed out')
            env=dict(os.environ, CSV2FHIR_UNO_PORT=str(port))
            subprocess.run(['java','-cp',str(jars),str(ROOT/'scripts/WorkbookUno.java'),str(candidate),str(commands)],
                           check=True,timeout=90,env=env)
            output.parent.mkdir(parents=True,exist_ok=True)
            if output.exists():raise FileExistsError(output)
            shutil.copy2(candidate,output)
        finally:
            process.terminate()
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:process.kill();process.wait()


def main():
    if len(sys.argv)!=3:raise SystemExit('Usage: synthea_to_excel.py INPUT.json OUTPUT.xlsx')
    source=Path(sys.argv[1]);output=Path(sys.argv[2]);report=output.with_suffix('.loss.json')
    if output.exists() or report.exists():raise FileExistsError('Output/report already exists')
    data=source.read_bytes();rows,loss=prepare(json.loads(data))
    loss['sourceSha256']=hashlib.sha256(data).hexdigest()
    write_workbook(rows,output)
    report.write_text(json.dumps(loss,ensure_ascii=False,indent=2)+'\n')
    print(f'{output}: {len(rows["Diagnose"])} diagnoses; report: {report}')

if __name__=='__main__':main()
