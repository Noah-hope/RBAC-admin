// 从现有原生图源渲染正文所需PNG；不修改drawio，不调用旧需求预览生成器。
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..');
const {chromium}=require(path.join(root,'frontend/node_modules/@playwright/test'));
const out=path.join(__dirname,'images');fs.mkdirSync(out,{recursive:true});
// 可传入页面ID（例如er-2）只覆盖该PNG；同源其他页面仅渲染比对，不写图片。
const selected=process.argv[2]||null;
const previous=selected?JSON.parse(fs.readFileSync(path.join(out,'manifest.json'),'utf8')):null;
if(selected&&!previous.images.some(i=>i.id===selected))throw new Error('Unknown image ID: '+selected);
const sources=[
 {id:'df-context',source:'docs/01-requirements/dataflows/context/系统边界级数据流图.drawio',pages:[0]},
 ...Array.from({length:7},(_,i)=>({id:'df-p'+(i+1),source:'docs/01-requirements/dataflows/第一层数据流图.drawio',pages:[0],panel:'P'+(i+1)})),
 {id:'er',source:'docs/04-database/database-erd.drawio',pages:[0,1,2,3]},
];
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:1500,height:1000},deviceScaleFactor:1.5});const errors=[];
  page.on('pageerror',e=>errors.push(e.message));await page.route('**/*',r=>r.abort());
  await page.setContent('<meta charset="utf-8"><style>html,body{margin:0;background:white}#g{position:relative;overflow:hidden;background:white}#g b,#g strong{font-family:SimHei;font-weight:700}</style><div id="g"></div>');
  await page.evaluate(()=>{window.mxLoadResources=false;window.mxLoadStylesheets=false;});
  await page.addScriptTag({path:path.join(root,'.tmp/drawio-render-check/viewer-static.min.js')});
  const manifest=selected?previous.images.map(i=>({...i})):[];
  for(const source of sources){
   if(selected&&!source.pages.some(index=>(source.pages.length>1?source.id+'-'+(index+1):source.id)===selected))continue;
   const raw=fs.readFileSync(path.join(root,source.source),'utf8');
   const pages=await page.evaluate(raw=>[...mxUtils.parseXml(raw).getElementsByTagName('diagram')].map(p=>({name:p.getAttribute('name'),xml:new XMLSerializer().serializeToString(p)})),raw);
   for(const index of source.pages){
    const item=pages[index],id=source.pages.length>1?source.id+'-'+(index+1):source.id;
    const dimensions=await page.evaluate(({xml,panel})=>{
     if(window.activeGraph)window.activeGraph.destroy();
     const d=mxUtils.parseXml(xml),m=d.getElementsByTagName('mxGraphModel')[0];
     const cells=[...m.getElementsByTagName('mxCell')];
     let crop=null;
     if(panel){
      const panelCell=cells.find(c=>c.id==='panel-'+panel),geo=panelCell.getElementsByTagName('mxGeometry')[0];
      crop={x:+geo.getAttribute('x'),y:+geo.getAttribute('y'),width:+geo.getAttribute('width'),height:+geo.getAttribute('height')};
      const selectedEdges=cells.filter(c=>c.getAttribute('edge')==='1'&&(c.getAttribute('logicalSource')===panel||c.getAttribute('logicalTarget')===panel));
      const keep=new Set(['0','1',panel,'panel-'+panel,'caption-'+panel]);
      for(const c of selectedEdges){keep.add(c.id);keep.add(c.getAttribute('source'));keep.add(c.getAttribute('target'));keep.add('label-'+c.id);}
      for(const c of cells)if(!keep.has(c.id))c.remove();
      const bounds=[...m.getElementsByTagName('mxCell')].filter(c=>c.getAttribute('vertex')==='1'&&c.id!=='panel-'+panel).map(c=>c.getElementsByTagName('mxGeometry')[0]);
      const x0=Math.min(...bounds.map(g=>+g.getAttribute('x')))-20,y0=Math.min(...bounds.map(g=>+g.getAttribute('y')))-20;
      const x1=Math.max(...bounds.map(g=>+g.getAttribute('x') + +g.getAttribute('width')))+20,y1=Math.max(...bounds.map(g=>+g.getAttribute('y') + +g.getAttribute('height')))+20;
      crop={x:x0,y:y0,width:x1-x0,height:y1-y0};
      geo.setAttribute('x',String(x0));geo.setAttribute('y',String(y0));geo.setAttribute('width',String(crop.width));geo.setAttribute('height',String(crop.height));
      for(const c of [...m.getElementsByTagName('mxCell')])if(c.getAttribute('vertex')==='1'){
       const geometry=c.getElementsByTagName('mxGeometry')[0];
       geometry.setAttribute('x',String(+geometry.getAttribute('x')-crop.x));
       geometry.setAttribute('y',String(+geometry.getAttribute('y')-crop.y));
      }
     }
     for(const c of [...m.getElementsByTagName('mxCell')]){
      if(!c.getAttribute('style'))continue;
      const heading=c.getAttribute('kind')==='process'||/^(title|subtitle|caption-)/.test(c.id);
      let style=c.getAttribute('style').replace(/fontColor=[^;]*;/g,'').replace(/fontFamily=[^;]*;/g,'').replace(/fontStyle=[^;]*;/g,'');
      style+='fontColor=#000000;fontFamily='+(heading?'SimHei':'FangSong')+';fontStyle='+(heading?'1':'0')+';';
      if(panel)style=style.replace(/fontSize=([\d.]+);/g,(_,size)=>'fontSize='+(+size*1.1)+';');
      style=style.replace(/fillColor=#17365D;/ig,'fillColor=#D9E6F2;');
      c.setAttribute('style',style);
      if(c.id==='legend'&&c.getAttribute('value'))c.setAttribute('value',c.getAttribute('value').replace('font-size:27px','font-size:27px;font-family:SimHei;font-weight:700'));
     }
     const width=crop?crop.width:+m.getAttribute('pageWidth'),height=crop?crop.height:+m.getAttribute('pageHeight'),container=document.getElementById('g');
     container.innerHTML='';container.style.width=width+'px';container.style.height=height+'px';
     const g=new Graph(container);g.setHtmlLabels(true);new mxCodec(d).decode(m,g.model);g.view.validate();window.activeGraph=g;
     return {width,height,nodes:Object.values(g.model.cells).filter(c=>c.vertex).length,edges:Object.values(g.model.cells).filter(c=>c.edge).length,panel:panel||null,fontColors:[...new Set(Object.values(g.model.cells).filter(c=>c.vertex).map(c=>g.view.getState(c).style.fontColor))]};
    },{xml:item.xml,panel:source.panel});
    await page.evaluate(()=>document.fonts.ready);
    const png=await page.locator('#g').screenshot({type:'png',animations:'disabled'});
    const pngSha256=crypto.createHash('sha256').update(png).digest('hex');
    const entry={id,file:id+'.png',source:source.source,page:index+1,title:source.panel?source.panel+' 精简数据流图':item.name,...dimensions,scale:1.5,documentTypography:'SimHei bold headings; FangSong body; black text; dark fill lightened for contrast',sourceSha256:crypto.createHash('sha256').update(raw).digest('hex'),pngSha256};
    if(selected&&id!==selected){
     if(pngSha256!==manifest.find(i=>i.id===id).pngSha256)throw new Error('An unselected source page also differs: '+id);
     console.log('Unchanged PNG verified without overwrite: '+id);
    }else{
     fs.writeFileSync(path.join(out,id+'.png'),png);
     console.log('PNG '+id+': '+dimensions.width+'x'+dimensions.height+' @1.5x');
    }
    if(selected)manifest[manifest.findIndex(i=>i.id===id)]=entry;else manifest.push(entry);
   }
  }
  if(errors.length)throw new Error(errors.join('\n'));
  fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify({renderer:'Official drawio viewer runtime in local Edge; page-independent native XML rendering',images:manifest,errors},null,2)+'\n');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
