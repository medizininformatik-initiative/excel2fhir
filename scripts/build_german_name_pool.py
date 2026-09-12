"""Build the checked-in name pools from pinned Faker source files, without executing them.

Usage: python3 scripts/build_german_name_pool.py DOWNLOADED_SOURCE_DIRECTORY
Directory contents: de_DE.py, de_AT.py, tr_TR.py, LICENSE.txt from SOURCE_COMMIT.
Run only for intentional dataset updates; normal imports need no network/Faker.
"""
import ast
import hashlib
import json
from pathlib import Path
import sys
import unicodedata

ROOT = Path(__file__).resolve().parents[1]
SOURCE_COMMIT = '59a6872a547248bb40cdd0f809089a605c68467f'
SOURCE_HASHES = {
    'de_AT.py':'88c64b45e679a00218f24e59d1617bfe932fd74bbdada3e125ffe35b277cf83b',
    'de_DE.py':'42e20fb0daf2ccd4210fedfc94cb6274410cc2e04a8021f5f53ad178b24ed602',
    'tr_TR.py':'1bce48d23776525fbd7793f42a5be45f338709ff68148a3af54035a88ec9d36a',
    'LICENSE.txt':'cd5a5fbc8bacca52b88158ca562ac5f7c2e9adb27371cead51aaf4e61a657928',
}


def unique_names(values):
    result = {}
    for value in values:
        name = unicodedata.normalize('NFC', ' '.join(value.split()))
        if '.' in name:continue  # Exclude abbreviated forms such as H.-Dieter.
        if not name or any(not (c.isalpha() or c in " -'’") for c in name):
            raise ValueError('Unexpected name: '+repr(value))
        result.setdefault(name.casefold(), name)
    return sorted(result.values(), key=lambda s:(s.casefold(), s))


def read_pools(source):
    tree = ast.parse(source)
    provider = next(n for n in tree.body if isinstance(n, ast.ClassDef) and n.name == 'Provider')
    return {target.id:ast.literal_eval(node.value)
            for node in provider.body if isinstance(node, ast.Assign)
            for target in node.targets if isinstance(target, ast.Name)
            and target.id in ('first_names_male','first_names_female','last_names')}


def build(source_dir):
    folder = Path(source_dir)
    for filename, digest in SOURCE_HASHES.items():
        if hashlib.sha256((folder/filename).read_bytes()).hexdigest() != digest:
            raise ValueError('Source checksum mismatch: '+filename)
    pools = {locale:read_pools((folder/(locale+'.py')).read_text()) for locale in ('de_DE','de_AT','tr_TR')}
    path = ROOT/'scripts/mappings/german-demographics.json'
    data = json.loads(path.read_text())
    supplement_path = ROOT/'scripts/mappings/german-name-supplement.json'
    supplement = json.loads(supplement_path.read_text())
    data['version'] = 'german-demographics-v2'
    for gender in ('male','female'):
        data['givenNames'][gender] = unique_names([*pools['de_DE']['first_names_'+gender], *supplement['givenNames'][gender]])
    # Unknown/other gender does not restrict a person to a tiny unisex pool.
    data['givenNames']['unknown'] = unique_names([*data['givenNames']['male'], *data['givenNames']['female'], *supplement['givenNames']['unknown']])
    data['familyNames'] = unique_names([name for locale in pools for name in pools[locale]['last_names']] + supplement['familyNames'])
    data['nameSources'] = {
        'project':'https://github.com/joke2k/faker', 'commit':SOURCE_COMMIT,
        'license':'MIT', 'licenseFile':'third-party/faker/LICENSE.txt',
        'files':[{ 'url':f'https://raw.githubusercontent.com/joke2k/faker/{SOURCE_COMMIT}/faker/providers/person/{locale}/__init__.py',
                   'sha256':SOURCE_HASHES[locale+'.py'], 'fields':['first_names_male','first_names_female','last_names'] if locale=='de_DE' else ['last_names']}
                 for locale in pools],
        'supplement':'scripts/mappings/german-name-supplement.json',
        'supplementSha256':hashlib.sha256(supplement_path.read_bytes()).hexdigest(),
        'normalization':'NFC, trim/collapse whitespace, case-insensitive deduplication; exclude dotted abbreviations; preserve spelling variants',
    }
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')
    license_path = ROOT/'third-party/faker/LICENSE.txt'
    license_path.parent.mkdir(parents=True, exist_ok=True)
    license_path.write_bytes((folder/'LICENSE.txt').read_bytes())
    return data


if __name__ == '__main__':
    if len(sys.argv) != 2:raise SystemExit(__doc__)
    data = build(sys.argv[1])
    print({k:len(v) for k,v in data['givenNames'].items()}, 'surnames', len(data['familyNames']))
