// 导出独立离线阅读版；PDF构建也调用此函数，保持两种阅读版本同步。
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const output = path.join(root, 'output/html/集中权限管理系统项目设计说明书.html');
function exportHtml(html) {
  const screen = `
@media screen {
 html { scroll-padding-top:24px; }
 body { max-width:1100px; margin:0 auto; padding:32px 40px 64px; font-size:18px; overflow-wrap:anywhere; }
 .cover { height:auto; padding-top:32px; margin-bottom:48px; }
 .cover-title { font-size:42px; }
 .cover-subtitle { font-size:28px; }
 .cover-description { max-width:none; }
 #toc { margin:32px 0 48px; }
 a[href^="#"] { text-decoration:underline; text-underline-offset:3px; }
 a:focus-visible { outline:2px solid #000; outline-offset:4px; }
 .sect1 { margin-top:48px; }
 h2 { font-size:28px; }
 .diagram-page,.diagram-section { width:100%; margin:32px 0; }
 .diagram-page img,.diagram-section img,.compact-flow img { width:auto!important; max-width:100%; height:auto!important; }
 .compact-flow img { width:100%!important; }
 .diagram-section > .paragraph,.diagram-page .figure-note,.diagram-section .figure-note { max-width:100%; }
 .table-scroll { overflow-x:auto; margin:16px 0; }
 .table-scroll table { min-width:620px; margin:0; font-size:16px; }
 .cover table { font-size:16px; }
 pre { max-width:100%; }
}
@media screen and (max-width:600px) {
 body { padding:20px 16px 40px; font-size:17px; }
 .cover-title { font-size:30px; }
 .cover-subtitle { font-size:23px; }
 h2 { font-size:24px; }
 .cover td { padding:8px; }
}
`;
  html = html.replace('<meta charset="utf-8">', '<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">');
  html = html.replace('</head>', '<style>' + screen + '</style></head>');
  html = html.replace(/(<table\b[\s\S]*?<\/table>)/g, '<div class="table-scroll">$1</div>');
  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, html);
  console.log('HTML: ' + output);
  return output;
}
module.exports = { exportHtml };
if (require.main === module) {
  const { default: Asciidoctor } = require(path.join(root, '.tmp/project-doc-tools/node_modules/@asciidoctor/core'));
  const processor = Asciidoctor();
  const logger = processor.MemoryLogger.create();
  processor.LoggerManager.setLogger(logger);
  let content = processor.convert(fs.readFileSync(path.join(__dirname, '项目设计说明书.adoc'), 'utf8'), {
    safe: 'safe', doctype: 'book', header_footer: false, base_dir: __dirname,
  });
  if (logger.getMessages().length) throw new Error('AsciiDoc conversion reported messages');
  let count = 0;
  content = content.replace(/(<img\b[^>]*\bsrc=")([^"]+)"/g, (_, prefix, src) => {
    const file = path.resolve(__dirname, src);
    if (!src.startsWith('images/') || !file.startsWith(path.join(__dirname, 'images') + path.sep)) throw new Error('Unexpected image: ' + src);
    count++;
    return prefix + 'data:image/png;base64,' + fs.readFileSync(file).toString('base64') + '"';
  });
  if (count !== 12) throw new Error('Expected 12 embedded images');
  exportHtml('<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>RBAC权限管理系统项目设计说明书</title><style>' + fs.readFileSync(path.join(__dirname, 'document.css'), 'utf8') + '</style></head><body>' + content + '</body></html>');
}
