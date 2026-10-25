// Render the native XML with the official drawio viewer runtime in a local browser.
// Usage: node <this file> <path to official viewer-static.min.js> [screenshot.png]
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require(path.resolve('frontend/node_modules/@playwright/test'));

(async () => {
  const runtime = path.resolve(process.argv[2]);
  const source = path.join(__dirname, '系统边界级数据流图.drawio');
  const data = JSON.parse(fs.readFileSync(path.join(__dirname, '数据流说明.json'), 'utf8'));
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 2440, height: 1620 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', route => /^https?:/.test(route.request().url()) ? route.abort() : route.continue());
    await page.setContent('<!doctype html><meta charset="utf-8"><body style="margin:0"><div id="graph" style="width:2400px;height:1580px;overflow:hidden"></div></body>');
    await page.evaluate(() => {
      window.mxLoadResources = false;
      window.mxLoadStylesheets = false;
      window.mxBasePath = '';
    });
    await page.addScriptTag({ path: runtime });
    const results=[];
    for (const expected of data.pages) {
    await page.setViewportSize({width:expected.width+40,height:expected.height+40});
    const result = await page.evaluate(({xml,key,width,height}) => {
      const container=document.getElementById('graph');
      container.innerHTML='';
      container.style.width=width+'px';
      container.style.height=height+'px';
      const graph = new Graph(document.getElementById('graph'));
      graph.setHtmlLabels(true);
      const doc = mxUtils.parseXml(xml);
      const diagram=[...doc.getElementsByTagName('diagram')].find(d=>d.getAttribute('id')===key);
      // drawio decodes one page at a time; page-local cell IDs may repeat in the mxfile.
      const pageDoc=mxUtils.parseXml(mxUtils.getXml(diagram.getElementsByTagName('mxGraphModel')[0]));
      new mxCodec(pageDoc).decode(pageDoc.documentElement, graph.getModel());
      graph.getView().validate();
      window.testGraph = graph;
      const rect = r => ({ x:r.x, y:r.y, width:r.width, height:r.height });
      const labels = Object.values(graph.getModel().cells).filter(c => c.edge).map(cell => {
        const state = graph.getView().getState(cell);
        return { id:cell.id, text:rect(state.text.boundingBox), bounds:rect(state.text.bounds),
          points:state.absolutePoints.map(p => ({x:p.x,y:p.y})) };
      });
      const headers=Object.values(graph.getModel().cells).filter(c=>c.id.startsWith('group-')).map(c=>{
        return {id:c.id,text:rect(graph.getView().getState(c).text.boundingBox)};
      });
      return { labels, headers, graphBounds:rect(graph.getGraphBounds()) };
    }, {xml:fs.readFileSync(source, 'utf8'),key:expected.id,width:expected.width,height:expected.height});
    if (process.argv[3]) {
      const dest=path.resolve(process.argv[3]).replace(/\.png$/, '-'+expected.id+'.png');
      await page.screenshot({path:dest,fullPage:true});
    }
    assert.equal(errors.length, 0, errors.join('\n'));
    assert.equal(result.labels.length, expected.flows.length);
    assert.deepEqual(result.labels.map(l=>l.id).sort(),expected.flows.map(l=>l.id).sort());
    for (const label of result.labels) {
      assert.ok(label.bounds.width >= 450 && label.text.width >= 100, `${label.id}: label collapsed to ${label.text.width}px`);
      assert.ok(label.text.height < 45, `${label.id}: unwanted wrapping, height ${label.text.height}px`);
      assert.ok(label.text.y >= 130, `${label.id}: text extends above diagram`);
      assert.ok(label.text.y + label.text.height < expected.height-100, `${label.id}: text extends below diagram`);
      assert.ok(label.text.x>=0 && label.text.x+label.text.width<expected.width,`${label.id}: outside page width`);
    }
    const texts=[...result.labels,...result.headers];
    for (let i=0; i<texts.length; i++) for (let j=i+1; j<texts.length; j++) {
      const a=texts[i], b=texts[j];
      const overlap = a.text.x < b.text.x+b.text.width && a.text.x+a.text.width > b.text.x &&
        a.text.y < b.text.y+b.text.height && a.text.y+a.text.height > b.text.y;
      assert.ok(!overlap, `${a.id}/${b.id}: rendered text overlaps`);
    }
    const zoomed = await page.evaluate(() => {
      testGraph.getView().setScale(0.28);
      return Object.values(testGraph.getModel().cells).filter(c=>c.edge).map(c=>{
        const b=testGraph.getView().getState(c).text.boundingBox;
        return {id:c.id,width:b.width/0.28,height:b.height/0.28};
      });
    });
    for (const label of zoomed) {
      assert.ok(label.width>=100 && label.height<45, `${label.id}: wrapping changed at 28% zoom`);
    }
    results.push({page:expected.id,flows:result.labels.length,headers:result.headers.length,status:'passed'});
    }
    await page.goto('file:///'+path.join(__dirname,'顶层数据流图预览.html').replaceAll('\\','/'));
    assert.equal(await page.locator('svg').count(),1);
    for (const flow of data.pages[0].flows) assert.ok((await page.locator('svg').textContent()).includes(flow.name));
    assert.equal(errors.length,0,errors.join('\n'));
    console.log(JSON.stringify({pages:results,zoom:[1,0.28],singlePagePreview:'passed',errors},null,2));
    console.log('PASS: one native page, eight short labels; no wrapping, overlap or overflow at 2 zoom levels.');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode=1; });
