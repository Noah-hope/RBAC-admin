// Official drawio skill repair pass; only touches this local generated diagram.
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, 'UC-01_维护业务系统与权限目录_流程图.drawio');
let xml = fs.readFileSync(file, 'utf8');
const diagram = xml.match(/<mxfile[\s\S]*?<\/mxfile>|<mxGraphModel[\s\S]*?<\/mxGraphModel>/);
if (diagram && xml.replace(/^<\?xml[^>]*\?>\s*/, '').trim() !== diagram[0])
{
    xml = diagram[0];
}
xml = xml.replace(/="([^"]*)"/g, function(m, value)
{
    return '="' + value.replace(/</g, '&lt;').replace(/&(?!(?:amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);)/g, '&amp;') + '"';
});
xml = xml.replace(/<mxCell id="([^"]+)" parent="\1"\s*\/>/g, '<mxCell id="$1" parent="0"/>');
fs.writeFileSync(file, xml, 'utf8');
console.log('Local XML repair completed; native file preserved.');
