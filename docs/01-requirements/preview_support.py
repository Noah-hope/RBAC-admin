"""Version labels for optional requirement previews; never writes image files."""
from pathlib import Path
import html
import json
import re

DOCS = Path(__file__).resolve().parent
FOLDERS = ('usecases-v1', 'usecase-tables-v1', 'flows-v1',
           'activities-v1', 'sequences-v1', 'dataflows-v1')


def status():
    return json.loads((DOCS / 'preview-status.json').read_text(encoding='utf-8'))


def notice(folder=None):
    s = status()
    overrides = s.get('folder_previews', {})
    preview_revision = overrides.get(folder, {}).get('preview_revision', s['preview_revision'])
    if s['source_revision'] == preview_revision:
        return f"正文/图源与预览：{s['source_revision']}。图片为辅助阅读，业务规则以详细用例为准。"
    if folder not in FOLDERS and overrides:
        updated = '、'.join(f"{name}（{entry['preview_revision']}）" for name, entry in overrides.items())
        return (f"当前正文/图源：{s['source_revision']}；已单独更新 PNG/SVG：{updated}；"
                f"其余目录图片仍为 {s['preview_revision']}，可能包含已替代规则，新增子流程图片可能尚未生成。"
                "请以详细用例和 drawio 为准；各目录版本见对应 README。")
    return (f"当前正文/图源：{s['source_revision']}；现有 PNG/SVG：{preview_revision}，"
            "可能包含已替代规则。新增子流程尚未生成图片。请以详细用例和 drawio 为准；预览按需生成。")


def annotate_indexes():
    for path in [DOCS / 'README.md', *(DOCS / f / 'README.md' for f in FOLDERS)]:
        text = path.read_text(encoding='utf-8')
        text = re.sub(r'<!-- preview-status -->.*?<!-- /preview-status -->',
                      '<!-- preview-status -->\n> ' + notice(path.parent.name) + '\n<!-- /preview-status -->',
                      text, flags=re.S)
        if path.read_text(encoding='utf-8') != text:
            path.write_text(text, encoding='utf-8', newline='\n')
    for folder in FOLDERS:
        for path in (DOCS / folder).glob('*.html'):
            text = path.read_text(encoding='utf-8')
            text = re.sub(r'<!-- preview-status -->.*?<!-- /preview-status -->', '', text, flags=re.S)
            banner = ('<!-- preview-status --><aside role="note" style="padding:16px;'
                      'margin:16px;background:#fff4cf;border:1px solid #b48618">'
                      + html.escape(notice(folder)) + '</aside><!-- /preview-status -->')
            title_end = re.search(r'</h1>', text, re.I)
            offset = title_end.end() if title_end else text.find('>') + 1
            text = text[:offset] + banner + text[offset:]

            def missing_image(match):
                src = html.unescape(match.group(1))
                if (path.parent / src).exists():
                    return match.group(0)
                return '<span>尚未生成此页图片，请打开同用例的 drawio 图源。</span>'

            text = re.sub(r'<img\b[^>]*src="([^"]+)"[^>]*>', missing_image, text)
            # A missing optional image must not remain a broken clickable target.
            def missing_link(match):
                target = html.unescape(match.group(1))
                if not target.endswith(('.png', '.svg')) or (path.parent / target).exists():
                    return match.group(0)
                code = re.match(r'UC-\d{2}', target)
                candidates = sorted(path.parent.glob(code.group(0) + '_*.drawio')) if code else []
                return 'href="' + html.escape(candidates[0].name if candidates else '#') + '"'

            text = re.sub(r'href="([^"]+)"', missing_link, text)
            if path.read_text(encoding='utf-8') != text:
                path.write_text(text, encoding='utf-8', newline='\n')
