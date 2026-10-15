"""Native UML activities, straight control edges, local auxiliary PNG previews.
Reads current flow definitions without running their output code; never reads history.
"""
from pathlib import Path
import copy, html, json, math
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
FLOW = OUT.parent / 'flows-v1/generate_all.py'
ns = {'__file__': str(FLOW)}
exec(compile(FLOW.read_text(encoding='utf-8').split('\ndef make_xml(page):')[0], str(FLOW), 'exec'), ns)
cases, Flow = ns['cases'], ns['Flow']
f = Flow('UC-01', '维护业务系统与权限目录', '总管理员；直接维护，不经过 OA')
f.a('access','打开系统与权限目录维护')
f.d('auth','会话与总管理员资格有效？').b('auth',['拒绝维护，不修改目录'])
f.a('select','选择已有系统或登记新系统')
f.a('edit','维护系统、菜单、功能操作\n菜单可见条件及启停状态')
f.d('valid','编码、归属、层级、权限状态及绑定合法？').b('valid',['显示问题字段与原因\n修正本次输入'],target='edit',tone='amber')
f.a('preview','展示目录变化及引用影响\n填写修改原因')
f.d('confirm','确认保存？').b('confirm',['取消未提交变更，原目录不变'],tone='amber')
f.a('recheck','提交时复核资格、版本和目录规则')
f.d('fresh','复核通过？').b('fresh',['拒绝保存，提示冲突或资格变化'])
f.a('save','同一事务保存目录、版本与成功审计')
f.d('stored','全部提交成功？').b('stored',['整体回滚，不显示成功'])
f.a('result','显示保存结果及对象启停状态',tone='green')
f.note('目录维护不自动授权；新系统有唯一 SYSTEM_ACCESS，不绑定菜单。\n已引用目录不直接物理删除；不跨系统迁移。\n菜单绑定同功能的查看／进入页面权限；编辑不推导查看。')
f.note('范围缩减不得直接破坏已生效配置，须先按 UC-02 调整。\n停用与重新启用只过滤尚有效关联，已失效／已撤销授权不复活。')
cases[1] = [f]

ADMIN = {1:{'access','select','edit','preview','confirm'},
 2:{'access','role','edit','confirm','result'},3:{'access','edit','confirm','result'},
 4:{'access','edit','confirm','result'},7:{'access','edit','confirm'},
 8:{'access','detail'},9:{'input','use','logout'},10:{'access','edit','confirm'}}
FONT='C:/Windows/Fonts/msyh.ttc'
COLORS=ns['COLORS']
wrap=ns['wrap']

def owner(num, key):
    if num in (5,6): return 'caller' if key in ('access','external') else 'center'
    if num==11: return 'oa' if key=='access' else 'center'
    if key in ADMIN.get(num,set()): return 'admin'
    return 'center'

def build(num, flow):
    flow=copy.deepcopy(flow)
    if num==11:
        flow.steps[0]['label']='交付 OA 最终结果及关联申请信息'
    corrections={2:('valid','根据规则错误或版本冲突提示\n重新读取并修正配置'),
                 3:('valid','根据整批拒绝的问题提示\n重新选择员工与角色配对'),
                 4:('valid','根据拒绝或冲突提示\n重新查询并选择目标关系'),
                 8:('valid','根据参数问题提示修正条件\n重新发起查询')}
    if num in corrections:
        source,label=corrections[num]
        next(b for b in flow.branches if b['source']==source)['labels']=[label]
    if num==2:
        at=next(i for i,s in enumerate(flow.steps) if s['id']=='role')
        flow.steps[at]['label']='选择已有角色\n或填写新角色信息，请求保存草稿'
        flow.steps.insert(at+1,dict(id='draft',label='读取已有角色；新角色校验编码唯一\n保存不可分配草稿及创建审计',kind='action',tone='blue'))
    if num==9 and flow.key!='UC-09':
        ADMIN[9]={'access','edit'}
        if '密码修改' in flow.key: ADMIN[9].add('result')
    if num in (2,3,4):
        at=next(i for i,s in enumerate(flow.steps) if s['id']=='execute')
        flow.steps[at:at]=[dict(id='oa-approve',label='按外部 OA 流程审批固定方案\n本项目不展开内部审批步骤',kind='action',tone='blue'),
                         dict(id='oa-deliver',label='交付可信最终批准／驳回结果',kind='action',tone='blue')]
        flow.steps[at+2]['label']='调用 UC-11：接收结果、复核并自动执行\n批准不等于已生效，显示实际执行状态'
    lane_names=([('caller','受信业务系统后端'),('center','权限中心')] if num in (5,6) else
                [('oa','外部 OA 系统'),('center','权限中心：接收与自动执行')] if num==11 else
                [('admin','管理员\n总管理员／获准公司专员' if num in (2,3,4,8) else '管理员\n总管理员' if num in (1,7,10) or flow.key.endswith('账号创建') or flow.key.endswith('密码重置') else '后台账号本人'),('center','权限中心')])
    if num in (2,3,4): lane_names.append(('oa','外部 OA 系统'))
    loops=[b for b in flow.branches if b['target']]
    lw=980+max(0,len(loops)-1)*300
    mainx=lw-205; altx=lw-565
    lane_idx={key:i for i,(key,_) in enumerate(lane_names)}
    nodes=[];edges=[]; lookup={}; y=115
    def node(key,label,lane,cx,yy,kind='action',tone='blue'):
        w=320 if kind=='action' else 290 if kind=='decision' else 24
        font=ImageFont.truetype(FONT,18)
        lines=wrap(label,font,w-70 if kind=='decision' else w-26)
        h=max(110 if kind=='decision' else 76,len(lines)*27+28) if kind in ('action','decision') else 24
        v=dict(id=key,label=label,lane=lane,x=cx-w/2,y=yy,w=w,h=h,kind=kind,tone=tone)
        nodes.append(v);lookup[key]=v;return v
    firstowner=owner(num,flow.steps[0]['id'])
    node('start','',firstowner,mainx,y,'initial');y+=90
    merge_for={}
    for i,b in enumerate(loops):
        target=b['target']
        merge_for.setdefault(target,[]).append((b,190+i*300,'merge-'+str(i)))
    seq=['start']
    for s in flow.steps:
        key=s['id']; lane='oa' if key.startswith('oa-') else owner(num,key)
        for b,cx,mid in merge_for.get(key,[]):
            node(mid,'',lane,cx,y,'merge');seq.append(mid);y+=75
        v=node(key,s['label'],lane,mainx,y,s['kind'],s['tone']);seq.append(key)
        branches=[b for b in flow.branches if b['source']==key]
        bottom=y+v['h']
        for b in branches:
            target=b['target']
            if target:
                _,cx,mid=next(t for t in merge_for[target] if t[0] is b)
                blane=owner(num,target)
            else:
                cx=altx;blane='center'
                if key in ('confirm','detail','logout'): blane=lane
            by=y+max(0,(v['h']-76)/2)
            prev=key
            for j,label in enumerate(b['labels']):
                bid=key+'-alt-'+str(j)
                branchcx=altx if target and j<len(b['labels'])-1 else cx
                bv=node(bid,label,blane,branchcx,by,'action',b['tone'])
                edges.append(dict(a=prev,b=bid,label='['+b['guard']+']' if j==0 else ''))
                prev=bid;by+=bv['h']+70
            if target: edges.append(dict(a=prev,b=mid,label='[修正后]' if num!=11 else '[恢复后重试]'))
            else:
                endid=key+'-end';node(endid,'',blane,cx,by,'final')
                edges.append(dict(a=prev,b=endid,label=''));by+=24
            bottom=max(bottom,by)
        y=bottom+110
    node('end','',owner(num,flow.steps[-1]['id']),mainx,y,'final');seq.append('end')
    for a,b in zip(seq,seq[1:]):
        step=next((s for s in flow.steps if s['id']==a),{})
        edges.append(dict(a=a,b=b,label='['+step['main']+']' if step.get('main') else ''))
    for v in nodes:
        assert v['x']>20 and v['x']+v['w']<lw-20 and v['y']>75
        for u in nodes:
            if u['id']>=v['id'] or u['lane']!=v['lane']:continue
            assert not (v['x']<u['x']+u['w'] and u['x']<v['x']+v['w'] and v['y']<u['y']+u['h'] and u['y']<v['y']+v['h']),(flow.key,u['id'],v['id'],'overlapping nodes')
    reached={'start'}
    while True:
        nxt=reached|{e['b'] for e in edges if e['a'] in reached}
        if nxt==reached:break
        reached=nxt
    assert reached==set(lookup),(flow.key,'unreachable nodes',set(lookup)-reached)
    return dict(key=flow.key,title=flow.title,nodes=nodes,edges=edges,lookup=lookup,lanes=lane_names,lw=lw,li=lane_idx,lh=y+100,notes=flow.notes)

def pos(page,v):
    return (30+page['li'][v['lane']]*page['lw']+v['x'],150+v['y'],v['w'],v['h'])

def intersects(a,b,rect):
    # Segment versus shrunken bounding box, excluding harmless perimeter touches.
    x,y,w,h=rect;x+=3;y+=3;w-=6;h-=6
    lo,hi=0.,1.
    for p,q in ((-(b[0]-a[0]),a[0]-x),(b[0]-a[0],x+w-a[0]),(-(b[1]-a[1]),a[1]-y),(b[1]-a[1],y+h-a[1])):
        if abs(p)<1e-9:
            if q<0:return False
        else:
            t=q/p
            if p<0:lo=max(lo,t)
            else:hi=min(hi,t)
            if lo>hi:return False
    return True

def perimeter(v,dx,dy):
    x,y,w,h=v;cx=x+w/2;cy=y+h/2
    scale=min(w/2/abs(dx) if dx else 1e9,h/2/abs(dy) if dy else 1e9)
    return cx+dx*scale,cy+dy*scale

def route(page,e):
    a=pos(page,page['lookup'][e['a']]);b=pos(page,page['lookup'][e['b']])
    dx=b[0]+b[2]/2-a[0]-a[2]/2;dy=b[1]+b[3]/2-a[1]-a[3]/2
    pa=perimeter(a,dx,dy);pb=perimeter(b,-dx,-dy)
    def normalized(p,r):return ((p[0]-r[0])/r[2],(p[1]-r[1])/r[3])
    simple_a=page['lookup'][e['a']]['kind']=='action'
    simple_b=page['lookup'][e['b']]['kind']=='action'
    candidates=[(normalized(pa,a),normalized(pb,b))] if simple_a and simple_b else []
    anchors=[(.5,0),(1,.5),(.5,1),(0,.5),(.25,0),(.75,0),(.25,1),(.75,1),(0,.25),(0,.75),(1,.25),(1,.75)]
    candidates += [(aa,bb) for aa in (anchors if simple_a else anchors[:4]) for bb in (anchors if simple_b else anchors[:4])]
    choices=[]
    for aa,bb in candidates:
        p=(a[0]+a[2]*aa[0],a[1]+a[3]*aa[1]);q=(b[0]+b[2]*bb[0],b[1]+b[3]*bb[1])
        if any(intersects(p,q,pos(page,v)) for v in page['nodes'] if v['id'] not in (e['a'],e['b'])):continue
        # Do not let an anchor send the straight segment through its own shape.
        if intersects(p,q,a) or intersects(p,q,b):continue
        choices.append((math.dist(p,q),aa,bb,p,q))
    assert choices,(page['key'],e['a'],e['b'],'no unobstructed straight segment')
    _,aa,bb,p,q=min(choices,key=lambda z:z[0]);e.update(aa=aa,bb=bb,p=p,q=q)

def write(path,root):
    ET.indent(root,space='  ');ET.ElementTree(root).write(path,encoding='utf-8',xml_declaration=True)

def render(page):
    width=page['lw']*len(page['lanes'])+60
    height=int(150+page['lh']+max(200,len(page['notes'])*200)+70)
    diagram=ET.Element('diagram',id=page['key'].lower()+'-activity',name=page['key']+' '+page['title'])
    model=ET.SubElement(diagram,'mxGraphModel',page='1',pageWidth=str(width),pageHeight=str(height),grid='1',gridSize='10')
    root=ET.SubElement(model,'root');ET.SubElement(root,'mxCell',id='0');ET.SubElement(root,'mxCell',id='1',parent='0')
    im=Image.new('RGB',(width,height),'white');d=ImageDraw.Draw(im)
    def vertex(key,label,x,y,w,h,style,parent='1'):
        c=ET.SubElement(root,'mxCell',id=key,value=html.escape(label).replace('\n','<br>'),vertex='1',parent=parent,
          style='html=1;whiteSpace=wrap;fontFamily=Microsoft YaHei;fontSize=18;fontColor=#23384f;align=center;verticalAlign=middle;'+style)
        ET.SubElement(c,'mxGeometry',x=str(x),y=str(y),width=str(w),height=str(h),attrib={'as':'geometry'})
    def text(label,x,y,w,h,size=18,center=True):
        font=ImageFont.truetype(FONT,size);lines=wrap(label,font,w-30)
        assert len(lines)*(size+9)<=h,(page['key'],label,'text overflow')
        top=y+(h-len(lines)*(size+9))/2
        for i,line in enumerate(lines):d.text((x+(w-font.getlength(line))/2 if center else x+12,top+i*(size+9)),line,font=font,fill='#23384f')
    title=page['key']+' '+page['title']+' · UML 活动图'
    vertex('title',title,30,15,width-60,60,'text;strokeColor=none;fillColor=none;fontSize=28;align=left;')
    text(title,30,15,width-60,60,28,False)
    sub='责任泳道 · 直线控制流 · [条件] 表示分支；终点仅结束本次活动，不取消后台恢复任务'
    vertex('subtitle',sub,30,80,width-60,45,'text;strokeColor=none;fillColor=none;align=left;')
    text(sub,30,80,width-60,45,18,False)
    for key,label in page['lanes']:
        x=30+page['li'][key]*page['lw']
        vertex('lane-'+key,label,x,150,page['lw'],page['lh'],'swimlane;horizontal=1;startSize=75;container=1;collapsible=0;fillColor=#eef5ff;swimlaneFillColor=#ffffff;strokeColor=#8a9caf;')
        d.rectangle((x,150,x+page['lw'],150+page['lh']),outline='#8a9caf',width=2)
        d.rectangle((x,150,x+page['lw'],225),fill='#eef5ff',outline='#8a9caf',width=2);text(label,x,150,page['lw'],75,20)
    for i,e in enumerate(page['edges']):
        route(page,e)
        a,b=page['lookup'][e['a']],page['lookup'][e['b']]
        aa,bb=e['aa'],e['bb']
        style=f'html=1;edgeStyle=none;noEdgeStyle=1;curved=0;rounded=0;endArrow=open;endFill=0;endSize=10;strokeColor=#536b84;strokeWidth=1.7;fontFamily=Microsoft YaHei;fontSize=17;labelBackgroundColor=#ffffff;exitX={aa[0]};exitY={aa[1]};entryX={bb[0]};entryY={bb[1]};exitPerimeter=1;entryPerimeter=1;'
        c=ET.SubElement(root,'mxCell',id='edge'+str(i),source=e['a'],target=e['b'],value=html.escape(e['label']),edge='1',parent='lane-'+a['lane'] if a['lane']==b['lane'] else '1',style=style)
        ET.SubElement(c,'mxGeometry',relative='1',attrib={'as':'geometry'})
        p,q=e['p'],e['q'];d.line((p,q),fill='#536b84',width=2)
        angle=math.atan2(q[1]-p[1],q[0]-p[0])
        d.line([(q[0]-12*math.cos(angle-.45),q[1]-12*math.sin(angle-.45)),q,(q[0]-12*math.cos(angle+.45),q[1]-12*math.sin(angle+.45))],fill='#536b84',width=2)
        if e['label']:
            font=ImageFont.truetype(FONT,17);tw=font.getlength(e['label']);xx=(p[0]+q[0])/2-tw/2;yy=(p[1]+q[1])/2-22
            d.rectangle((xx-3,yy-1,xx+tw+3,yy+25),fill='white');d.text((xx,yy),e['label'],font=font,fill='#536b84')
    for v in page['nodes']:
        x,y,w,h=pos(page,v);kind=v['kind'];fill,stroke=COLORS[v['tone']]
        if kind in ('initial','final'):
            style='ellipse;fillColor='+('#23384f' if kind=='initial' else '#ffffff')+';strokeColor=#23384f;strokeWidth=2;'
            d.ellipse((x,y,x+w,y+h),fill='#23384f' if kind=='initial' else 'white',outline='#23384f',width=2)
        elif kind in ('decision','merge'):
            style=f'rhombus;fillColor={fill};strokeColor={stroke};spacing=8;'
            d.polygon([(x+w/2,y),(x+w,y+h/2),(x+w/2,y+h),(x,y+h/2)],fill=fill,outline=stroke,width=2)
        else:
            style=f'rounded=1;arcSize=16;fillColor={fill};strokeColor={stroke};spacing=8;'
            d.rounded_rectangle((x,y,x+w,y+h),radius=12,fill=fill,outline=stroke,width=2)
        vertex(v['id'],v['label'],v['x'],v['y'],w,h,style,'lane-'+v['lane'])
        if kind=='final':
            vertex(v['id']+'-inner','',v['x']+5,v['y']+5,w-10,h-10,'ellipse;fillColor=#23384f;strokeColor=#23384f;','lane-'+v['lane'])
            d.ellipse((x+5,y+5,x+w-5,y+h-5),fill='#23384f')
        if v['label']:text(v['label'],x,y,w,h)
    note_y=150+page['lh']+25
    for i,note in enumerate(page['notes']):
        vertex('note'+str(i),note,40,note_y,width-80,185,'rounded=0;fillColor=#fff8e8;strokeColor=#c4a34f;align=left;spacing=12;fontSize=17;')
        d.rectangle((40,note_y,width-40,note_y+185),fill='#fff8e8',outline='#c4a34f');text(note,40,note_y,width-80,185,17,False);note_y+=200
    im.save(OUT/(page['key']+'_活动图预览.png'))
    return diagram

def container():return ET.Element('mxfile',host='app.diagrams.net',agent='official drawio skill XML activity workflow',modified='2026-10-15T00:00:00.000Z')

def main():
    combined=container();cards=[]
    for num in sorted(cases):
        mx=container()
        for flow in cases[num]:
            page=build(num,flow);diagram=render(page);mx.append(diagram);combined.append(copy.deepcopy(diagram));cards.append(page)
        title=cases[num][0].title.replace('／','')
        write(OUT/(f'UC-{num:02d}_{title}_活动图.drawio'),mx)
    write(OUT/'权限管理系统_全部活动图.drawio',combined)
    for diagram in combined:
        cells=diagram.findall('./mxGraphModel/root/mxCell');ids={c.get('id') for c in cells}
        assert len(ids)==len(cells)
        edges=[c for c in cells if c.get('edge')=='1'];connected=set()
        for e in edges:
            assert e.get('source') in ids and e.get('target') in ids
            assert 'edgeStyle=none;' in e.get('style') and e.find('.//Array') is None
            connected.update((e.get('source'),e.get('target')))
        graph={c.get('id') for c in cells if c.get('vertex')=='1' and c.get('parent') not in ('0','1') and not c.get('id').endswith('-inner')}
        assert graph<=connected,(diagram.get('name'),graph-connected)
    head='<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>全部 UML 活动图</title><style>body{font:16px Microsoft YaHei;background:#f2f4f7;color:#23384f;margin:24px}section{background:white;margin:24px 0;padding:20px}img{max-width:100%;height:auto}nav{display:flex;gap:16px;flex-wrap:wrap}</style><h1>权限管理系统 UML 活动图</h1><p>11 个用例、14 页。直线控制流；图片是本地辅助预览，可点击原图放大。编辑请使用 drawio 文件。</p><nav>'
    contents=[head]+[f'<a href="#{p["key"]}">{p["key"]}</a>' for p in cards]+['</nav>']
    for p in cards:
        img=p['key']+'_活动图预览.png';contents.append(f'<section id="{p["key"]}"><h2>{html.escape(p["key"]+" "+p["title"])}</h2><a href="{img}"><img loading="lazy" src="{img}"></a></section>')
    contents.append('</html>');(OUT/'全部活动图预览.html').write_text('\n'.join(contents),encoding='utf-8')
    print(json.dumps(dict(usecases=11,pages=len(cards),straight_edges=sum(len(p['edges']) for p in cards),validation='unique IDs, endpoints, connected nodes, straight segments avoid unrelated node boxes, text bounds passed'),ensure_ascii=False))

if __name__=='__main__':main()
