"""Project Synthea rows to the same CSV tables as the Excel exporter."""
from pathlib import Path
from functools import lru_cache
from workbook_xml import read_sheets
from workbook_records import SCHEMAS, ordered_values
from workbook_absent import display
from synthea_to_excel import column_name

@lru_cache(maxsize=1)
def layout():
    sheets = read_sheets(Path(__file__).resolve().parents[1] / 'FHIR_Testdatengenerator_Vorlage.xlsx')
    result = {}
    for name in SCHEMAS:
        cells = sheets[name]
        headers = []
        while cells.get(column_name(len(headers) + 1) + '1'):
            headers.append(cells[column_name(len(headers) + 1) + '1'])
        if not headers or headers[-1] != 'Erklärung/Ausfüllhilfe':
            raise ValueError(f'Unexpected template columns in {name}')
        ordered_values(name, {}, headers[:-1])
        column = column_name(len(headers))
        hints = {int(key[len(column):]): value for key, value in cells.items()
                 if key.startswith(column) and key[len(column):].isdigit() and int(key[len(column):]) > 1}
        result[name] = headers, hints
    return result

JAVA_WHITESPACE = "\t\n\v\f\r\x1c\x1d\x1e\x1f \u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2008\u2009\u200a\u2028\u2029\u205f\u3000"

def quote(value):
    value = str(value)
    if any(c in value for c in ',"\n\r') or value != value.strip(JAVA_WHITESPACE):
        return '"' + value.replace('"', '""') + '"'
    return value

def write_direct_csv(rows, directory, prefix):
    unknown = rows.keys() - layout().keys()
    if unknown:
        raise ValueError(f'Unknown input tables: {sorted(unknown)}')
    if any(row.get('Präparatcodesystem') == 'PZN' for row in rows.get('Medikation', [])):
        from medication_products import require_external_path
        require_external_path(directory)
    directory.mkdir(parents=True)
    for name, (headers, hints) in layout().items():
        records = rows.get(name, [])
        with (directory / (prefix + '_' + name + '.csv')).open('w', encoding='utf-8', newline='') as output:
            output.write(','.join(map(quote, headers)) + '\n')
            for index in range(2, max(len(records) + 1, max(hints, default=1)) + 1):
                row = records[index - 2] if index - 2 < len(records) else None
                values = ([display(name, header, value, row) for header, value in
                           zip(headers[:-1], ordered_values(name, row, headers[:-1]))]
                          if row is not None else [''] * (len(headers) - 1))
                values.append(hints.get(index, ''))
                if row is not None or any(values):
                    output.write(','.join(map(quote, values)) + '\n')
