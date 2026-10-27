// 从团队归档的JaCoCo结果生成便于提交的PDF汇总，不运行测试或修改原始报告。
const fs=require('node:fs'),path=require('node:path');
const {execFileSync}=require('node:child_process');
const {pathToFileURL}=require('node:url');
const root=path.resolve(__dirname,'../..'),tmp=path.join(root,'.tmp/jacoco-pdf');
const output=path.join(root,'output/pdf/JaCoCo测试报告-2026-10-27.pdf');
const {chromium}=require(path.join(root,'frontend/node_modules/@playwright/test'));
(async()=>{
 fs.mkdirSync(tmp,{recursive:true});
 execFileSync('python',['-c',String.raw`
from pathlib import Path
import hashlib,html,json,zipfile,xml.etree.ElementTree as ET
root=Path.cwd(); folder=root/'docs/05-detailed-design/reports'
archive=folder/'JaCoCo-2026-10-27.zip'
expected=(folder/'JaCoCo-2026-10-27.zip.sha256').read_text().split()[0].lower()
assert hashlib.sha256(archive.read_bytes()).hexdigest()==expected
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    result=json.loads(z.read('results.json'))
    xml=ET.fromstring(z.read('report/jacoco.xml'))
def counter(node,kind):
    c=node.find("counter[@type='%s']"%kind)
    if c is None:return 0,0
    return int(c.get('covered')),int(c.get('missed'))
def ratio(node,kind):
    covered,missed=counter(node,kind); total=covered+missed
    return ('%.2f%%'% (covered*100/total)) if total else '无统计项'
for kind,metrics in result['coverage'].items():
    assert counter(xml,kind)==(metrics['covered'],metrics['missed'])
suites=result['suites']; assert sum(s['tests'] for s in suites)==700
assert all(s[k]==0 for s in suites for k in ['failures','errors','skipped'])
esc=lambda s:html.escape(str(s))
def table(headers,rows,widths=None):
    colgroup='<colgroup>'+''.join('<col style="width:%s%%">'%w for w in widths)+'</colgroup>' if widths else ''
    return '<table>'+colgroup+'<thead><tr>'+''.join('<th>'+esc(h)+'</th>' for h in headers)+'</tr></thead><tbody>'+''.join('<tr>'+''.join('<td>'+esc(c)+'</td>' for c in row)+'</tr>' for row in rows)+'</tbody></table>'
body='<h1>RBAC权限管理系统</h1><div class="subtitle">JaCoCo测试与覆盖率报告</div>'
body+='<p>测试完成时间：'+esc(result['finishedAt'])+'；执行耗时：'+esc(result['duration'])+'。</p>'
body+='<h2>1. 测试结果</h2>'
rows=[]
for kind,name in [('surefire','普通测试'),('failsafe','真实MySQL/HTTP')]:
    selected=[s for s in suites if s['kind']==kind]
    rows.append([name,len(selected),sum(s['tests'] for s in selected),0,0,0])
rows.append(['合计',len(suites),700,0,0,0])
body+=table(['测试类型','测试组','测试数','失败','错误','跳过'],rows,[30,14,14,14,14,14])
body+='<h2>2. 总体覆盖率</h2>'
names={'LINE':'行','METHOD':'方法','CLASS':'类','BRANCH':'分支','INSTRUCTION':'指令','COMPLEXITY':'复杂度'}
body+=table(['指标','已覆盖','总数','覆盖率'],[[names[k],result['coverage'][k]['covered'],result['coverage'][k]['total'],ratio(xml,k)] for k in names],[25,25,25,25])
body+='<p>全部正式代码参与统计，未配置包或类排除。行、方法和类覆盖率均为100%；分支覆盖率为78.59%。分支为0/0的项目表示没有可统计分支。</p>'
body+='<h2>3. 环境与报告来源</h2>'
body+=table(['项目','取值'],[[n,result[k]] for n,k in [('JDK','java'),('MySQL','mysql'),('Maven','maven'),('JaCoCo','jacoco'),('测试命令','command')]], [22,78])
body+='<p>本PDF由2026-10-27团队归档中的results.json和jacoco.xml生成，记录该次自动化测试结果；本次导出未重新运行测试。PDF包含汇总、包覆盖率及测试组结果，逐行源码覆盖标记请查看同批完整HTML报告。</p>'
body+='<p class="small">原始归档：docs/05-detailed-design/reports/JaCoCo-2026-10-27.zip<br>SHA-256：'+expected+'</p>'
body+='<section><h2>4. 各包覆盖率</h2><p>包名省略公共前缀com.adgn.backend；覆盖率使用XML原始计数计算。无可执行代码的包不列入本表。</p>'
packages=[p for p in xml.findall('package') if sum(counter(p,'CLASS'))]
rows=[]
for p in sorted(packages,key=lambda p:p.get('name')):
    name=p.get('name').replace('/','.')
    if name=='com.adgn.backend':name='（根包）'
    else:name=name.removeprefix('com.adgn.backend.')
    rows.append([name,ratio(p,'LINE'),ratio(p,'BRANCH'),ratio(p,'METHOD')])
body+=table(['包','行','分支','方法'],rows,[46,18,18,18])+'</section>'
body+='<section><h2>5. 测试组结果</h2><p>下表按实际JUnit报告列出全部测试组；测试类名保留完整名称，便于与原报告对应。</p>'
body+=table(['测试组','类别','测试数','失败/错误/跳过'],[[s['name'],'普通' if s['kind']=='surefire' else 'MySQL/HTTP',s['tests'],'0 / 0 / 0'] for s in suites],[59,16,9,16])+'</section>'
css='''@page {size:A4;margin:17mm 16mm 20mm}*{box-sizing:border-box}body{font:10.5pt/1.5 FangSong,serif;color:#000;margin:0}h1,h2,th,.subtitle{font-family:SimHei,sans-serif;font-weight:700;color:#000}h1{font-size:26pt;margin:0 0 2mm}.subtitle{font-size:18pt;margin:0 0 7mm}h2{font-size:15pt;margin:6mm 0 3mm;break-after:avoid}p{margin:0 0 3mm;orphans:3;widows:3}table{width:100%;border-collapse:collapse;table-layout:fixed;margin:3mm 0 4mm;font-size:9pt;line-height:1.35}th,td{padding:1.3mm;border:.6pt solid #bbc7d2;text-align:left;overflow-wrap:anywhere;vertical-align:top}th{background:#edf1f5}tr{break-inside:avoid}thead{display:table-header-group;break-after:avoid}section{break-before:page}.small{font-size:9pt;overflow-wrap:anywhere}'''
target=root/'.tmp/jacoco-pdf/report.html'
target.write_text('<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>JaCoCo测试与覆盖率报告</title><style>'+css+'</style></head><body>'+body+'</body></html>',encoding='utf8')
(root/'.tmp/jacoco-pdf/summary.json').write_text(json.dumps({'tests':700,'suiteCount':len(suites),'packageRows':len(packages),'archiveSha256':expected,'coverage':result['coverage']},ensure_ascii=False,indent=2),encoding='utf8')
`],{cwd:root,stdio:'inherit'});
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try{
  const page=await browser.newPage();await page.route(/^https?:\/\//,r=>r.abort());
  await page.goto(pathToFileURL(path.join(tmp,'report.html')).href);await page.emulateMedia({media:'print'});
  await page.evaluate(()=>document.fonts.ready);
  await page.pdf({path:output,preferCSSPageSize:true,printBackground:true,outline:true,tagged:true,displayHeaderFooter:true,headerTemplate:'<div></div>',
   footerTemplate:'<div style="width:100%;font-family:FangSong,serif;font-size:8px;color:#000;margin:0 16mm;display:flex;justify-content:space-between"><span>RBAC · JaCoCo测试报告 · 2026-10-27</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>'});
 }finally{await browser.close();}
 execFileSync('python',['-c',String.raw`
from pathlib import Path
import json,pymupdf as f
from PIL import Image,ImageDraw
root=Path.cwd(); p=root/'output/pdf/JaCoCo测试报告-2026-10-27.pdf'; d=f.open(p)
text=''.join(page.get_text() for page in d)
assert '700' in text and '78.59%' in text and '100.00%' in text
qa=root/'.tmp/jacoco-pdf/qa';qa.mkdir(parents=True,exist_ok=True)
issues=[]
for i,page in enumerate(d):
    assert page.get_text().strip()
    for block in page.get_text('dict')['blocks']:
        if block.get('type')!=0:continue
        for line in block['lines']:
            for span in line['spans']:
                r=f.Rect(span['bbox'])
                if span['text'].strip() and (r.x0<0 or r.y0<0 or r.x1>page.rect.width+1 or r.y1>page.rect.height+1 or span['color']!=0):issues.append([i+1,span['text']])
    page.get_pixmap(matrix=f.Matrix(1,1),alpha=False).save(qa/('page-%02d.png'%(i+1)))
assert not issues,issues
for start in range(0,len(d),12):
    sheet=Image.new('RGB',(1000,1560),'#dddddd');draw=ImageDraw.Draw(sheet)
    for n in range(start,min(start+12,len(d))):
        img=Image.open(qa/('page-%02d.png'%(n+1))).convert('RGB');img.thumbnail((310,360))
        j=n-start;x=(j%3)*333+11;y=(j//3)*390+22;sheet.paste(img,(x,y));draw.text((x,y-17),'Page %d'%(n+1),fill='black')
    sheet.save(qa/('contact-%d.png'%(start//12+1)))
print(json.dumps({'pdf':str(p),'pages':len(d),'textBounds':'passed','allTextBlack':True},ensure_ascii=False))
`],{cwd:root,stdio:'inherit'});
})().catch(e=>{console.error(e);process.exitCode=1;});
