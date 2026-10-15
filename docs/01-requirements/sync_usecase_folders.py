"""Build a per-use-case reading view from authoritative type-based documents.
Refuses to overwrite locally edited generated files. Never reads history.
"""
from pathlib import Path
import copy, hashlib, html, json, os, re
import xml.etree.ElementTree as E
from preview_support import notice

DOCS=Path(__file__).resolve().parent
OUT=DOCS/'按用例分类'
MANIFEST=OUT/'同步清单.json'
PAGES={1:[0,1],2:[0,1],3:[0,2],4:[0,2],5:[0,3],6:[0,3],7:[0,1],8:[0,2],9:[0],10:[0],11:[0,2]}
PROCESS={1:'P2',2:'P4',3:'P4',4:'P4',5:'P6',6:'P6',7:'P1',8:'P7',9:'P1',10:'P3',11:'P5'}

def digest(data):return hashlib.sha256(data).hexdigest()
def rel(path,base):return Path(os.path.relpath(path,base)).as_posix()
def xml_pages(path,indices):
    source=E.parse(path).getroot();root=E.Element('mxfile',dict(source.attrib))
    for i in indices:root.append(copy.deepcopy(list(source)[i]))
    E.indent(root,space='  ')
    return E.tostring(root,encoding='utf-8',xml_declaration=True)

def main():
    details={int(p.name[3:5]):p for p in (DOCS/'usecases-v1').glob('UC-*.md')}
    assert set(details)==set(range(1,12))
    titles={n:p.read_text(encoding='utf-8').splitlines()[0].split(' ',2)[2] for n,p in details.items()}
    folders={n:OUT/f'UC-{n:02d}_{titles[n]}' for n in details}
    assert len(set(folders.values()))==11
    assert all(p.parent==OUT and not any(c in p.name for c in '<>:"/\\|?*') for p in folders.values())
    old=json.loads(MANIFEST.read_text(encoding='utf-8')) if MANIFEST.exists() else {'files':[]}
    oldhash={f['destination']:f['sha256'] for f in old['files']}
    writes={};records=[];local_maps={}
    def put(dest,data,source=None,kind='copy'):
        assert dest.is_relative_to(OUT)
        writes[dest]=data
        records.append(dict(destination=rel(dest,OUT),source=rel(source,DOCS) if source else None,
                            source_sha256=digest(source.read_bytes()) if source else None,
                            sha256=digest(data),kind=kind))
    def cp(source,dest):put(dest,source.read_bytes(),source)
    def rewrite(text,source,dest):
        def replace(m):
            target=m.group(1)
            if re.match(r'^(?:[A-Za-z]:|[a-z]+://|#)',target):return m.group(0)
            raw,sep,anchor=target.partition('#')
            actual=(source.parent/raw).resolve()
            mapped=next((folders[n]/p.name for n,p in details.items() if p.resolve()==actual),None)
            return ']('+rel(mapped or actual,dest.parent)+(sep+anchor if sep else '')+')'
        return re.sub(r'\]\(([^)]+)\)',replace,text)
    table=DOCS/'usecase-tables-v1/课程格式用例说明.md'
    sections={int(m.group(1)):m.group(0) for m in re.finditer(r'^## UC-(\d{2}) .*?(?=^## UC-\d{2} |\Z)',table.read_text(encoding='utf-8'),re.M|re.S)}
    usecase_diagrams=list(E.parse(DOCS/'usecases-v1/权限管理系统_用例图.drawio').getroot())
    tablexml=DOCS/'usecase-tables-v1/权限管理系统_课程格式用例说明.drawio'
    table_pages=list(E.parse(tablexml).getroot())
    for n in sorted(details):
        folder=folders[n];code=f'UC-{n:02d}';source=details[n]
        put(folder/source.name,rewrite(source.read_text(encoding='utf-8'),source,folder/source.name).encode(),source,'markdown-links')
        for dirname in ['flows-v1','sequences-v1','activities-v1']:
            for p in sorted((DOCS/dirname).iterdir()):
                if re.match(rf'^{code}(?:_|-)',p.name) and p.suffix in ('.drawio','.png','.svg'):
                    cp(p,folder/p.name)
        put(folder/'课程简表.md',rewrite(sections[n],table,folder/'课程简表.md').encode(),table,'markdown-section')
        indices=[i for i,d in enumerate(table_pages) if d.get('id')==code];assert len(indices)==1
        put(folder/'课程简表.drawio',xml_pages(tablexml,indices),tablexml,'drawio-pages')
        cp(DOCS/f'usecase-tables-v1/{code}.png',folder/'课程简表.png')
        sourcegraph=DOCS/'usecases-v1/权限管理系统_用例图.drawio'
        put(folder/'相关用例图.drawio',xml_pages(sourcegraph,PAGES[n]),sourcegraph,'drawio-pages')
        for i in PAGES[n]:
            stem=usecase_diagrams[i].get('id')
            for ext in ('.svg','.png'):
                p=DOCS/'usecases-v1'/(stem+ext)
                if p.exists():cp(p,folder/p.name)
        process=PROCESS[n]
        for p in sorted((DOCS/'dataflows-v1').glob(process+'_第一层*')):
            if p.suffix in ('.drawio','.svg','.png'):cp(p,folder/p.name)
        local=[p for p in writes if p.parent==folder]
        source_links=[f'- [{details[n].name}]({rel(details[n],folder)})']
        for dirname,label in [('flows-v1','流程图'),('sequences-v1','时序图'),('activities-v1','活动图'),('dataflows-v1','共享数据流图')]:
            source_links.append(f'- [{label}原始目录说明]({rel(DOCS/dirname/"README.md",folder)})')
        readme=[f'# {code} {titles[n]}','',
          '本目录按用例归集当前文件，便于阅读和交付。由 `docs/01-requirements/sync_usecase_folders.py` 同步生成；业务规则仍以按类型目录中的详细用例为准。','',
          notice(),'',
          '## 用例文件','',f'- [详细用例说明]({source.name})','- [课程展示简表](课程简表.md)','- [本用例图片预览](预览.html)','',
          '以下图文件可以在 draw.io 中编辑；如需修改正式设计，请修改原始文件后同步本目录，避免两份文件各自变化。','']
        readme += [f'- [{p.name}]({p.name})' for p in local if p.suffix=='.drawio']
        readme += ['',f'数据流图对应处理过程 **{process}**，可能同时服务其他用例；相关用例图保留共享图页，不把共享过程误当成本用例独占。离岗关权、账号启停、全局员工启停及自动执行子流程随对应图归集，图片按需生成。','',
          '## 共通规则与原始文件','',f'- [需求总览与统一规则]({rel(DOCS/"usecases-v1/用例说明.md",folder)})',
          f'- [管理范围与人员权限规则]({rel(DOCS/"usecases-v1/管理范围与人员权限规则.md",folder)})',
          f'- [顶层及第一层数据流分析]({rel(DOCS/"dataflows-v1/README.md",folder)})']+source_links
        put(folder/'README.md','\n'.join(readme).encode(),kind='index')
        preview=['<!doctype html><html lang="zh-CN"><meta charset="utf-8">',f'<title>{html.escape(code+" "+titles[n])}</title>',
          '<style>body{font:17px Microsoft YaHei;margin:24px;background:#f2f4f7;color:#213b58}section{padding:20px;background:white;margin:24px 0}img{max-width:100%;height:auto}</style>',f'<h1>{html.escape(code+" "+titles[n])}</h1><p>点击图片放大；正式修改请同步原始文件。</p>']
        preview.append('<aside role="note">'+html.escape(notice())+'</aside>')
        for p in local:
            if p.suffix=='.png':preview.append(f'<section><h2>{html.escape(p.stem)}</h2><a href="{html.escape(p.name)}"><img loading="lazy" src="{html.escape(p.name)}"></a></section>')
        put(folder/'预览.html','\n'.join(preview+['</html>']).encode(),kind='preview-index')
    index=['# 按用例分类的需求与设计资料','',
      '本目录是现有按类型目录的同步阅读副本，包含 11 个按“用例编号_用例名”命名的文件夹。每个文件夹归集详细说明、课程简表、相关用例图、流程图、时序图、活动图、对应第一层数据流图和预览。','',
      '共享的总体规则、顶层数据流分析、完整图及生成脚本继续保留在原目录，通过每个文件夹的 README 访问。','',
      notice(),'', '| 编号 | 用例文件夹 |','| --- | --- |']
    index += [f'| UC-{n:02d} | [{folders[n].name}]({folders[n].name}/README.md) |' for n in sorted(folders)]
    index += ['', '## 修改与同步','',
      '正式修改以原有按类型目录为准，完成修改后在项目根目录运行：','', '```powershell','python docs/01-requirements/sync_usecase_folders.py','```','',
      '同步器只读取当前有效资料，不读取或重建 history。源文件不会被修改或移动。同步清单记录来源、源文件及归集文件的 SHA-256；若归集副本被手工修改，同步器会停止，防止覆盖。请先将有用修改合并回原始文件再同步。',
      '', '详细用例中的跨用例链接已改为指向相邻用例文件夹；相关图只抽取现有共享图页，不重新生成图形布局。']
    put(OUT/'README.md','\n'.join(index).encode(),kind='index')
    # Preflight every overwrite before making any changes.
    for dest,data in writes.items():
        if dest.exists() and dest.read_bytes()!=data:
            assert oldhash.get(rel(dest,OUT))==digest(dest.read_bytes()),f'归集文件有手工改动，请先合并：{dest}'
    for dest,data in writes.items():
        if not dest.exists() or dest.read_bytes()!=data:
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(data)
    manifest_data=json.dumps(dict(folder_count=11,files=records),ensure_ascii=False,indent=2).encode('utf-8')
    if not MANIFEST.exists() or MANIFEST.read_bytes()!=manifest_data:
        MANIFEST.write_bytes(manifest_data)
    for dest,data in writes.items():assert dest.read_bytes()==data
    print(json.dumps(dict(usecases=11,files=len(writes),folder=str(OUT),source_files='unchanged'),ensure_ascii=False))

if __name__=='__main__':main()
