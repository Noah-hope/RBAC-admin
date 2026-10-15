"""Editable context DFD and local PNG/SVG previews, based on current use cases."""
from pathlib import Path
import html, json, math
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

OUT=Path(__file__).resolve().parent
W,H=2300,1830
FONT='C:/Windows/Fonts/msyh.ttc'
NODES={
 'center':(900,550,500,700,'0\n集中权限管理系统'),
 'global':(70,300,300,300,'权限中心总管理员'),
 'specialist':(70,1120,300,300,'公司权限管理专员'),
 'hr':(1010,130,280,125,'HR 系统\n人员组织数据来源'),
 'oa':(1900,300,300,420,'OA 系统'),
 'business':(1900,1120,300,300,'业务系统后端'),
}
# Input/output pairs use separate, straight directed edges.
FLOWS=[
 ('F01','global','center','后台身份与管理请求\n目录／角色／授权／账号与范围\n导入指令、映射及审计查询', (370,340),(900,600),(0,0)),
 ('F02','center','global','身份与全局管理范围\n配置数据、办理状态\n导入报告及审计记录',(900,780),(370,550),(0,0)),
 ('F03','specialist','center','后台身份与本公司管理请求\n角色配置／分配／撤销申请\n范围内查询及本人账号操作',(370,1160),(900,1080),(0,0)),
 ('F04','center','specialist','身份与本公司管理范围\n范围内配置、申请执行状态\n及授权审计记录',(900,1200),(370,1370),(0,0)),
 ('F05','hr','center','组织、岗位、员工、任职数据\n及明确的人员变更数据',(1150,255),(1150,550),(250,-90)),
 ('F06','center','oa','固定权限变更申请\n角色配置／分配／撤销方案\n申请编号、版本及关联信息',(1400,600),(1900,340),(0,0)),
 ('F07','oa','center','外部审批关联信息\n最终批准／驳回结果',(1900,510),(1400,820),(0,0)),
 ('F08','center','oa','审批结果接收／处理状态\n含重复、拒绝及执行状态',(1400,980),(1900,680),(0,0)),
 ('F09','business','center','受信权限查询／校验请求\n调用身份、员工、当前公司、系统\n指定权限编码（单项校验）',(1900,1160),(1400,1120),(0,0)),
 ('F10','center','business','系统进入资格、可见菜单\n允许功能操作／单项校验结果\n调用或服务错误信息',(1400,1220),(1900,1370),(0,0)),
]

def main():
 mx=ET.Element('mxfile',host='app.diagrams.net',agent='official drawio skill XML workflow')
 dia=ET.SubElement(mx,'diagram',id='dfd-context',name='顶层数据流图（系统上下文）')
 model=ET.SubElement(dia,'mxGraphModel',page='1',pageWidth=str(W),pageHeight=str(H),grid='1',gridSize='10',adaptiveColors='auto')
 root=ET.SubElement(model,'root');ET.SubElement(root,'mxCell',id='0');ET.SubElement(root,'mxCell',id='1',parent='0')
 im=Image.new('RGB',(W,H),'white');d=ImageDraw.Draw(im)
 svg=ET.Element('svg',xmlns='http://www.w3.org/2000/svg',width=str(W),height=str(H),viewBox=f'0 0 {W} {H}')
 ET.SubElement(svg,'rect',x='0',y='0',width=str(W),height=str(H),fill='white')
 def rect(x,y,w,h,fill,stroke,rounded=False):
  ET.SubElement(svg,'rect',x=str(x),y=str(y),width=str(w),height=str(h),fill=fill,stroke=stroke,attrib={'stroke-width':'2','rx':'24' if rounded else '0'})
  if rounded:d.rounded_rectangle((x,y,x+w,y+h),radius=24,fill=fill,outline=stroke,width=2)
  else:d.rectangle((x,y,x+w,y+h),fill=fill,outline=stroke,width=2)
 def text(label,x,y,w,h,size=22,center=True):
  font=ImageFont.truetype(FONT,size);lines=label.split('\n');lh=size+10
  assert len(lines)*lh<=h,(label,'height overflow')
  assert max(font.getlength(s) for s in lines)<=w-8,(label,'width overflow')
  top=y+(h-len(lines)*lh)/2
  for i,line in enumerate(lines):
   xx=x+(w-font.getlength(line))/2 if center else x+12
   d.text((xx,top+i*lh),line,font=font,fill='#243a52')
   t=ET.SubElement(svg,'text',x=str(x+w/2 if center else x+12),y=str(top+i*lh+size),fill='#243a52',attrib={'text-anchor':'middle' if center else 'start','font-family':'Microsoft YaHei, sans-serif','font-size':str(size)})
   t.text=line
 def vertex(key,label,x,y,w,h,style):
  c=ET.SubElement(root,'mxCell',id=key,value=html.escape(label).replace('\n','<br>'),vertex='1',parent='1',style='html=1;whiteSpace=wrap;fontFamily=Microsoft YaHei;fontSize=23;fontColor=#243a52;align=center;verticalAlign=middle;'+style)
  ET.SubElement(c,'mxGeometry',x=str(x),y=str(y),width=str(w),height=str(h),attrib={'as':'geometry'})
 title='集中权限管理系统 · 顶层数据流图'
 subtitle='系统上下文：一个整体处理过程、五类外部实体；箭头表示数据传递，不表示执行顺序'
 for key,label,y,h,size in [('title',title,10,52,32),('subtitle',subtitle,63,40,20)]:
  vertex(key,label,30,y,W-60,h,f'text;strokeColor=none;fillColor=none;fontSize={size};align=left;')
  text(label,30,y,W-60,h,size,False)
 # Draw lines first, then labels and nodes, so arrows never obscure text.
 for key,a,b,label,p,q,offset in FLOWS:
  ar,br=NODES[a],NODES[b]
  aa=((p[0]-ar[0])/ar[2],(p[1]-ar[1])/ar[3]);bb=((q[0]-br[0])/br[2],(q[1]-br[1])/br[3])
  style=f'html=1;edgeStyle=none;noEdgeStyle=1;curved=0;rounded=0;endArrow=block;endFill=1;endSize=10;strokeColor=#4d6783;strokeWidth=2;fontFamily=Microsoft YaHei;fontSize=19;fontColor=#243a52;labelBackgroundColor=#ffffff;exitX={aa[0]};exitY={aa[1]};entryX={bb[0]};entryY={bb[1]};exitPerimeter=0;entryPerimeter=0;'
  c=ET.SubElement(root,'mxCell',id=key,source=a,target=b,value=html.escape(key+' '+label).replace('\n','<br>'),edge='1',parent='1',style=style)
  geo=ET.SubElement(c,'mxGeometry',relative='1',attrib={'as':'geometry'})
  ET.SubElement(geo,'mxPoint',x=str(offset[0]),y=str(offset[1]),attrib={'as':'offset'})
  d.line((p,q),fill='#4d6783',width=3)
  angle=math.atan2(q[1]-p[1],q[0]-p[0]);wing=[(q[0]-15*math.cos(angle-.4),q[1]-15*math.sin(angle-.4)),q,(q[0]-15*math.cos(angle+.4),q[1]-15*math.sin(angle+.4))]
  d.polygon(wing,fill='#4d6783')
  ET.SubElement(svg,'line',x1=str(p[0]),y1=str(p[1]),x2=str(q[0]),y2=str(q[1]),stroke='#4d6783',attrib={'stroke-width':'2.5'})
  ET.SubElement(svg,'polygon',points=' '.join(f'{x},{y}' for x,y in wing),fill='#4d6783')
 for key,a,b,label,p,q,offset in FLOWS:
  label=key+' '+label;font=ImageFont.truetype(FONT,19);tw=max(font.getlength(s) for s in label.split('\n'))+18;th=len(label.split('\n'))*29+6
  xx=(p[0]+q[0])/2+offset[0]-tw/2;yy=(p[1]+q[1])/2+offset[1]-th/2
  d.rectangle((xx,yy,xx+tw,yy+th),fill='white')
  ET.SubElement(svg,'rect',x=str(xx),y=str(yy),width=str(tw),height=str(th),fill='white')
  text(label,xx,yy,tw,th,19)
 for key,(x,y,w,h,label) in NODES.items():
  central=key=='center';fill='#b9d7ff' if central else '#f7f9fc';stroke='#1c4e8a' if central else '#71869a'
  vertex(key,label,x,y,w,h,('rounded=1;arcSize=18;fontSize=30;' if central else 'rounded=0;')+f'fillColor={fill};strokeColor={stroke};strokeWidth=2;spacing=12;')
  rect(x,y,w,h,fill,stroke,central);text(label,x,y,w,h,30 if central else 23)
 notes=[('读图与层级','矩形：外部实体；圆角矩形：整体处理过程。\n本图不展开数据库、内部模块和审批步骤。\n此处“顶层”指上下文图，第一层分解另画。'),
        ('一期外部对接','HR 以文件／模拟数据提供资料，管理员上传确认。\nOA 模拟外部审批；结果接收不等于权限已生效。\n业务系统使用简单、真实的 HTTP 调用演示。'),
        ('边界与有效权限','普通员工不直接操作权限管理后台。\n权限中心只管理功能权限，不设计数据权限。\n没有权限与调用失败／服务故障分别返回。')]
 for i,(heading,body) in enumerate(notes):
  x=45+i*745;label=heading+'\n'+body
  vertex('note'+str(i),label,x,1580,710,175,'rounded=0;fillColor=#fff8e8;strokeColor=#c4a34f;fontSize=20;align=left;spacing=14;')
  rect(x,1580,710,175,'#fff8e8','#c4a34f');text(label,x+8,1580,694,175,20,False)
 ET.indent(mx,space='  ');ET.ElementTree(mx).write(OUT/'权限管理系统_顶层数据流图.drawio',encoding='utf-8',xml_declaration=True)
 ET.ElementTree(svg).write(OUT/'顶层数据流图预览.svg',encoding='utf-8',xml_declaration=True)
 im.save(OUT/'顶层数据流图预览.png')
 # Check the context graph, not only XML syntax.
 cells=root.findall('mxCell');ids={c.get('id') for c in cells};assert len(ids)==len(cells)
 assert len(NODES)==6 and len(FLOWS)==10
 for c in cells:
  if c.get('edge')=='1':
   assert c.get('source') in NODES and c.get('target') in NODES
   assert 'center' in (c.get('source'),c.get('target'))
   assert c.find('mxGeometry') is not None and c.find('.//Array') is None
 for key in NODES.keys()-{'center'}:assert any(key in (a,b) for _,a,b,*_ in FLOWS)
 print(json.dumps(dict(processes=1,external_entities=5,data_flows=10,validation='XML, IDs, edge references, context boundary and text bounds passed'),ensure_ascii=False))

if __name__=='__main__':main()
