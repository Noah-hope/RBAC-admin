"""Refresh SVG/PNG auxiliary previews from current drawio without changing its layout."""
from pathlib import Path
from html import escape
import json
import sys
import re
from PIL import Image, ImageDraw, ImageFont
sys.argv.append("--preserve-layout") # Render current file only; no regeneration.
import xml.etree.ElementTree as E

OUT = Path(__file__).resolve().parent

def render_png(svg_text, output):
    """Render the simple SVG primitives emitted here; this is not a Desktop export."""
    svg=E.fromstring(svg_text)
    width,height=int(svg.get('width')),int(svg.get('height'))
    im=Image.new('RGB',(width,height),'white');draw=ImageDraw.Draw(im)
    def paint(element, inherited=None):
        style=dict(inherited or {});style.update(element.attrib)
        tag=element.tag.rsplit('}',1)[-1]
        number=lambda key,default=0: float(style.get(key,default))
        def color(key):
            value=style.get(key,'none')
            if value.startswith('light-dark('):value=value[11:].split(',',1)[0].strip()
            return None if value=='none' else value
        fill,stroke=color('fill'),color('stroke');line_width=max(1,round(number('stroke-width',1)))
        if tag=='rect':
            x,y=number('x'),number('y')
            w=width if style['width']=='100%' else number('width')
            h=height if style['height']=='100%' else number('height')
            draw.rectangle((x,y,x+w,y+h),fill=fill,outline=stroke,width=line_width)
        elif tag in ('ellipse','circle'):
            x,y=number('cx'),number('cy');rx=number('r') if tag=='circle' else number('rx');ry=number('r') if tag=='circle' else number('ry')
            draw.ellipse((x-rx,y-ry,x+rx,y+ry),fill=fill,outline=stroke,width=line_width)
        elif tag=='polyline':
            points=[tuple(map(float,p.split(','))) for p in style['points'].split()]
            draw.line(points,fill=stroke,width=line_width)
        elif tag=='path':
            tokens=iter(re.findall(r'[MLHV]|-?\d+(?:\.\d+)?',style['d']))
            x=y=0
            for command in tokens:
                if command in ('M','L'):
                    target=(float(next(tokens)),float(next(tokens)))
                elif command=='H':target=(float(next(tokens)),y)
                elif command=='V':target=(x,float(next(tokens)))
                else:raise ValueError(('Unsupported preview path',command))
                if command!='M':draw.line([(x,y),target],fill=stroke,width=line_width)
                x,y=target
        elif tag=='text':
            font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',round(number('font-size',18)))
            anchor={'middle':'ms','end':'rs'}.get(style.get('text-anchor'),'ls')
            draw.text((number('x'),number('y')),element.text or '',font=font,fill=fill or '#17324d',anchor=anchor)
        for child in element:paint(child,style)
    paint(svg)
    im.save(output)
CASES = {
    'UC-01': '维护业务系统与权限目录', 'UC-02': '创建角色并配置权限',
    'UC-03': '为员工分配角色', 'UC-04': '撤销员工角色',
    'UC-05': '查询员工有效权限', 'UC-06': '校验业务访问权限',
    'UC-07': '配置管理员及管理范围', 'UC-08': '查询授权审计记录',
    'UC-09': '管理员登录与退出', 'UC-10': '导入／同步员工组织信息',
    'UC-11': '执行已批准的权限变更',
}

def make_page(name, slug, ids, actors, connections, notes):
    height = max(820, 300 + len(ids) * 118)
    nodes = [dict(id='title', kind='text', x=35, y=20, w=1030, h=42, label=name),
             dict(id='subtitle', kind='text', x=35, y=67, w=1030, h=34,
                  label='第一期 RBAC0 · 2026-10-11 · 评审草稿，管理及集成规则含待确认假设'),
             dict(id='boundary', kind='boundary', x=310, y=125, w=485,
                  h=height-235, label='集中权限管理系统')]
    for i, uc in enumerate(ids):
        nodes.append(dict(id=uc, kind='usecase', x=355, y=187+i*118, w=395, h=76,
                          label=uc+'\n'+CASES[uc]))
    for key, label, x, y in actors:
        nodes.append(dict(id=key, kind='actor', x=x, y=y, w=52, h=90, label=label))
    nodes.append(dict(id='notes', kind='note', x=35, y=height-94, w=1030, h=75,
                      label='\n'.join(notes)))
    lookup = {n['id']:n for n in nodes}
    edges=[]
    for i,(a,b) in enumerate(connections):
        actor,case=lookup[a],lookup[b]
        left=actor['x']<case['x']
        start=(actor['x']+(actor['w'] if left else 0),actor['y']+actor['h']/2)
        end=(case['x'] if left else case['x']+case['w'],case['y']+case['h']/2)
        edges.append(dict(id=f'edge{i}',source=a,target=b,points=[start,end],left=left))
    return dict(name=name,slug=slug,w=1100,h=height,nodes=nodes,edges=edges)

pages=[
make_page('01 系统总体用例图','01-overview',list(CASES),[
    ('group','集团权限管理员',95,220),('dept','部门权限管理员',95,645),
    ('biz','业务系统',950,625),('hr','HR 系统\n（一期模拟）',950,1160),
    ('oa','OA 系统\n（一期模拟）',950,1330)],
    [('group',c) for c in ['UC-01','UC-02','UC-03','UC-04','UC-07','UC-08','UC-09','UC-10']]+
    [('dept',c) for c in ['UC-03','UC-04','UC-08','UC-09']]+
    [('biz','UC-05'),('biz','UC-06'),('hr','UC-10'),('oa','UC-11')],
    ['连线表示参与关系，不表示执行顺序；普通员工是管理对象，不直接操作本平台。',
     '假设：集团配置角色；部门按范围授权及查日志；HR 导入、OA 回调均为模拟。']),
make_page('02 系统目录与角色配置','02-role-config',['UC-01','UC-02','UC-07'],[
    ('group','集团权限管理员',95,305)],
    [('group',c) for c in ['UC-01','UC-02','UC-07']],
    ['角色限定一个业务系统；配置是否授予查看、编辑等独立功能操作。',
     '本页是总体图的局部视图，不表示 UC-01 必须在每次 UC-02 中执行。']),
make_page('03 员工角色分配与撤销','03-assignment',['UC-03','UC-04','UC-11','UC-08'],[
    ('group','集团权限管理员',95,210),('dept','部门权限管理员',95,475),
    ('oa','OA 系统\n（一期模拟）',950,465)],
    [('group',c) for c in ['UC-03','UC-04','UC-08']]+
    [('dept',c) for c in ['UC-03','UC-04','UC-08']]+[('oa','UC-11')],
    ['本草稿用认证的 OA 回调执行批准方案；保存申请与实际生效是两个时刻。',
     '若改为人工执行审批结果，应同步修改 UC-03／04／11；部门不能撤销其他来源授权。']),
make_page('04 业务系统权限查询与校验','04-runtime',['UC-05','UC-06'],[
    ('biz','业务系统\n（可信服务端）',95,275)],
    [('biz','UC-05'),('biz','UC-06')],
    ['业务系统认证员工并传递统一标识；权限中心认证调用方并限定其目标系统。',
     '本系统只管理功能权限；查询菜单不能代替实际操作的功能权限校验。']),
]

# Update only connections in an already edited file; preserve every vertex and page.
existing_tree = None
if '--preserve-layout' in sys.argv:
    existing_tree = E.parse(OUT/'权限管理系统_用例图.drawio')
    updated_pages=[]
    for index, diagram in enumerate(existing_tree.getroot().findall('diagram')):
        model=diagram.find('mxGraphModel')
        cells=model.findall('./root/mxCell'); nodes=[]; edges=[]
        for cell in cells:
            if cell.get('vertex')!='1':continue
            g=cell.find('mxGeometry'); style=cell.get('style','')
            kind=('actor' if 'umlActor' in style else 'usecase' if 'ellipse' in style
                  else 'boundary' if cell.get('id')=='boundary' else 'note' if cell.get('id')=='notes' else 'text')
            nodes.append(dict(id=cell.get('id'),kind=kind,label=cell.get('value',''), style=dict(p.split('=',1) for p in style.split(';') if '=' in p),
                              **{k:float(g.get(attr,'0')) for k,attr in [('x','x'),('y','y'),('w','width'),('h','height')]}))
        lookup={n['id']:n for n in nodes}
        for cell in cells:
            if cell.get('edge')!='1':continue
            a,b=lookup[cell.get('source')],lookup[cell.get('target')]
            left=a['x']<b['x']
            style_parts=[p for p in cell.get('style','').split(';') if p and p.split('=')[0] not in
                         {'edgeStyle','noEdgeStyle','curved','rounded','orthogonalLoop','jettySize','exitX','exitY','entryX','entryY','exitDx','exitDy','entryDx','entryDy','exitPerimeter','entryPerimeter'}]
            style_parts += ['edgeStyle=none','noEdgeStyle=1','curved=0','rounded=0',f'exitX={1 if left else 0}',
                            'exitY=0.5',f'entryX={0 if left else 1}','entryY=0.5','exitPerimeter=0','entryPerimeter=0']
            # Keep the source edge unchanged.
            g=cell.find('mxGeometry')
            for child in list(g):
                if child.get('as')=='points': raise ValueError('Preview expects straight edges')
            edges.append(dict(color=dict(p.split('=',1) for p in cell.get('style','').split(';') if '=' in p).get('strokeColor','#64748b'),id=cell.get('id'),source=a['id'],target=b['id'],left=left,
                              points=[(a['x']+(a['w'] if left else 0),a['y']+a['h']/2),
                                      (b['x'] if left else b['x']+b['w'],b['y']+b['h']/2)]))
        updated_pages.append(dict(name=diagram.get('name'),slug=pages[index]['slug'],
                                  w=int(model.get('pageWidth','1100')),h=int(model.get('pageHeight','820')),nodes=nodes,edges=edges))
    pages=updated_pages

STYLES={
 'text':'text;html=0;align=left;verticalAlign=middle;whiteSpace=wrap;strokeColor=none;fillColor=none;fontSize=18;fontColor=#17324d;',
 'boundary':'rounded=0;html=0;whiteSpace=wrap;fillColor=#ffffff;strokeColor=#64748b;strokeWidth=2;verticalAlign=top;align=center;spacingTop=12;fontStyle=1;fontSize=19;container=0;',
 'usecase':'ellipse;html=0;whiteSpace=wrap;fillColor=#eef5ff;strokeColor=#32649a;strokeWidth=2;fontSize=18;fontColor=#17324d;',
 'actor':'shape=umlActor;html=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;fillColor=#ffffff;strokeColor=#17324d;strokeWidth=2;fontSize=17;fontColor=#17324d;whiteSpace=wrap;labelWidth=190;',
 'note':'rounded=0;html=0;whiteSpace=wrap;align=left;spacingLeft=15;fillColor=#fff8e8;strokeColor=#d7b76a;fontColor=#5e4b24;fontSize=15;',
}
mx=E.Element('mxfile',host='app.diagrams.net',modified='2026-10-11T00:00:00.000Z',agent='OOAD course diagram generator',version='24.7.17',type='device')
for page in pages:
    diagram=E.SubElement(mx,'diagram',id=page['slug'],name=page['name'])
    model=E.SubElement(diagram,'mxGraphModel',dx='1100',dy=str(page['h']),grid='1',gridSize='10',guides='1',tooltips='1',connect='1',arrows='1',fold='1',page='1',pageScale='1',pageWidth='1100',pageHeight=str(page['h']),math='0',shadow='0')
    root=E.SubElement(model,'root');E.SubElement(root,'mxCell',id='0');E.SubElement(root,'mxCell',id='1',parent='0')
    for n in page['nodes']:
        cell=E.SubElement(root,'mxCell',id=n['id'],value=n['label'],style=STYLES[n['kind']],vertex='1',parent='1')
        E.SubElement(cell,'mxGeometry',x=str(n['x']),y=str(n['y']),width=str(n['w']),height=str(n['h']),attrib={'as':'geometry'})
    for edge in page['edges']:
        left=edge['left']
        style=f'edgeStyle=none;noEdgeStyle=1;curved=0;html=0;rounded=0;endArrow=none;startArrow=none;strokeColor=#64748b;strokeWidth=1.4;exitX={1 if left else 0};exitY=0.5;entryX={0 if left else 1};entryY=0.5;exitPerimeter=0;entryPerimeter=0;'
        cell=E.SubElement(root,'mxCell',id=edge['id'],style=style,edge='1',parent='1',source=edge['source'],target=edge['target'])
        geo=E.SubElement(cell,'mxGeometry',relative='1',attrib={'as':'geometry'})
    svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="1100" height="{page["h"]}" viewBox="0 0 1100 {page["h"]}">',
         '<rect width="100%" height="100%" fill="white"/>',
         '<g font-family="Microsoft YaHei, Noto Sans CJK SC, sans-serif" fill="#17324d">']
    def text(n,size=18,anchor='middle',y=None):
        lines=n['label'].split('\n'); x=n['x']+n['w']/2 if anchor=='middle' else n['x']+12
        yy=y if y is not None else n['y']+n['h']/2-(len(lines)-1)*12+6
        return ''.join(f'<text x="{x}" y="{yy+i*24}" font-size="{size}" text-anchor="{anchor}">{escape(line)}</text>' for i,line in enumerate(lines))
    for n in page['nodes']:
        if n['kind']=='boundary':
            svg.append(f'<rect x="{n["x"]}" y="{n["y"]}" width="{n["w"]}" height="{n["h"]}" fill="white" stroke="#64748b" stroke-width="2"/>')
            svg.append(text(n,19,y=n['y']-12 if n.get('style',{}).get('verticalLabelPosition')=='top' else n['y']+32))
    for edge in page['edges']:
        pts=' '.join(f'{x},{y}' for x,y in edge['points'])
        svg.append(f'<polyline points="{pts}" fill="none" stroke="{edge.get("color","#64748b")}" stroke-width="1.4"/>')
    for n in page['nodes']:
        x,y,w,h=n['x'],n['y'],n['w'],n['h'];kind=n['kind']
        if kind=='usecase':
            svg.append(f'<ellipse cx="{x+w/2}" cy="{y+h/2}" rx="{w/2}" ry="{h/2}" fill="#eef5ff" stroke="#32649a" stroke-width="2"/>');svg.append(text(n))
        elif kind=='actor':
            cx=x+w/2
            svg.append(f'<g stroke="{n.get("style",{}).get("strokeColor","#17324d")}" stroke-width="2" fill="white"><circle cx="{cx}" cy="{y+12}" r="12"/><path d="M {cx} {y+24} V {y+57} M {x} {y+38} H {x+w} M {cx} {y+57} L {x} {y+h} M {cx} {y+57} L {x+w} {y+h}" fill="none"/></g>');svg.append(text(n,int(n.get('style',{}).get('fontSize','17')),y=y+h+25))
        elif kind=='text':svg.append(text(n,25 if n['id']=='title' else 15,anchor='start'))
        elif kind=='note':
            svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#fff8e8" stroke="#d7b76a"/>');svg.append(text(n,15,anchor='start'))
    svg.append('</g></svg>')
    (OUT/(page['slug']+'.svg')).write_text('\n'.join(svg),encoding='utf-8')
    render_png('\n'.join(svg),OUT/(page['slug']+'.png'))
E.indent(mx,space='  ')
dest=OUT/'权限管理系统_用例图.drawio'
if existing_tree is not None:
    pass # Drawio remains untouched; it is the authoritative source.
else:
    E.ElementTree(mx).write(dest,encoding='utf-8',xml_declaration=True)
# Validate editable graph integrity and actor placement, not application behavior.
for page in E.parse(dest).getroot().findall('diagram'):
    cells=page.findall('./mxGraphModel/root/mxCell'); ids=[c.get('id') for c in cells]
    assert len(ids)==len(set(ids))
    for c in cells:
        if c.get('edge')=='1':
            assert c.get('source') in ids and c.get('target') in ids
            assert c.find('./mxGeometry/Array[@as="points"]') is None
assert len(pages)==4
print(json.dumps({'file':str(dest),'pages':[p['name'] for p in pages],'use_cases':len(CASES),'validation':'XML and edge references valid'},ensure_ascii=False))
