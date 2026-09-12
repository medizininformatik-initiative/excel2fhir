#!/usr/bin/env python3
"""Create reviewable Excel cases and verify the supported FHIR roundtrip.
Usage: run_synthea_cases.py SYNTHEA_FHIR_DIRECTORY OUTPUT_DIRECTORY
Requires the freshly built target/excel2fhir.jar. Source files stay unchanged.
"""
import json
from pathlib import Path
import subprocess
import sys
from check_synthea_roundtrip import check
from synthea_to_excel import prepare, write_workbook
from workbook_xml import read_sheets


def run(source_dir, output_dir):
    out=Path(output_dir);out.mkdir(parents=True,exist_ok=False)
    codes=read_sheets('FHIR_Testdatengenerator_Vorlage.xlsx')['Codes']
    results=[];failures=[]
    for source in sorted(Path(source_dir).glob('*.json')):
        bundle=json.loads(source.read_text())
        if not any(e.get('resource',{}).get('resourceType')=='Patient'for e in bundle.get('entry',[])):continue
        case=out/source.stem;case.mkdir()
        try:
            rows,report=prepare(bundle)
            book=case/'Fall.xlsx';write_workbook(rows,book)
            (case/'Fall.loss.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
            with (case/'conversion.log').open('w')as log:
                subprocess.run(['java','-Xmx4g','-jar','target/excel2fhir.jar','-f',str(book),'-o',str(case/'fhir'),'-t',str(case/'csv')],
                               check=True,stdout=log,stderr=subprocess.STDOUT)
            fhir=list((case/'fhir').glob('*.json'))
            if len(fhir)!=1:raise ValueError('Expected exactly one output bundle')
            result=check(bundle,json.loads(fhir[0].read_text()),report)
            assert read_sheets(book)['Codes']==codes,'Codes sheet changed during import'
            result.update(source=str(source),workbook=str(book),rows={n:len(v)for n,v in rows.items()})
            results.append(result)
            print('PASS',len(results),source.name,flush=True)
        except Exception as ex:
            failures.append({'source':str(source),'error':repr(ex)})
            print('FAIL',source.name,repr(ex),flush=True)
        (out/'summary.json').write_text(json.dumps({'results':results,'failures':failures},ensure_ascii=False,indent=2)+'\n')
    if failures:raise SystemExit(1)


if __name__=='__main__':
    if len(sys.argv)!=3:raise SystemExit(__doc__)
    run(*sys.argv[1:])
