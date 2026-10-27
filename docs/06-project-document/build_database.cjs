// 独立数据库说明书构建；不调用项目设计说明书的compose/build，避免改写原交付。
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {execFileSync}=require('node:child_process');
const {pathToFileURL}=require('node:url');
const root=path.resolve(__dirname,'../..'),tmp=path.join(root,'.tmp/database-document');
const {default:Asciidoctor}=require(path.join(root,'.tmp/project-doc-tools/node_modules/@asciidoctor/core'));
const {chromium}=require(path.join(root,'frontend/node_modules/@playwright/test'));
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const pdf=path.join(root,'output/pdf/RBAC权限管理系统数据库设计说明书.pdf');
const outputHtml=path.join(root,'output/html/RBAC权限管理系统数据库设计说明书.html');
const protectedFiles=['docs/06-project-document/项目设计说明书.adoc',
 'output/pdf/集中权限管理系统项目设计说明书.pdf','output/html/集中权限管理系统项目设计说明书.html',
 'output/pdf/项目设计说明书源文件及图片.zip','docs/04-database/database-erd.drawio',
 'docs/04-database/schema-model.json','docs/04-database/schema.sql',
 ...[1,2,3,4].map(n=>'docs/06-project-document/images/er-'+n+'.png')];
(async()=>{
 fs.mkdirSync(tmp,{recursive:true});
 const baseline=Object.fromEntries(protectedFiles.map(p=>[p,hash(path.join(root,p))]));
 fs.writeFileSync(path.join(tmp,'protected-baseline.json'),JSON.stringify(baseline,null,2));
 execFileSync('python',[path.join(__dirname,'database_compose.py')],{cwd:root,stdio:'inherit'});
 const processor=Asciidoctor(),logger=processor.MemoryLogger.create();processor.LoggerManager.setLogger(logger);
 let content=processor.convert(fs.readFileSync(path.join(__dirname,'数据库设计说明书.adoc'),'utf8'),
  {safe:'safe',doctype:'book',header_footer:false,base_dir:__dirname});
 const messages=logger.getMessages().map(m=>({severity:m.severity,message:String(m.message)}));
 if(messages.length)throw new Error(JSON.stringify(messages));
 let embedded=0;
 content=content.replace(/(<img\b[^>]*\bsrc=")([^"]+)"/g,(_,prefix,src)=>{
  if(!/^images\/er-[1-4]\.png$/.test(src))throw new Error('Unexpected figure: '+src);
  embedded++;return prefix+'data:image/png;base64,'+fs.readFileSync(path.join(__dirname,src)).toString('base64')+'"';
 });
 if(embedded!==4)throw new Error('Expected four ER figures');
 const css=fs.readFileSync(path.join(__dirname,'document.css'),'utf8')+'\n'+fs.readFileSync(path.join(__dirname,'database-document.css'),'utf8');
 const html='<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>RBAC权限管理系统数据库设计说明书</title><style>'+css+'</style></head><body>'+content+'</body></html>';
 const htmlPath=path.join(tmp,'database.html');fs.writeFileSync(htmlPath,html);
 fs.mkdirSync(path.dirname(pdf),{recursive:true});fs.mkdirSync(path.dirname(outputHtml),{recursive:true});
 fs.writeFileSync(outputHtml,html.replace(/(<table\b[\s\S]*?<\/table>)/g,'<div class="table-scroll">$1</div>'));
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));await page.route(/^https?:\/\//,r=>r.abort());
  await page.goto(pathToFileURL(htmlPath).href,{waitUntil:'load'});await page.emulateMedia({media:'print'});
  const inspection=await page.evaluate(async()=>{
   await document.fonts.ready;await Promise.all([...document.images].map(i=>i.decode()));
   return {
    dictionaryTables:document.querySelectorAll('table.dictionary-table').length,
    dictionaryRows:document.querySelectorAll('table.dictionary-table tbody tr').length,
    images:[...document.images].map(i=>({width:i.naturalWidth,height:i.naturalHeight,display:i.getBoundingClientRect().width,container:i.closest('.er-section').getBoundingClientRect().width})),
    cover:document.querySelector('.cover').innerText.replace(/\s+/g,''),
    headings:[...document.querySelectorAll('h1,h2,h3,h4,.title,#toctitle')].map(e=>({text:e.textContent,font:getComputedStyle(e).fontFamily,weight:getComputedStyle(e).fontWeight})),
    bodyFont:getComputedStyle(document.body).fontFamily,
    nonBlack:[...document.querySelectorAll('body *')].filter(e=>e.textContent.trim()&&getComputedStyle(e).color!=='rgb(0, 0, 0)').length,
    brokenAnchors:[...document.querySelectorAll('a[href^="#"]')].filter(a=>!document.getElementById(decodeURIComponent(a.getAttribute('href').slice(1)))).length
   };
  });
  const sources=JSON.parse(fs.readFileSync(path.join(__dirname,'database-sources.json'),'utf8'));
  if(inspection.dictionaryTables!==sources.tables||inspection.dictionaryRows!==sources.columns)throw new Error('Incomplete dictionary');
  if(inspection.images.length!==4||inspection.images.some(i=>!i.width||Math.abs(i.display-i.container)>1))throw new Error('ER width/image check failed');
  if(inspection.cover!=='RBAC权限管理系统数据库设计说明书'||inspection.nonBlack||inspection.brokenAnchors||errors.length)throw new Error('Document check failed');
  if(!inspection.bodyFont.includes('FangSong')||inspection.headings.some(h=>!h.font.includes('SimHei')||+h.weight<700))throw new Error('Typography mismatch');
  await page.pdf({path:pdf,preferCSSPageSize:true,printBackground:true,outline:true,tagged:true,displayHeaderFooter:true,
   headerTemplate:'<div></div>',footerTemplate:'<div style="width:100%;font-family:FangSong,serif;font-size:8px;color:#000;margin:0 16mm;display:flex;justify-content:space-between"><span>RBAC权限管理系统 · 数据库设计说明书</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>'});
  // 独立HTML在桌面和手机宽度核对，所有图片离线内嵌。
  await page.emulateMedia({media:'screen'});await page.goto(pathToFileURL(outputHtml).href,{waitUntil:'load'});
  const screens=[];
  for(const width of [1440,390]){
   await page.setViewportSize({width,height:1000});
   const state=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,broken:[...document.images].filter(i=>!i.complete||!i.naturalWidth).length}));
   if(state.scroll>width+1||state.broken)throw new Error('HTML layout failed: '+JSON.stringify(state));
   screens.push(state);
  }
  fs.writeFileSync(path.join(tmp,'inspection.json'),JSON.stringify({messages,inspection,screens,errors},null,2));
 }finally{await browser.close();}
 execFileSync('python',[path.join(__dirname,'database_verify.py')],{cwd:root,stdio:'inherit'});
 for(const [p,sha]of Object.entries(baseline))if(hash(path.join(root,p))!==sha)throw new Error('Existing artifact changed: '+p);
 console.log(JSON.stringify({pdf,html:outputHtml,existingArtifactsUnchanged:true},null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
