// 使用真正的Asciidoctor.js解析源文档，再用本地Edge分页生成含嵌入图片的PDF。
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{pathToFileURL}=require('node:url');
const {execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..');
const {default:Asciidoctor}=require(path.join(root,'.tmp/project-doc-tools/node_modules/@asciidoctor/core'));
const asciidoctor=Asciidoctor();
const {chromium}=require(path.join(root,'frontend/node_modules/@playwright/test'));
const adoc=path.join(__dirname,'项目设计说明书.adoc'),output=path.resolve(root,process.env.RBAC_DOCUMENT_PDF_OUTPUT||'output/pdf/集中权限管理系统项目设计说明书.pdf');
const tmp=path.join(root,'.tmp/project-document');fs.mkdirSync(tmp,{recursive:true});fs.mkdirSync(path.dirname(output),{recursive:true});
(async()=>{
 execFileSync('python',[path.join(__dirname,'compose.py')],{cwd:root,stdio:'inherit'});
 const images=JSON.parse(fs.readFileSync(path.join(__dirname,'images/manifest.json'),'utf8')).images;
 for(const entry of images){
  const sourceHash=crypto.createHash('sha256').update(fs.readFileSync(path.join(root,entry.source),'utf8')).digest('hex');
  if(sourceHash!==entry.sourceSha256)throw new Error('Source diagram changed; rerun export_drawio.cjs: '+entry.source);
  if(crypto.createHash('sha256').update(fs.readFileSync(path.join(__dirname,'images',entry.file))).digest('hex')!==entry.pngSha256)throw new Error('Exported image changed: '+entry.file);
 }
 const logger=asciidoctor.MemoryLogger.create();asciidoctor.LoggerManager.setLogger(logger);
 let content=asciidoctor.convert(fs.readFileSync(adoc,'utf8'),{safe:'safe',doctype:'book',header_footer:false,base_dir:__dirname});
 const messages=logger.getMessages().map(m=>({severity:m.severity,message:String(m.message)}));
 if(messages.some(m=>['ERROR','FATAL','WARN'].includes(m.severity)))throw new Error(JSON.stringify(messages));
 const refs=[];
 content=content.replace(/(<img\b[^>]*\bsrc=")([^"]+)"/g,(all,prefix,src)=>{
  if(!src.startsWith('images/'))throw new Error('Unexpected image '+src);
  const file=path.resolve(__dirname,src);if(!file.startsWith(path.join(__dirname,'images')+path.sep))throw new Error('Outside images directory');
  refs.push(src);return prefix+'data:image/png;base64,'+fs.readFileSync(file).toString('base64')+'"';
 });
 if(refs.length!==12||new Set(refs).size!==12)throw new Error('Expected 12 distinct embedded PNG figures');
 const css=fs.readFileSync(path.join(__dirname,'document.css'),'utf8');
 const html='<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>RBAC权限管理系统项目设计说明书</title><style>'+css+'</style></head><body>'+content+'</body></html>';
 const htmlPath=path.join(tmp,'document.html');fs.writeFileSync(htmlPath,html);
 require('./export_html.cjs').exportHtml(html);
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:1400,height:1000}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));await page.route(/^https?:\/\//,r=>r.abort());
  await page.goto(pathToFileURL(htmlPath).href,{waitUntil:'load'});await page.emulateMedia({media:'print'});
  const inspection=await page.evaluate(async()=>{
   await document.fonts.ready;
   await Promise.all([...document.images].map(i=>i.decode()));
   const formats={'figure-a3-landscape':[392,232],'figure-a1-landscape':[813,510],'figure-a2-landscape':[566,342],'figure-a2-portrait':[392,514]};
   const figures=[];
   for(const block of document.querySelectorAll('.diagram-page,.diagram-section')){
    const key=Object.keys(formats).find(k=>block.classList.contains(k));if(!key)throw new Error('Unknown figure format');
    let [width,height]=formats[key];
    if(block.classList.contains('diagram-section'))height=key==='figure-a2-landscape'?310:key==='figure-a2-portrait'?500:480;
    const img=block.querySelector('img'),ratio=img.naturalWidth/img.naturalHeight;
    const w=Math.min(width,height*ratio),h=w/ratio;img.style.width=w+'mm';img.style.height=h+'mm';
    figures.push({format:key,pixelWidth:img.naturalWidth,pixelHeight:img.naturalHeight,widthMm:w,heightMm:h});
   }
   return {figures,headings:[...document.querySelectorAll('h2,h3')].map(h=>({id:h.id,text:h.textContent})),tables:document.querySelectorAll('table').length,dictionaryTables:document.querySelectorAll('table.dictionary-table').length,dictionaryRows:document.querySelectorAll('table.dictionary-table tbody tr').length,apiRows:document.querySelectorAll('table.api-table tbody tr').length,brokenImages:[...document.images].filter(i=>!i.complete||!i.naturalWidth).length,anchors:[...document.querySelectorAll('a')].map(a=>a.getAttribute('href')),text:document.body.innerText.length};
  });
  if(inspection.brokenImages||errors.length)throw new Error(JSON.stringify({inspection,errors}));
  if(inspection.dictionaryTables!==0||inspection.dictionaryRows!==0||inspection.apiRows!==67)throw new Error('Removed dictionary/API coverage mismatch: '+JSON.stringify(inspection));
  if(inspection.anchors.some(a=>a&&/\.drawio(?:$|#)/.test(a)))throw new Error('A diagram link remains instead of an image');
  const typography=await page.evaluate(()=>({body:getComputedStyle(document.body).fontFamily,nonBlack:[...document.querySelectorAll('body *')].filter(e=>e.textContent.trim()&&getComputedStyle(e).color!=='rgb(0, 0, 0)').length,headings:[...document.querySelectorAll('h1,h2,h3,h4,h5,h6,.title,#toctitle')].map(e=>({family:getComputedStyle(e).fontFamily,weight:getComputedStyle(e).fontWeight})),compactFigures:document.querySelectorAll('.compact-flow img').length,erFigures:[...document.querySelectorAll('.er-section img')].map(e=>({width:e.getBoundingClientRect().width,container:e.closest('.er-section').getBoundingClientRect().width}))}));
  if(!typography.body.includes('FangSong')||typography.nonBlack||typography.headings.some(h=>!h.family.includes('SimHei')||+h.weight<700)||typography.compactFigures!==8)throw new Error('Typography/compact figure mismatch: '+JSON.stringify(typography));
  if(typography.erFigures.length!==4||typography.erFigures.some(f=>Math.abs(f.width-f.container)>1))throw new Error('ER images must fill the text width');
  const cover=await page.locator('.cover').innerText();
  if(cover.replace(/\s+/g,'')!=='RBAC权限管理系统项目设计说明书')throw new Error('Cover must contain only title and subtitle');
  await page.pdf({path:output,preferCSSPageSize:true,printBackground:true,displayHeaderFooter:true,headerTemplate:'<div></div>',footerTemplate:'<div style="width:100%;font-family:FangSong,serif;font-size:8px;color:#000000;margin:0 16mm;display:flex;justify-content:space-between"><span>集中权限管理系统 · 项目设计说明书</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>',outline:true,tagged:true});
  fs.writeFileSync(path.join(tmp,'inspection.json'),JSON.stringify({asciidoctorVersion:asciidoctor.getVersion(),messages,...inspection,typography,errors},null,2));
  execFileSync('python',[path.join(__dirname,'finalize_pdf.py'),output],{cwd:root,stdio:'inherit'});
  console.log(JSON.stringify({pdf:output,embeddedPngs:refs.length,tables:inspection.tables,headings:inspection.headings.length,asciidoctorMessages:messages,errors,bytes:fs.statSync(output).size},null,2));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
