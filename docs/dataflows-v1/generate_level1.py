"""Level-one DFD: one complete graph plus seven same-level reading panels.
Repeated external entities / stores have identical logical IDs, not new stores.
"""
from pathlib import Path
import copy, html, json, math
import xml.etree.ElementTree as E
from PIL import Image, ImageDraw, ImageFont

OUT=Path(__file__).resolve().parent
FONT='C:/Windows/Fonts/msyh.ttc'
W=3000
ENT={'global':'权限中心总管理员','specialist':'公司权限管理专员','hr':'HR／人员组织数据来源','oa':'OA 系统','business':'业务系统后端'}
STORES={'D1':'身份、会话与管理范围','D2':'组织、员工与任职','D3':'系统、菜单与操作目录',
        'D4':'角色与角色权限','D5':'员工角色授权','D6':'变更申请、审批与执行记录',
        'D7':'授权及管理审计','D8':'导入批次与报告','D9':'运行与调用日志'}
# Store tuple: logical ID, read data (store -> process), write data (process -> store).
PROCS=[
 ('P1','1.0','管理身份与管理范围','UC-07、09',[
  ('global',[('F01.1','in','后台账号、会话与专员范围请求'),('F02.1','out','身份、范围与账号办理结果')]),
  ('specialist',[('F03.1','in','登录、本人改密与退出请求'),('F04.1','out','身份、当前范围与会话结果')])],[
  ('D1','账号、会话、任命与当前范围','账号、凭据版本、会话及任免范围'),
  ('D2','员工状态、公司及有效任职',''),('D3','拟配置的系统及有效状态',''),
  ('D6','未结束申请与管理范围变更影响',''),
  ('D7','','账号与任免管理审计'),('D9','','必要登录与运行摘要')]),
 ('P2','2.0','维护系统与权限目录','UC-01',[
  ('global',[('F01.2','in','目录维护、影响查询与保存请求'),('F02.2','out','目录、影响预览与保存结果')])],[
  ('D1','当前会话、总管理员资格',''),('D3','功能目录及当前版本','系统、菜单、操作、绑定与版本'),
  ('D4','目录引用与角色权限',''),('D5','受影响的员工授权来源',''),('D7','','目录变更及成功审计')]),
 ('P3','3.0','导入组织人员并处理失效','UC-10',[
  ('global',[('F01.3','in','导入指令、来源映射与执行确认'),('F02.3','out','预检、影响与实际批次报告')]),
  ('hr',[('F05','in','组织、岗位、员工、任职及明确变更')])],[
  ('D1','总管理员资格及专员任命','不满足任职条件的任命失效记录'),
  ('D2','原人员任职与来源映射','组织、员工、任职、映射及版本'),
  ('D4','角色组织归属与适用条件',''),('D5','原授权关系及有效性','不满足条件的授权失效记录'),
  ('D6','组织历史引用与固定申请依据',''),
  ('D8','批次、固定内容与防重依据','预检报告、批次结果与影响'),('D7','组织历史引用','人员变更及失效审计')]),
 ('P4','4.0','办理角色与员工授权申请','UC-02、03、04',[
  ('global',[('F01.4','in','角色／分配／撤销方案与配置查询'),('F02.4','out','配置、差异、提交及实际申请状态')]),
  ('specialist',[('F03.2','in','本公司范围内的方案与配置查询'),('F04.2','out','范围内配置、差异及实际申请状态')]),
  ('oa',[('F06','out','固定申请、编号、版本及关联信息'),('F07.1','in','外部审批申请关联信息')])],[
  ('D1','当前管理范围及原资格来源',''),('D2','员工状态、有效任职与组织',''),
  ('D3','有效系统、功能操作及归属',''),('D4','角色、权限、归属及版本','不可分配的角色草稿变更记录'),
  ('D5','已有授权、目标关系与版本',''),('D6','方案、冲突与实际执行状态','固定方案、摘要、版本与 OA 关联'),
  ('D7','','草稿、提交及方案办理审计')]),
 ('P5','5.0','接收审批并执行权限变更','UC-11',[
  ('oa',[('F07.2','in','可信最终批准／驳回及申请依据'),('F08','out','结果接收、拒绝、重复及执行状态')])],[
  ('D1','OA 调用身份及原资格对应范围',''),('D2','当前身份、任职与适用条件',''),
  ('D3','系统、功能权限状态与归属',''),('D4','角色及当前配置版本','批准执行后的角色权限及激活版本'),
  ('D5','目标授权及关系版本','批准执行后的授予／撤销与版本'),
  ('D6','固定方案、最终结果与重试依据','接收结果、执行／失败／失效状态'),
  ('D7','','接收、执行及实际成功审计')]),
 ('P6','6.0','查询与校验有效访问权限','UC-05、06',[
  ('business',[('F09','in','调用身份、员工、公司、系统及权限'),('F10','out','进入资格、菜单、功能操作／检查结果或错误')])],[
  ('D1','受信调用身份及绑定系统',''),('D2','员工、公司及有效任职',''),
  ('D3','有效目录、进入权限与菜单绑定',''),('D4','同公司同系统的有效角色权限',''),
  ('D5','员工当前有效授权关系',''),('D9','','最少必要的调用摘要')]),
 ('P7','7.0','查询范围内授权审计','UC-08',[
  ('global',[('F01.5','in','审计筛选、分页与详情查询'),('F02.5','out','全局可读审计及关联事件')]),
  ('specialist',[('F03.3','in','范围内审计筛选与详情查询'),('F04.3','out','范围内审计及关联事件')])],[
  ('D1','最新会话、资格与可读范围',''),('D6','审批与执行关联事件',''),
  ('D7','范围内审计、快照、差异与数量','')]),
]

def scene(proc):
 key,num,name,uc,actors,stores=proc
 h=max(1100,220*len(stores)+270,330*len(actors)+270)
 nodes=[];edges=[]
 def node(id,label,x,y,w,hh,kind,logical):
  nodes.append(dict(id=id,label=label,x=x,y=y,w=w,h=hh,kind=kind,logical=logical))
 def edge(id,a,b,label,p,q,parentflow=None):
  edges.append(dict(id=id,a=a,b=b,label=label,p=p,q=q,parentflow=parentflow))
 node(key,num+'\n'+name,1040,160,460,h-280,'process',key)
 # One evenly-spaced row per external entity, with ample room for request/response labels.
 actor_gap=(h-300)/len(actors)
 for i,(actor,flows) in enumerate(actors):
  cy=200+actor_gap*(i+.5)
  nid=key+'_'+actor
  node(nid,ENT[actor]+'\n（同名实体重复引用）',50,cy-120,340,240,'external',actor)
  for j,(fid,direction,label) in enumerate(flows):
   yy=cy+(j-(len(flows)-1)/2)*145
   a,b=(nid,key) if direction=='in' else (key,nid)
   p,q=((390,yy),(1040,yy)) if direction=='in' else ((1040,yy),(390,yy))
   edge(fid,a,b,fid+' '+label,p,q,fid.split('.')[0])
 for i,(did,read,write) in enumerate(stores):
  yy=180+i*220
  nid=key+'_'+did
  node(nid,did+'  '+STORES[did]+'\n（同编号为同一逻辑存储）',2350,yy,570,140,'store',did)
  if read:edge(key+'_'+did+'_r',nid,key,read,(2350,yy+35),(1500,yy+35))
  if write:edge(key+'_'+did+'_w',key,nid,write,(1500,yy+105),(2350,yy+105))
 return dict(key=key,title=num+' '+name,uc=uc,height=h,nodes=nodes,edges=edges)

def draw(pages,path,preview_name):
 width=W;height=int(sum(p['height'] for p in pages)+230)
 mx=E.Element('mxfile',host='app.diagrams.net',agent='official drawio skill level-one DFD')
 dia=E.SubElement(mx,'diagram',id=preview_name,name='第一层完整分解' if len(pages)>1 else pages[0]['title'])
 model=E.SubElement(dia,'mxGraphModel',page='1',pageWidth=str(width),pageHeight=str(height),grid='1',gridSize='10')
 root=E.SubElement(model,'root');E.SubElement(root,'mxCell',id='0');E.SubElement(root,'mxCell',id='1',parent='0')
 im=Image.new('RGB',(width,height),'white');d=ImageDraw.Draw(im)
 svg=E.Element('svg',xmlns='http://www.w3.org/2000/svg',width=str(width),height=str(height),viewBox=f'0 0 {width} {height}')
 E.SubElement(svg,'rect',x='0',y='0',width=str(width),height=str(height),fill='white')
 def text(label,x,y,w,h,size=22):
  font=ImageFont.truetype(FONT,size);lines=label.split('\n');lh=size+12
  assert len(lines)*lh<=h,(label,'height')
  assert max(font.getlength(s) for s in lines)<=w-16,(label,'width')
  top=y+(h-len(lines)*lh)/2
  for i,line in enumerate(lines):
   xx=x+(w-font.getlength(line))/2
   d.text((xx,top+i*lh),line,font=font,fill='#213b58')
   t=E.SubElement(svg,'text',x=str(x+w/2),y=str(top+i*lh+size),fill='#213b58',attrib={'text-anchor':'middle','font-size':str(size),'font-family':'Microsoft YaHei,sans-serif'});t.text=line
 def vertex(id,label,x,y,w,h,kind,logical=''):
  if kind=='process':style='rounded=1;arcSize=10;fillColor=#b9d7ff;strokeColor=#1c4e8a;fontSize=27;';fill='#b9d7ff';stroke='#1c4e8a'
  elif kind=='store':style='shape=partialRectangle;left=0;right=0;top=1;bottom=1;fillColor=#fff3d6;strokeColor=#9f7b2a;';fill='#fff3d6';stroke='#9f7b2a'
  elif kind=='external':style='rounded=0;fillColor=#f1f5f9;strokeColor=#71869a;';fill='#f1f5f9';stroke='#71869a'
  else:style='text;strokeColor=none;fillColor=none;fontSize=23;';fill=None;stroke=None
  c=E.SubElement(root,'mxCell',id=id,value=html.escape(label).replace('\n','<br>'),vertex='1',parent='1',style='html=1;whiteSpace=wrap;fontFamily=Microsoft YaHei;fontSize=22;fontColor=#213b58;align=center;verticalAlign=middle;strokeWidth=2;spacing=12;'+style)
  if logical:c.set('logicalId',logical)
  E.SubElement(c,'mxGeometry',x=str(x),y=str(y),width=str(w),height=str(h),attrib={'as':'geometry'})
  if fill:
   E.SubElement(svg,'rect',x=str(x),y=str(y),width=str(w),height=str(h),fill=fill,stroke=stroke if kind!='store' else 'none',rx='25' if kind=='process' else '0',attrib={'stroke-width':'2'})
   if kind=='process':d.rounded_rectangle((x,y,x+w,y+h),radius=25,fill=fill,outline=stroke,width=2)
   elif kind=='external':d.rectangle((x,y,x+w,y+h),fill=fill,outline=stroke,width=2)
   else:
    d.rectangle((x,y,x+w,y+h),fill=fill)
    for yy in (y,y+h):
     d.line((x,yy,x+w,yy),fill=stroke,width=2);E.SubElement(svg,'line',x1=str(x),y1=str(yy),x2=str(x+w),y2=str(yy),stroke=stroke,attrib={'stroke-width':'2'})
  text(label,x,y,w,h,27 if kind=='process' else 22)
 title='集中权限管理系统 · 第一层数据流图'
 vertex('title',title,30,10,W-60,60,'text')
 vertex('legend','蓝色圆角框：处理过程；灰色框：外部实体；双横线：数据存储。重复引用不是新增副本；本图不表示执行顺序。',30,75,W-60,55,'text')
 ybase=150
 for page in pages:
  vertex(page['key']+'_heading',page['title']+'  ｜  '+page['uc'],30,ybase,W-60,70,'text')
  lookup={n['id']:n for n in page['nodes']}
  for e in page['edges']:
   a,b=lookup[e['a']],lookup[e['b']];p,q=e['p'],e['q'];p=(p[0],p[1]+ybase);q=(q[0],q[1]+ybase)
   aa=((e['p'][0]-a['x'])/a['w'],(e['p'][1]-a['y'])/a['h']);bb=((e['q'][0]-b['x'])/b['w'],(e['q'][1]-b['y'])/b['h'])
   assert all(0<=v<=1 for v in (*aa,*bb)),(e['id'],aa,bb)
   style=f'html=1;edgeStyle=none;noEdgeStyle=1;rounded=0;curved=0;endArrow=block;endFill=1;strokeColor=#4d6783;strokeWidth=2;fontFamily=Microsoft YaHei;fontSize=20;labelBackgroundColor=#ffffff;exitX={aa[0]};exitY={aa[1]};entryX={bb[0]};entryY={bb[1]};exitPerimeter=0;entryPerimeter=0;'
   c=E.SubElement(root,'mxCell',id=e['id'],source=e['a'],target=e['b'],value=html.escape(e['label']),edge='1',parent='1',style=style)
   if e['parentflow']:c.set('contextFlow',e['parentflow'])
   geo=E.SubElement(c,'mxGeometry',relative='1',attrib={'as':'geometry'});E.SubElement(geo,'mxPoint',x='0',y='-18',attrib={'as':'offset'})
   d.line((p,q),fill='#4d6783',width=2);angle=math.atan2(q[1]-p[1],q[0]-p[0]);wing=[(q[0]-13*math.cos(angle-.4),q[1]-13*math.sin(angle-.4)),q,(q[0]-13*math.cos(angle+.4),q[1]-13*math.sin(angle+.4))];d.polygon(wing,fill='#4d6783')
   E.SubElement(svg,'line',x1=str(p[0]),y1=str(p[1]),x2=str(q[0]),y2=str(q[1]),stroke='#4d6783',attrib={'stroke-width':'2'})
   E.SubElement(svg,'polygon',points=' '.join(f'{x},{y}' for x,y in wing),fill='#4d6783')
   font=ImageFont.truetype(FONT,20);tw=font.getlength(e['label'])+22;tx=(p[0]+q[0])/2-tw/2;ty=p[1]-38
   assert tw<abs(p[0]-q[0])-12,(e['id'],'label exceeds edge space')
   d.rectangle((tx,ty,tx+tw,ty+40),fill='white');E.SubElement(svg,'rect',x=str(tx),y=str(ty),width=str(tw),height='40',fill='white');text(e['label'],tx,ty,tw,40,20)
  for n in page['nodes']:vertex(n['id'],n['label'],n['x'],n['y']+ybase,n['w'],n['h'],n['kind'],n['logical'])
  ybase+=page['height']
 E.indent(mx,space='  ');E.ElementTree(mx).write(path,encoding='utf-8',xml_declaration=True)
 E.ElementTree(svg).write(OUT/(preview_name+'.svg'),encoding='utf-8',xml_declaration=True)
 im.save(OUT/(preview_name+'.png'))
 return dia

def main():
 pages=[scene(p) for p in PROCS]
 complete=draw(pages,OUT/'权限管理系统_第一层数据流图.drawio','第一层数据流图预览')
 panels=E.Element('mxfile',host='app.diagrams.net')
 for p in pages:
  path=OUT/(p['key']+'_第一层分区.drawio')
  panels.append(draw([p],path,p['key']+'_第一层预览'))
 E.indent(panels,space='  ');E.ElementTree(panels).write(OUT/'第一层数据流图_分区阅读.drawio',encoding='utf-8',xml_declaration=True)
 expected={'F01':('global','in'),'F02':('global','out'),'F03':('specialist','in'),'F04':('specialist','out'),'F05':('hr','in'),'F06':('oa','out'),'F07':('oa','in'),'F08':('oa','out'),'F09':('business','in'),'F10':('business','out')}
 seen=set();balance=[]
 for key,num,name,uc,actors,stores in PROCS:
  for actor,flows in actors:
   for fid,direction,label in flows:
    parent=fid.split('.')[0];assert expected[parent]==(actor,direction)
    seen.add(parent);balance.append(dict(context=parent,flow=fid,process=num,entity=actor,direction=direction,data=label))
 assert seen==set(expected)
 for file in [OUT/'权限管理系统_第一层数据流图.drawio',OUT/'第一层数据流图_分区阅读.drawio']:
  for dia in E.parse(file).getroot():
   cells=dia.findall('./mxGraphModel/root/mxCell');ids={c.get('id') for c in cells};assert len(ids)==len(cells)
   for c in cells:
    if c.get('edge')=='1':assert c.get('source') in ids and c.get('target') in ids and c.find('mxGeometry') is not None
 (OUT/'第一层边界平衡检查.json').write_text(json.dumps(dict(status='passed',contextFlows=10,processes=7,logicalStores=9,flows=balance),ensure_ascii=False,indent=2),encoding='utf-8')
 index=['<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>第一层数据流图</title><style>body{font:17px Microsoft YaHei;background:#f2f4f7;color:#213b58;margin:28px}section{background:white;padding:18px;margin:28px 0}img{max-width:100%}nav{display:flex;gap:24px;flex-wrap:wrap}</style><h1>第一层数据流图：分区阅读</h1><p>同一层的七个处理过程；不是第二层分解。同编号 D 存储、同名外部实体表示同一逻辑对象。点击图片可放大。</p><nav>']
 index += [f'<a href="#{p["key"]}">{p["title"]}</a>' for p in pages];index.append('</nav>')
 for p in pages:index.append(f'<section id="{p["key"]}"><h2>{p["title"]}</h2><a href="{p["key"]}_第一层预览.svg"><img src="{p["key"]}_第一层预览.png"></a></section>')
 index.append('</html>');(OUT/'第一层数据流图预览.html').write_text('\n'.join(index),encoding='utf-8')
 print(json.dumps(dict(processes=7,logical_stores=9,boundary_flows=len(balance),context_balance='F01-F10 passed',preview_panels=7),ensure_ascii=False))

if __name__=='__main__':main()
