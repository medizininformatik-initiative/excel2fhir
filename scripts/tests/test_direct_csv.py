import sys,unittest,tempfile,csv
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from direct_csv import write_direct_csv,quote
from workbook_records import record

class ProjectionTest(unittest.TestCase):
 def test_quotes_unicode_whitespace_and_newlines(self):
  values=['00123','äöü ß 😀','a,b','a"b','a\nb','a\rb',' edge ','']
  encoded=','.join(map(quote,values))+'\n'
  import io
  self.assertEqual(values,next(csv.reader(io.StringIO(encoded,newline=''))))
  self.assertEqual('00123',quote('00123'))
 def test_fields_missing_labels_and_hints(self):
  with tempfile.TemporaryDirectory() as temp:
   output=Path(temp)/'csv'
   row=record('Person',{'Patient-ID':'00123','Vorname':'!dar:unknown','Nachname':' Großer "Name"\nZweite Zeile','Geburtsdatum':'!dar:unknown'})
   write_direct_csv({'Person':[row]},output,'case')
   with (output/'case_Person.csv').open(newline='') as f: rows=list(csv.DictReader(f))
   self.assertEqual(rows[0]['Patient-ID'],'00123')
   self.assertEqual(rows[0]['Nachname'],row['Nachname'])
   self.assertIn('Data Absent Reason',rows[0]['Vorname'])
   self.assertIn('Data Absent Reason',rows[0]['Geburtsdatum'])
   self.assertTrue(rows[-1]['Erklärung/Ausfüllhilfe'])
   self.assertEqual(11,len(list(output.glob('*.csv'))))
 def test_extended_rows_have_no_repeated_help(self):
  with tempfile.TemporaryDirectory() as temp:
   output=Path(temp)/'csv'
   rows=[record('Diagnose',{'Patient-ID':'p','Code':'A00','Codesystem':'ICD-10-GM'}) for _ in range(1032)]
   write_direct_csv({'Diagnose':rows},output,'case')
   with (output/'case_Diagnose.csv').open(newline='') as f: actual=list(csv.DictReader(f))
   self.assertEqual(1032,len(actual))
   self.assertEqual('',actual[-1]['Erklärung/Ausfüllhilfe'])
   self.assertEqual('A00',actual[-1]['Code'])
 def test_unknown_field_rejected(self):
  with tempfile.TemporaryDirectory() as temp:
   with self.assertRaises(ValueError):
    write_direct_csv({'Person':[{'unexpected':'data'}]},Path(temp)/'csv','case')

 def test_java_whitespace_semantics(self):
  self.assertEqual('\u00a0text\u00a0', quote('\u00a0text\u00a0'))
  self.assertEqual('"\u2002text\u2002"', quote('\u2002text\u2002'))
 def test_unknown_table_rejected(self):
  with tempfile.TemporaryDirectory() as temp:
   with self.assertRaises(ValueError):write_direct_csv({'Unknown':[]},Path(temp)/'csv','case')
 def test_product_output_guard(self):
  with self.assertRaises(ValueError):
   write_direct_csv({'Medikation':[{'Präparatcodesystem':'PZN'}]},Path(__file__).resolve().parents[2] / 'forbidden-test','case')

if __name__ == '__main__':
 unittest.main()
