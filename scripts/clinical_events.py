"""Additional clinical sheets. Structured details outside these columns are reported."""
from clinical_import import coding, UnsupportedValue
import base64
import hashlib
from german_demographics import localize_document_identity
from german_texts import GermanTexts
from vaccine_mapping import map_vaccine, metadata as vaccine_metadata

COMMON = ['Patient-ID', 'Fall-Nr', 'Eintrag ID', 'Bezeichner', 'Code', 'Codesystem']
SCHEMAS = {
    'Impfung': COMMON + ['Zeitpunkt', 'Status', 'Primärquelle'],
    'Befundbericht': COMMON + ['Zeitpunkt', 'Status', 'Ausgabezeitpunkt', 'Ergebnisse', 'Beschreibung'],
    'Behandlungsplan': COMMON + ['Zeitpunkt', 'Ende', 'Status', 'Absicht', 'Beschreibung', 'Aktivitätscodes'],
}
SHEETS = dict(zip(['Immunization','DiagnosticReport','CarePlan'], SCHEMAS))


def prepare_events(entries, pid, encounters, imported_observations):
    rows = {s: [] for s in SCHEMAS}; imported, losses, vaccines = [], [], []
    texts = GermanTexts()
    index = {}
    for entry in entries:
        r = entry.get('resource', {})
        if r.get('id'):
            index[r['resourceType']+'/'+r['id']] = r
            if entry.get('fullUrl'): index[entry['fullUrl']] = r
    for entry in entries:
        r = entry.get('resource', {}); typ = r.get('resourceType')
        if typ not in SHEETS: continue
        def loss(path, reason): losses.append({'resourceType':typ,'id':r.get('id'),'path':path,'reason':reason})
        patient = r.get('patient', r.get('subject', {})).get('reference')
        if patient and (patient not in index or index[patient]['id'] != pid): raise ValueError('Broken patient reference '+patient)
        encounter = r.get('encounter', {}).get('reference')
        if encounter and (encounter not in index or index[encounter]['id'] not in encounters): raise ValueError('Broken encounter '+encounter)
        nr = encounters[index[encounter]['id']] if encounter else ''
        values = dict(zip(COMMON[:3], [pid,nr,r['id']]))
        handled = {'resourceType','id','subject','patient','encounter'}
        try:
            cc = (r.get('vaccineCode') if typ=='Immunization'
                  else next((c for c in r.get('category',[]) if any(v.get('system')=='http://snomed.info/sct' for v in c.get('coding',[]))), {}) if typ=='CarePlan' else r.get('code'))
            code, system, label = coding(cc or {})
            if len((cc or {}).get('coding',[]))>1:loss('code.coding[1:]','Erstes Coding übernommen; weitere Codings nicht dargestellt')
            values.update({'Code':code,'Codesystem':system,'Bezeichner':label})
            handled.add('vaccineCode' if typ=='Immunization' else 'category' if typ=='CarePlan' else 'code')
            for source, target in [('status','Status'),('intent','Absicht')]:
                if target in SCHEMAS[SHEETS[typ]]:
                    values[target] = r.get(source,''); handled.add(source)
            if typ=='Immunization':
                decision = map_vaccine(cc['coding'][0])
                vaccines.append({'sourceId': r['id'], **decision})
                if cc['coding'][0]['system'] == 'http://hl7.org/fhir/sid/cvx':
                    values['Bezeichner'] = texts.text(label, 'Impfung', system, code)
                    target = decision['target']
                    values['Code'] = target['code'] if target else ''
                    values['Codesystem'] = 'ATC ' + target['version'] if target else ''
                    if not target: values['Bezeichner'] += ' (Impfstoffzuordnung offen)'
                values['Zeitpunkt']=r.get('occurrenceDateTime','')
                values['Primärquelle']=str(r['primarySource']).lower() if 'primarySource'in r else ''
                handled.update(['occurrenceDateTime','primarySource'])
            elif typ=='DiagnosticReport':
                values['Zeitpunkt']=r.get('effectiveDateTime','');values['Ausgabezeitpunkt']=r.get('issued','')
                results=[]
                for ref in r.get('result',[]):
                    found=index.get(ref.get('reference'))
                    if found and found.get('id') in imported_observations:results.append(found['id'])
                    else:loss('result','Nicht übernommener oder nicht auflösbarer Ergebnisverweis ausgelassen')
                values['Ergebnisse']=';'.join(results);values['Beschreibung']=r.get('conclusion','')
                # Presented notes remain in the DocumentReference input; no
                # attachment text is relabelled as a diagnostic conclusion.
                handled.update(['effectiveDateTime','issued','result','conclusion'])
            elif typ=='CarePlan':
                values['Zeitpunkt']=r.get('period',{}).get('start','');values['Ende']=r.get('period',{}).get('end','')
                values['Beschreibung']=r.get('description','')
                activities=[]
                for activity in r.get('activity',[]):
                    ac, sy, _=coding(activity.get('detail',{}).get('code',{}))
                    if sy!='SNOMED CT (Version nicht angegeben)':raise UnsupportedValue('Aktivität ist nicht SNOMED')
                    activities.append(ac)
                values['Aktivitätscodes']=';'.join(activities)
                if activities:loss('activity','Aktivitätscodes übernommen; Detailstatus als unknown erzeugt, weitere Details fehlen')
                handled.update(['period','description'])
            rows[SHEETS[typ]].append([values.get(c,'') for c in SCHEMAS[SHEETS[typ]]])
            imported.append({'sourceId':r['id'],'resourceType':typ})
            for key in r.keys()-handled:loss(key,'Eigenschaft nicht oder nur teilweise übernommen')
        except (UnsupportedValue, KeyError) as ex:loss('$','Ressource ausgelassen: '+str(ex))
    return rows, {'clinicalImports':imported, 'losses':losses,
                  'vaccineMapping': vaccine_metadata(), 'vaccineMappings': vaccines,
                  'vaccineTexts': texts.report()}

DOCUMENT_EXTRA = ['Dokumenttext','Status','Ausgabezeitpunkt','Dokumentcode','Dokumentcodesystem','Dokumentbezeichner']
PERSON_EXTRA = ['Straße','Postleitzahl','Ort','Bundesland','Land','Sterbezeitpunkt']


def prepare_documents(entries, pid, encounters):
    rows=[];imports=[];losses=[];identity_changes=[]
    index={}
    for e in entries:
        r=e.get('resource',{})
        if r.get('id'):
            index[r['resourceType']+'/'+r['id']]=r
            if e.get('fullUrl'):index[e['fullUrl']]=r
    for e in entries:
        r=e.get('resource',{})
        if r.get('resourceType')!='DocumentReference':continue
        def loss(path,reason):losses.append({'resourceType':'DocumentReference','id':r['id'],'path':path,'reason':reason})
        patient=index.get(r.get('subject',{}).get('reference'))
        if patient is None or patient.get('id')!=pid:raise ValueError('Broken document patient reference')
        refs=r.get('context',{}).get('encounter',[])
        nr=''
        if refs:
            enc=index.get(refs[0].get('reference'))
            if enc is None or enc.get('id')not in encounters:raise ValueError('Broken document encounter reference')
            nr=encounters[enc['id']]
        try:
            attachments=r.get('content',[])
            if not attachments:raise UnsupportedValue('Dokumentinhalt fehlt')
            a=attachments[0]['attachment']
            if not a.get('contentType','').startswith('text/plain'):raise UnsupportedValue('Nur eingebetteter Klartext wird derzeit übernommen')
            text=base64.b64decode(a.get('data',''),validate=True).decode('utf-8')
            original = text
            text, replacements = localize_document_identity(text, patient)
            if not text or len(text)>32767:raise UnsupportedValue('Dokument leer oder größer als Excel-Zellgrenze')
            code,system,label=coding(r.get('type',{}))
            rows.append([pid,nr,'','ja',text,r.get('status',''),r.get('date',''),code,system,label])
            imports.append({'resourceType':'DocumentReference','sourceId':r['id']})
            identity_changes.append({'sourceId':r['id'], 'replacements':replacements,
                'sourceTextSha256':hashlib.sha256(original.encode()).hexdigest(),
                'targetTextSha256':hashlib.sha256(text.encode()).hexdigest(),
                'translation':'deferred; source language and US context remain'})
            if replacements:loss('content.attachment.data','Generierte Personennamen angepasst; keine Übersetzung des klinischen Textes')
            for k in r.keys()-{'resourceType','id','subject','status','date'}:
                loss(k,'Teilweise übernommen: erster Dokumenttyp, Klartextinhalt und erster Kontakt; weitere Metadaten fehlen')
        except (UnsupportedValue,ValueError,KeyError)as ex:loss('$','Ressource ausgelassen: '+str(ex))
    return rows,{'clinicalImports':imports,'losses':losses,'documentIdentityChanges':identity_changes}
