"""Resolve Identifier bindings from the bundled FHIR packages for editor suggestions."""
from pathlib import Path
import tarfile,json
resources={}
for p in (Path(__file__).resolve().parents[3] / 'src/main/resources/fhir').glob('*.tgz'):
 with tarfile.open(p) as t:
  for m in t:
   if not m.name.endswith('.json') or not any(x in m.name for x in ('ValueSet-','CodeSystem-','StructureDefinition-')):continue
   try:d=json.load(t.extractfile(m))
   except Exception:continue
   if d.get('url'):resources[d['url']]=d
urls={'http://hl7.org/fhir/ValueSet/identifier-type','http://fhir.de/ValueSet/identifier-type-de-basis','http://hl7.org/fhir/ValueSet/identifier-use'}
for d in resources.values():
 if d.get('resourceType')=='StructureDefinition':
  for e in d.get('differential',{}).get('element',[]):
   if ('identifier' in e.get('path','').lower() or e.get('path','').startswith('Identifier')) and 'binding' in e:
    if e['path'].endswith(('.type','.use')):urls.add(e['binding']['valueSet'].split('|')[0])
def flatten(items):
 for c in items:
  yield c
  yield from flatten(c.get('concept',[]))
def expand(url,seen=None):
 seen=set() if seen is None else seen
 if url in seen:return []
 seen.add(url)
 d=resources.get(url)
 if not d:raise ValueError('Missing '+url)
 out=[]
 for inc in d.get('compose',{}).get('include',[]):
  for v in inc.get('valueSet',[]):out+=expand(v.split('|')[0],seen)
  if inc.get('filter'):raise ValueError('Filter '+str(inc))
  system=inc.get('system')
  if system:
   cs=resources.get(system,{})
   concepts=inc.get('concept',list(flatten(cs.get('concept',[]))))
   if not concepts:raise ValueError('Missing concepts '+system)
   for c in concepts:out.append({'system':system,'code':c['code'],'display':c.get('display',c['code'])})
 return out
codes={}
for url in sorted(urls):
 for c in expand(url):codes[c['system']+'|'+c['code']]=c
# Include pattern/fixed type codings, including pseudonym and anonymized types.
for d in resources.values():
 if d.get('resourceType') != 'StructureDefinition': continue
 for e in d.get('differential', {}).get('element', []):
  path = e.get('path', '')
  if 'identifier' not in path.lower(): continue
  candidates = []
  for prefix in ('pattern', 'fixed'):
   candidates += e.get(prefix + 'Identifier', {}).get('type', {}).get('coding', [])
   if path.endswith('.type'):
    candidates += e.get(prefix + 'CodeableConcept', {}).get('coding', [])
   if path.endswith('.type.coding') and prefix + 'Coding' in e:
    candidates.append(e[prefix + 'Coding'])
  for c in candidates:
   if c.get('system') and c.get('code'):
    display = next((v.get('display') for v in flatten(resources.get(c['system'], {}).get('concept', [])) if v['code'] == c['code']), None)
    codes.setdefault(c['system'] + '|' + c['code'], {'system': c['system'], 'code': c['code'], 'display': display or c.get('display', c['code'])})
uses=[c for c in codes.values() if c['system']=='http://hl7.org/fhir/identifier-use']
types=[c for c in codes.values() if c not in uses]
Path(__file__).with_name('identifier-bindings.json').write_text(json.dumps({'valueSets':sorted(urls),'use':uses,'types':sorted(types,key=lambda c:(c['code'],c['system']))},ensure_ascii=False,indent=2)+'\n')
print(len(types),len(uses),sorted(urls))
