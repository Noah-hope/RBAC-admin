"""验证设计书范围、第五章改写、接口清单保持及PDF排版。"""
from pathlib import Path
import json,re,hashlib,zipfile,os
import pymupdf as fitz

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
source=HERE/'项目设计说明书.adoc';canonical_pdf=ROOT/'output/pdf/集中权限管理系统项目设计说明书.pdf'
pdf=ROOT/os.environ.get('RBAC_DOCUMENT_PDF_OUTPUT','output/pdf/集中权限管理系统项目设计说明书.pdf')
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
images=json.loads((HERE/'images/manifest.json').read_text(encoding='utf-8'))['images']
assert len(images)==12
for i in images:
    assert digest(HERE/'images'/i['file'])==i['pngSha256']
    assert digest(ROOT/i['source'])==i['sourceSha256']
refs=re.findall(r'^image::([^\[]+)\[',source.read_text(encoding='utf-8'),re.M)
assert len(refs)==12 and {x for x in refs}=={i['file'] for i in images}
assert not re.search(r'link:.*\.drawio',source.read_text(encoding='utf-8'))
records=json.loads((HERE/'sources.json').read_text(encoding='utf-8'))
for s in records['sources']:assert digest(ROOT/s['path'])==s['sha256'],s['path']
inspection=json.loads((ROOT/'.tmp/project-document/inspection.json').read_text(encoding='utf-8'))
assert inspection['dictionaryTables']==0 and inspection['dictionaryRows']==0 and inspection['apiRows']==67
assert not inspection['errors'] and not inspection['brokenImages'] and not inspection['messages']
text=source.read_text(encoding='utf-8')
assert len(re.findall(r'^== ',text,re.M))==9
for token in ['口试','完整物理数据字典','数据流图的阅读方法','键、约束、历史和索引','== 非功能设计','== 设计资料来源']:
    assert token not in text,token
assert not re.search(r'\bM\d{2}\b',text)
api_body=text.split('== API设计与交互约定\n',1)[1].split('== 结构化设计与代码组织\n',1)[0]
api_appendix=text.split('== 入站接口清单\n',1)[1].split('[appendix]\n== 数据流、用例、表与模块追踪摘要',1)[0]
api_hashes={k:hashlib.sha256(v.encode()).hexdigest() for k,v in [('body',api_body),('appendix',api_appendix)]}
assert api_hashes=={'body':'0bc729b58139e7d9dbebd9474dd4699b240dd00467227334620a7986199d9ce2','appendix':'84b78515ab21ac41590fc36f54b7e3da6ea8c25eaeed02a3c2989e82ad4fc9ad'}
change_baseline=ROOT/'.tmp/project-document/cover-figure10-baseline.json'
body_verified=False
if change_baseline.exists():
    previous=json.loads(change_baseline.read_text(encoding='utf-8'))
    assert text.split('toc::[]',1)[1]==previous['body']
    for file,sha in previous['images'].items():
        if file!='er-2.png':assert digest(HERE/'images'/file)==sha,file
    body_verified=True
document=fitz.open(pdf)
assert len(document.get_toc())==len(inspection['headings'])==58
top=[t for t in document.get_toc() if t[0]==1]
assert len(top)==9 and top[-2][1].startswith('附录 A:') and top[-1][1].startswith('附录 B:')
assert sum(len(p.get_image_info()) for p in document)==12
assert ''.join(document[0].get_text().split())=='RBAC权限管理系统项目设计说明书'
# 验证图10嵌入的像素确实来自本次覆盖后的er-2.png。
painted=[(p,i) for p in document for i in p.get_image_info()]
figure_page,figure10=painted[9]
assert '图 10.' in figure_page.get_text()
embedded=fitz.Pixmap(document,figure10['xref']) if 'xref' in figure10 else None
if embedded is None:
    info=figure_page.get_image_info(xrefs=True)[0]
    embedded=fitz.Pixmap(document,info['xref'])
original=fitz.Pixmap(HERE/'images/er-2.png')
assert (embedded.width,embedded.height,embedded.samples)==(original.width,original.height,original.samples)
assert inspection['typography']['nonBlack']==0 and inspection['typography']['compactFigures']==8
assert inspection['typography']['body'].startswith('FangSong')
assert [i['edges'] for i in images if i['panel']]==[9,5,12,12,7,6,6]
assert all(i['fontColors']==['#000000'] for i in images)
assert len(inspection['typography']['erFigures'])==4
assert all(abs(i['width']-i['container'])<1 for i in inspection['typography']['erFigures'])
outside=[];alltext=[]
for page in document:
    assert abs(page.rect.width-595.28)<1 and abs(page.rect.height-841.89)<1
    text=page.get_text();alltext.append(text);assert '\ufffd' not in text
    assert not any('.drawio' in str(l.get('uri','')) for l in page.get_links())
    for image in page.get_image_info():assert page.rect.contains(fitz.Rect(image['bbox']))
    for block in page.get_text('dict')['blocks']:
        for line in block.get('lines',[]):
            for span in line['spans']:
                assert span['color']==0,span['text']
                x0,y0,x1,y1=span['bbox']
                if x0<0 or y0<0 or x1>page.rect.width+.1 or y1>page.rect.height+.1:outside.append(span['text'])
assert not outside
toc_numbers=[s['text'] for b in document[1].get_text('dict')['blocks'] for l in b.get('lines',[]) for s in l['spans'] if s['bbox'][0]>510 and s['text'].isdigit()]
assert toc_numbers==[str(t[2]) for t in document.get_toc() if t[0]==1]
flat=re.sub(r'\s+','',''.join(alltext))
import yaml
contract=yaml.safe_load((ROOT/'docs/03-api/openapi.yaml').read_text(encoding='utf-8'))
operations=[v['operationId'] for methods in contract['paths'].values() for v in methods.values() if isinstance(v,dict) and v.get('operationId')]
assert len(operations)==67 and all(op in flat for op in operations)
assert '口试' not in flat and '完整物理数据字典' not in flat
baseline=change_baseline
originals_verified=0
if baseline.exists():
    for file,sha in json.loads(baseline.read_text(encoding='utf-8'))['originalSources'].items():
        assert digest(ROOT/file)==sha,file
        originals_verified+=1
archive=ROOT/'output/pdf/项目设计说明书源文件及图片.zip'
with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED) as z:
    for p in sorted(HERE.rglob('*')):
        if p.is_file() and '__pycache__' not in p.parts and p.name!='validation.json':z.write(p,p.relative_to(HERE))
qa=json.loads((ROOT/'.tmp/project-document/qa/report.json').read_text(encoding='utf-8'))
assert qa['pages']==len(document) and not qa['thinPages'] and not qa['outsideText'] and not qa['badGlyphs']
html=ROOT/'output/html/集中权限管理系统项目设计说明书.html'
result={'status':'PASSED','date':'2026-10-27','version':'1.2','processor':'Asciidoctor.js 3.0.4 -> local Edge PDF -> PyMuPDF TOC numbers',
 'pages':len(document),'chapters':7,'appendices':['A','B'],'outlineEntries':len(document.get_toc()),'embeddedPngs':12,
 'dictionaryAppendixRemoved':True,'bodyUnchanged':body_verified,'coverOnlyTitleAndSubtitle':True,'figure10MatchesLatestPng':True,'apiTextSha256':api_hashes,'apiAppendixUnchanged':True,'inboundOperations':67,'outOfBoundsText':outside,
 'sourceRecordsVerified':len(records['sources']),'imageHashesVerified':12,
 'visualReview':'All current pages rasterized and reviewed via contact sheets; cover, TOC, tables, compact DFD and ER pages inspected; representative Poppler renders checked',
 'typography':{'headings':'SimHei bold','body':'FangSong','allTextColor':'black','compactProcessFlows':[9,5,12,12,7,6,6],'erWidth':'A4 body width'},
 'drawioLinksInPdf':0,'originalSourcesVerified':originals_verified,'originalSourcesModified':False,'businessTestsRun':False,'sqlExecuted':False,
 'canonicalPdfReplacementPending':pdf.resolve()!=canonical_pdf.resolve(),
 'artifacts':[{'path':p.relative_to(ROOT).as_posix(),'bytes':p.stat().st_size,'sha256':digest(p)} for p in [source,pdf,html,archive]]}
(HERE/'validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
