"""Create UC-01 as editable draw.io XML using the official drawio skill workflow.
The PNG is an auxiliary local rendering, not a draw.io Desktop export.
"""
import sys
GENERATE_PREVIEWS = '--previews' in sys.argv

from pathlib import Path
import html, json, xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

OUT=Path(__file__).resolve().parent
FILE=OUT/'UC-01_维护业务系统与权限目录_流程图.drawio'
WIDTH,HEIGHT=1280,2260
nodes=[];edges=[]

def node(id,label,x,y,w=360,h=70,kind='action',tone='blue'):
    nodes.append(dict(id=id,label=label,x=x,y=y,w=w,h=h,kind=kind,tone=tone))
def edge(a,b,label='',points=None):
    edges.append(dict(a=a,b=b,label=label,points=points))

node('title','UC-01 维护业务系统与权限目录',40,20,1200,50,'text')
node('subtitle','最终需求基线 · 2026-10-15 · 发起者：权限中心总管理员 · 不经过 OA',40,78,1200,35,'text')
node('start','开始',445,140,190,45,'terminal')
node('open','已登录管理员打开目录维护',360,225)
node('identity','会话及总管理员资格有效？',390,345,300,110,'decision')
node('deny','拒绝维护\n不读取或修改目录',55,355,240,85,tone='red')
node('deny-end','本次办理结束',85,485,180,45,'terminal','red')
node('select','选择已有系统或登记新系统',360,510)
node('edit','维护本次需要变更的内容\n系统、导航／功能菜单、操作、可见条件\n功能归属、启停状态',360,625,360,110)
node('valid','本次内容预检通过？',390,790,300,110,'decision')
node('correct','显示问题字段及原因\n修改本次输入',55,800,240,85,tone='amber')
node('preview','展示新增、修改、启停及引用影响\n管理员填写修改原因',360,965,360,85)
node('confirm','管理员确认保存？',390,1110,300,110,'decision')
node('cancel','取消本次未提交变更\n原目录保持不变',55,1120,240,85,tone='amber')
node('cancel-end','本次办理结束',85,1260,180,45,'terminal','amber')
node('recheck','提交时重新检查\n管理员资格、版本和目录规则',360,1280,360,85)
node('fresh','提交时复核通过？',390,1420,300,110,'decision')
node('reject','拒绝本次保存，目录不变\n提示资格失效、版本冲突或规则错误',55,1420,240,100,tone='red')
node('reject-end','本次保存结束',85,1600,180,45,'terminal','red')
node('save','整体保存目录变更、版本和成功审计',360,1585,360,80)
node('saved','本次保存全部成功？',390,1720,300,110,'decision')
node('rollback','本次事务整体回滚\n报告保存失败，不显示成功',55,1730,240,90,tone='red')
node('failure-end','本次保存结束',85,1890,180,45,'terminal','red')
node('result','显示保存结果\n区分已启用与仍停用的对象',360,1895,360,85,tone='green')
node('end','完成',445,2045,190,45,'terminal','green')
node('rule-a','目录校验\n编码唯一、字段完整、同系统归属\n父子无循环，禁止跨系统迁移\n已引用目录不直接物理删除',815,580,410,155,'note','amber')
node('rule-b','权限与菜单校验\n操作必须属于本系统有效功能\n角色只配置是否授予\n启用功能菜单须绑定本功能\n有效的查看／进入页面权限',815,795,410,180,'note','amber')
node('rule-c','影响预览\n停用导航目录会影响有效子树\n重新启用只恢复尚有效的关联\n已撤销／已失效授权不恢复',815,1020,410,145,'note','amber')
node('rule-d','提交与失败处理\n版本冲突：重新读取、核对后办理\n资格失效：停止办理\n其他规则错误：修改后重新办理\n失败不得产生部分目录或半完成绑定',815,1405,410,175,'note','amber')
node('rule-e','目录维护与授权分开\n新系统有唯一 SYSTEM_ACCESS\n不绑定菜单、不自动授予功能操作\n新增目录不自动授予角色或员工\n角色授权另走 UC-02／03 与 OA',815,1835,410,175,'note','amber')
node('footer','说明：一次保存可只维护部分目录，不要求一次录入全部系统；新功能可先停用保存，合法绑定后再启用。',40,2160,1190,55,'text')

main=['start','open','identity','select','edit','valid','preview','confirm','recheck','fresh','save','saved','result','end']
for a,b in zip(main,main[1:]):
    edge(a,b,'是' if a in ('identity','valid','confirm','fresh','saved') else '')
edge('identity','deny','否');edge('deny','deny-end')
edge('valid','correct','否');edge('correct','edit','修改后')
edge('confirm','cancel','否');edge('cancel','cancel-end')
edge('fresh','reject','否');edge('reject','reject-end')
edge('saved','rollback','否');edge('rollback','failure-end')

mx=ET.Element('mxfile',host='app.diagrams.net',agent='drawio official skill XML workflow',modified='2026-10-15T00:00:00.000Z')
page=ET.SubElement(mx,'diagram',id='uc01-directory',name='UC-01 目录维护流程')
model=ET.SubElement(page,'mxGraphModel',dx=str(WIDTH),dy=str(HEIGHT),grid='1',gridSize='10',page='1',pageWidth=str(WIDTH),pageHeight=str(HEIGHT),adaptiveColors='auto',math='0',shadow='0')
r=ET.SubElement(model,'root');ET.SubElement(r,'mxCell',id='0');ET.SubElement(r,'mxCell',id='1',parent='0')
colors={'blue':('#eef5ff','#32649a'),'amber':('#fff8e8','#bc9036'),'red':('#fff1f0','#b9534e'),'green':('#ecf7ef','#44855e')}
for n in nodes:
    fill,stroke=colors[n['tone']]
    style=f'html=1;whiteSpace=wrap;fontFamily=Microsoft YaHei;fontSize=17;fontColor=#1e3449;align=center;verticalAlign=middle;spacing=10;fillColor={fill};strokeColor={stroke};strokeWidth=1.5;'
    if n['kind']=='decision':style='rhombus;'+style
    elif n['kind']=='terminal':style='ellipse;'+style
    elif n['kind']=='text':style='text;'+style+'fillColor=none;strokeColor=none;align=left;fontSize='+('28' if n['id']=='title' else '16')+';'
    elif n['kind']=='note':style='rounded=0;'+style+'align=left;fontSize=16;'
    else:style='rounded=1;arcSize=12;'+style
    c=ET.SubElement(r,'mxCell',id=n['id'],value=html.escape(n['label']).replace('\n','<br>'),style=style,vertex='1',parent='1')
    ET.SubElement(c,'mxGeometry',x=str(n['x']),y=str(n['y']),width=str(n['w']),height=str(n['h']),attrib={'as':'geometry'})
for i,e in enumerate(edges):
    c=ET.SubElement(r,'mxCell',id=f'e{i}',source=e['a'],target=e['b'],value=e['label'],edge='1',parent='1',style='edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=block;endFill=1;strokeColor=#53667b;strokeWidth=1.6;fontSize=15;fontFamily=Microsoft YaHei;labelBackgroundColor=#ffffff;')
    ET.SubElement(c,'mxGeometry',relative='1',attrib={'as':'geometry'})
ET.indent(mx,space='  ');ET.ElementTree(mx).write(FILE,encoding='utf-8',xml_declaration=True)

# Preview: same nodes, explicit orthogonal paths corresponding to the graph.
lookup={n['id']:n for n in nodes}
im=Image.new('RGB',(WIDTH,HEIGHT),'white');d=ImageDraw.Draw(im)
fontpath='C:/Windows/Fonts/msyh.ttc'
def wrap(s,font,width):
    out=[]
    for paragraph in s.split('\n'):
        line=''
        for c in paragraph:
            if font.getlength(line+c)>width and line:out.append(line);line=c
            else:line+=c
        out.append(line)
    return out
for e in edges:
    a,b=lookup[e['a']],lookup[e['b']]
    if e['a']=='correct':
        pts=[(a['x']+a['w']/2,a['y']),(a['x']+a['w']/2,b['y']+b['h']/2),(b['x'],b['y']+b['h']/2)]
    elif a['x']+a['w']/2==b['x']+b['w']/2:
        pts=[(a['x']+a['w']/2,a['y']+a['h']),(b['x']+b['w']/2,b['y'])]
    else:
        pts=[(a['x'],a['y']+a['h']/2),(b['x']+b['w'],a['y']+a['h']/2),(b['x']+b['w'],b['y']+b['h']/2)]
        if pts[-1]==pts[-2]:pts.pop()
    d.line(pts,fill='#53667b',width=2)
    (px,py),(qx,qy)=pts[-2:]
    if qx>px:arrow=[(qx,qy),(qx-10,qy-5),(qx-10,qy+5)]
    elif qx<px:arrow=[(qx,qy),(qx+10,qy-5),(qx+10,qy+5)]
    elif qy>py:arrow=[(qx,qy),(qx-5,qy-10),(qx+5,qy-10)]
    else:arrow=[(qx,qy),(qx-5,qy+10),(qx+5,qy+10)]
    d.polygon(arrow,fill='#53667b')
    if e['label']:
        f=ImageFont.truetype(fontpath,16)
        x=(pts[0][0]+pts[1][0])/2+8;y=(pts[0][1]+pts[1][1])/2-21
        d.rectangle((x-2,y-2,x+f.getlength(e['label'])+4,y+23),fill='white');d.text((x,y),e['label'],font=f,fill='#53667b')
for n in nodes:
    x,y,w,h=n['x'],n['y'],n['w'],n['h'];fill,stroke=colors[n['tone']]
    if n['kind']=='decision':d.polygon([(x+w/2,y),(x+w,y+h/2),(x+w/2,y+h),(x,y+h/2)],fill=fill,outline=stroke,width=2)
    elif n['kind']=='terminal':d.ellipse((x,y,x+w,y+h),fill=fill,outline=stroke,width=2)
    elif n['kind']=='action':d.rounded_rectangle((x,y,x+w,y+h),radius=10,fill=fill,outline=stroke,width=2)
    elif n['kind']=='note':d.rectangle((x,y,x+w,y+h),fill=fill,outline=stroke,width=1)
    size=28 if n['id']=='title' else 16 if n['kind'] in ('text','note') else 17
    f=ImageFont.truetype(fontpath,size)
    lines=wrap(n['label'],f,w-32 if n['kind']!='decision' else w-64)
    lineheight=size+8;top=y+(h-len(lines)*lineheight)/2
    assert len(lines)*lineheight<=h,(n['id'],'text overflow')
    for i,line in enumerate(lines):
        xx=x+16 if n['kind'] in ('text','note') else x+(w-f.getlength(line))/2
        d.text((xx,top+i*lineheight),line,font=f,fill='#1e3449')
if GENERATE_PREVIEWS: im.save(OUT/'UC-01_流程图预览.png')
assert len({n['id'] for n in nodes})==len(nodes)
assert all(e['a'] in lookup and e['b'] in lookup for e in edges)
ET.parse(FILE)
print(json.dumps({'file':str(FILE),'nodes':len(nodes),'edges':len(edges),'validation':'XML/IDs/references/text bounds valid'},ensure_ascii=False))

# Keep auxiliary preview versions explicit even when invoked directly.
import sys as _preview_sys
_preview_sys.path.insert(0, str(OUT.parent))
from preview_support import annotate_indexes as _annotate_indexes
_annotate_indexes()
