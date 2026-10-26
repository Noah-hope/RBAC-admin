"""Render the concise single-page context DFD."""
from pathlib import Path
import html, json
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont
from boundary_model import FLOWS

OUT=Path(__file__).resolve().parent
BASE='whiteSpace=wrap;html=1;fontFamily=SimSun;fontStyle=1;fontColor=#18344D;'
INK,BLUE='#18344D','#3569A2'


class Page:
    def __init__(self,root,key,title,width,height,subtitle):
        self.key,self.width,self.height=key,width,height
        self.xml=ET.SubElement(root,'diagram',id=key,name=title)
        graph=ET.SubElement(self.xml,'mxGraphModel',grid='0',page='1',pageWidth=str(width),pageHeight=str(height),background='#ffffff')
        self.cells=ET.SubElement(graph,'root')
        ET.SubElement(self.cells,'mxCell',id='0')
        ET.SubElement(self.cells,'mxCell',id='1',parent='0')
        self.svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
                  '<rect width="100%" height="100%" fill="white"/>',
                  '<defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="5" orient="auto"><path d="M0,0 L10,5 L0,10 Z" fill="#3569A2"/></marker></defs>']
        self.image=Image.new('RGB',(width,height),'white')
        self.paint=ImageDraw.Draw(self.image)
        self.nodes,self.labels,self.flows={},[],[]
        self.text('title',title,40,20,width-80,60,34,True)
        self.text('subtitle',subtitle,40,85,width-80,42,22)

    def line(self,text,x,y,size=24,bold=False,max_width=None,color=INK):
        font=ImageFont.truetype('C:/Windows/Fonts/simsun.ttc',size)
        assert self.paint.textlength(text,font=font)<=(max_width or self.width-80),text
        self.paint.text((x,y),text,anchor='mm',font=font,fill=color,stroke_width=1,stroke_fill=color)
        self.svg.append(f'<text x="{x}" y="{y}" text-anchor="middle" dominant-baseline="central" font-family="SimSun,serif" font-size="{size}" font-weight="700" fill="{color}">{html.escape(text)}</text>')

    def vertex(self,key,x,y,width,height,value,style,**meta):
        c=ET.SubElement(self.cells,'mxCell',id=key,value=value,style=BASE+style,vertex='1',parent='1',**meta)
        ET.SubElement(c,'mxGeometry',x=str(x),y=str(y),width=str(width),height=str(height),**{'as':'geometry'})

    def text(self,key,value,x,y,width,height,size=24,bold=False):
        self.vertex(key,x,y,width,height,html.escape(value),f'text;align=center;fontSize={size};fontStyle=1;')
        self.line(value,x+width/2,y+height/2,size,bold,width)

    def node(self,key,x,y,width,height,lines):
        system=key=='system'
        fill,stroke=('#17365D','#17365D') if system else ('#D9D9D9','#737373')
        text_color='#FFFFFF' if system else INK
        self.nodes[key]=(x,y,width,height)
        self.vertex(key,x,y,width,height,'<br>'.join(lines),f'rounded={int(system)};arcSize=10;fillColor={fill};strokeColor={stroke};strokeWidth=2;fontSize=30;fontStyle=1;fontColor={text_color};',kind='system' if system else 'external')
        if system:self.paint.rounded_rectangle((x,y,x+width,y+height),radius=30,fill=fill,outline=stroke,width=2)
        else:self.paint.rectangle((x,y,x+width,y+height),fill=fill,outline=stroke,width=2)
        self.svg.append(f'<rect x="{x}" y="{y}" width="{width}" height="{height}" rx="{30 if system else 0}" fill="{fill}" stroke="{stroke}" stroke-width="2"/>')
        for i,text in enumerate(lines):self.line(text,x+width/2,y+height/2+(i-(len(lines)-1)/2)*44,30,True,width-15,text_color)

    def edge(self,r,y,label_width=760):
        sx,sy,sw,sh=self.nodes[r['source']]
        tx,ty,tw,th=self.nodes[r['target']]
        right=sx<tx
        x1,x2=(sx+sw,tx) if right else (sx,tx+tw)
        assert sy<=y<=sy+sh and ty<=y<=ty+th
        center=(x1+x2)/2
        lines=[r['name'],*r['data']]
        label_y=y-25-(len(lines)-1)*30
        value=f'<b>{html.escape(lines[0])}</b>'
        style=(BASE+f'edgeStyle=none;endArrow=block;endFill=1;strokeColor={BLUE};strokeWidth=3;fontSize=24;labelWidth={label_width};align=center;verticalAlign=middle;'
               f'exitX={int(right)};exitY={(y-sy)/sh};exitPerimeter=0;entryX={int(not right)};entryY={(y-ty)/th};entryPerimeter=0;')
        edge=ET.SubElement(self.cells,'mxCell',id=r['id'],source=r['source'],target=r['target'],value=value,style=style,edge='1',parent='1',businessGroup=r.get('group','summary'),usecases=','.join(r['usecases']))
        geo=ET.SubElement(edge,'mxGeometry',relative='1',**{'as':'geometry'})
        ET.SubElement(geo,'mxPoint',x='0',y=str(-25-(len(lines)-1)*15),**{'as':'offset'})
        self.paint.line((x1,y,x2,y),fill=BLUE,width=3)
        sign=1 if right else -1
        self.paint.polygon([(x2,y),(x2-sign*14,y-7),(x2-sign*14,y+7)],fill=BLUE)
        self.svg.append(f'<path d="M{x1},{y} L{x2},{y}" fill="none" stroke="{BLUE}" stroke-width="3" marker-end="url(#arrow)"/>')
        for i,text in enumerate(lines):self.line(text,center,label_y+i*30,24,i==0,label_width-10)
        self.labels.append((center-label_width/2,label_y-16,label_width,len(lines)*30))
        self.flows.append(r)

    def save(self,stem):
        for i,a in enumerate(self.labels):
            assert 0<=a[0] and a[0]+a[2]<=self.width and 130<=a[1] and a[1]+a[3]<self.height-100
            for b in self.labels[i+1:]:
                assert not (a[0]<b[0]+b[2] and a[0]+a[2]>b[0] and a[1]<b[1]+b[3] and a[1]+a[3]>b[1]),(a,b)
        svg='\n'.join(self.svg+['</svg>'])
        (OUT/(stem+'.svg')).write_bytes(svg.encode('utf-8'))
        self.image.save(OUT/(stem+'.png'))
        return svg


def generate():
    root=ET.Element('mxfile',host='app.diagrams.net',version='24.7.17')
    p=Page(root,'context','集中权限管理系统 · 顶层数据流图',1800,950,'系统边界与主要输入、输出数据')
    p.node('admin',30,210,210,200,['权限中心','总管理员'])
    p.node('manager',30,590,210,200,['权限管理员'])
    p.node('system',780,220,240,570,['0','集中权限','管理系统'])
    p.node('oa',1560,210,210,200,['OA 系统','（一期模拟）'])
    p.node('business',1560,590,210,200,['业务系统后端'])
    for record,y in zip(FLOWS,[260,370,640,750,260,370,640,750]):
        p.edge(record,y,500)
    p.text('legend','矩形：外部实体　圆角框：权限系统整体　箭头：数据传递方向',30,840,1740,40,22)
    p.text('note','HR 文件由总管理员上传；普通员工通过业务系统使用权限。',30,890,1740,40,22)
    ET.indent(root,space='  ')
    xml=ET.tostring(root,encoding='unicode')
    ET.fromstring(xml)
    (OUT/'系统边界级数据流图.drawio').write_bytes(xml.encode('utf-8'))
    svg=p.save('系统边界级数据流图')
    data=dict(level='系统边界级',pages=[dict(id=p.key,width=p.width,height=p.height,flows=p.flows)])
    (OUT/'数据流说明.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    page_html='<!doctype html><meta charset="utf-8"><title>顶层数据流图</title><style>body{margin:0;background:white}svg{display:block;width:100%;height:auto}</style>'+svg
    (OUT/'顶层数据流图预览.html').write_text(page_html,encoding='utf-8',newline='\n')
    print('Generated: one context page, four external entities, eight concise data-flow labels.')
