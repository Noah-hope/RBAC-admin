"""需求配套同步及交接静态校验；不连接数据库，不执行SQL。"""
from pathlib import Path
import argparse
import ast
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent.parent


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline',default='2b78430')
    args=parser.parse_args()
    req=REPO/'docs/01-requirements';folder=req/'按用例分类'
    media=sorted(p for p in req.rglob('*') if p.suffix.lower() in ('.png','.svg'))
    queries=''.join(args.baseline+':'+p.relative_to(REPO).as_posix()+'\n' for p in media).encode('utf-8')
    raw=subprocess.check_output(['git','cat-file','--batch'],cwd=REPO,input=queries)
    offset=0
    for p in media:
        end=raw.index(b'\n',offset);header=raw[offset:end].split()
        assert header[1]==b'blob',(p,header)
        size=int(header[2])
        assert raw[end+1:end+1+size]==p.read_bytes(),str(p)+' changed media'
        offset=end+1+size+1
    manifest=json.loads((folder/'同步清单.json').read_text(encoding='utf-8'))
    for row in manifest['files']:
        if row['source'] is not None:
            assert hashlib.sha256((req/row['source']).read_bytes()).hexdigest()==row['source_sha256'],row['source']
        assert hashlib.sha256((folder/row['destination']).read_bytes()).hexdigest()==row['sha256'],row['destination']
    xmls=list(req.rglob('*.drawio'));pages=0
    for p in xmls:
        for d in ET.parse(p).getroot().findall('diagram'):
            pages+=1;cells=d.findall('.//mxCell');ids={c.get('id') for c in cells}
            assert len(ids)==len(cells),str(p)+' duplicate ids'
            for c in cells:
                if c.get('edge')!='1':continue
                if c.get('source') is not None or c.get('target') is not None:
                    assert c.get('source')in ids and c.get('target')in ids,(p,c.get('id'))
                else:
                    # 原图包含有意不绑定实体的生命线/分隔线，需有两端坐标。
                    geom=c.find('mxGeometry')
                    assert geom is not None and geom.find("mxPoint[@as='sourcePoint']") is not None and geom.find("mxPoint[@as='targetPoint']") is not None,(p,c.get('id'))
    scripts=list(req.rglob('*.py'))+list(ROOT.glob('*.py'))
    for p in scripts:ast.parse(p.read_text(encoding='utf-8-sig'))
    ids=re.findall(r'^\| (D-\d+) \|',(REPO/'docs/project/DECISIONS.md').read_text(encoding='utf-8'),re.M)
    assert len(ids)==len(set(ids))
    memory=(REPO/'docs/project/MEMORY.md').read_text(encoding='utf-8')
    assert not re.search(r'[A-Za-z]:[\\/]',memory)
    report={'baseline':args.baseline,'scope':'STATIC_HANDOFF_ONLY',
            'unchangedRequirementMedia':len(media),'syncedReadingFiles':len(manifest['files']),
            'requirementDrawioFiles':len(xmls),'requirementDiagramPages':pages,'decisionIds':len(ids),
            'pythonSyntaxFiles':len(scripts),'memoryAbsolutePaths':'NONE','businessDatabaseStarted':False,'sqlExecuted':False}
    (ROOT/'handoff-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
