"""Checks for the concise one-page context DFD."""
from pathlib import Path
import unittest
import json
import xml.etree.ElementTree as ET
OUT=Path(__file__).resolve().parent

class ContextDiagramTests(unittest.TestCase):
    def setUp(self):
        self.pages=ET.parse(OUT/'系统边界级数据流图.drawio').getroot().findall('diagram')

    def test_one_page_with_four_entities_and_one_whole_system(self):
        self.assertEqual(len(self.pages),1)
        cells=self.pages[0].findall('.//mxCell')
        self.assertEqual({c.get('id') for c in cells if c.get('kind')=='external'}, {'admin','manager','oa','business'})
        self.assertEqual([c.get('id') for c in cells if c.get('kind')=='system'], ['system'])
        self.assertFalse(any(c.get('id','').startswith('group-') for c in cells))

    def test_eight_short_data_labels_with_bidirectional_exchange(self):
        cells=self.pages[0].findall('.//mxCell')
        edges=[c for c in cells if c.get('edge')=='1']
        self.assertEqual(len(edges),8)
        for entity in ['admin','manager','oa','business']:
            self.assertIn((entity,'system'),[(c.get('source'),c.get('target')) for c in edges])
            self.assertIn(('system',entity),[(c.get('source'),c.get('target')) for c in edges])
        for edge in edges:
            self.assertNotIn('<br>',edge.get('value'))
            self.assertLess(len(edge.get('value')),45)
            self.assertIn('labelWidth=',edge.get('style'))

    def test_model_and_xml_match_and_all_usecases_are_covered(self):
        data=json.loads((OUT/'数据流说明.json').read_text(encoding='utf-8'))
        self.assertEqual(len(data['pages']),1)
        flows=data['pages'][0]['flows']
        self.assertEqual({u for f in flows for u in f['usecases']},{f'UC-{i:02d}' for i in range(1,12)})
        cells={c.get('id'):c for c in self.pages[0].findall('.//mxCell')}
        for f in flows:
            self.assertIn(f['name'],cells[f['id']].get('value'))
            self.assertEqual((f['source'],f['target']),(cells[f['id']].get('source'),cells[f['id']].get('target')))

if __name__=='__main__':unittest.main()
