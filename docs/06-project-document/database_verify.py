"""数据库说明书的目录页码、逐页文字边界及全页栅格检查。"""
from pathlib import Path
import hashlib
import json
import pymupdf as fitz
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
TMP = ROOT / '.tmp/database-document'
PDF = ROOT / 'output/pdf/RBAC权限管理系统数据库设计说明书.pdf'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify():
    doc = fitz.open(PDF)
    toc = doc.get_toc()
    chapters = [v for v in toc if v[0] == 1]
    assert len(chapters) == 7, chapters
    cover = doc[0]
    cover.add_redact_annot(fitz.Rect(0, cover.rect.height-35, cover.rect.width, cover.rect.height), fill=(1,1,1))
    cover.apply_redactions(images=0, graphics=0, text=0)
    assert ''.join(cover.get_text().split()) == 'RBAC权限管理系统数据库设计说明书'
    page = doc[1]
    font_path = 'C:/Windows/Fonts/simfang.ttf'
    font = fitz.Font(fontfile=font_path)
    page.insert_font(fontname='FangSong', fontfile=font_path)
    right = page.rect.width-46
    for _, title, number in chapters:
        matches = page.search_for(title)
        assert len(matches) == 1, (title, matches)
        rect = matches[0]
        if rect.x1+12 < right-22:
            page.draw_line((rect.x1+9,rect.y1-3),(right-22,rect.y1-3),color=(.7,.7,.7),width=.35,dashes='[1 3] 0')
        label = str(number)
        page.insert_text((right-font.text_length(label,fontsize=10),rect.y1-2),label,fontsize=10,fontname='FangSong',color=(0,0,0))
    doc.set_metadata({'title':'RBAC权限管理系统数据库设计说明书','author':'项目小组 A / B / C / D',
                      'subject':'关系模型、ER图、物理字段及实际事务机制','keywords':'RBAC,MySQL,数据库设计,ER,AsciiDoc',
                      'creator':'Asciidoctor.js + local Edge','producer':'Microsoft Edge / PyMuPDF'})
    doc.subset_fonts()
    finalized = TMP / 'finalized.pdf'
    doc.save(finalized,garbage=4,deflate=True)
    doc.close()
    finalized.replace(PDF)
    doc = fitz.open(PDF)
    sources = json.loads((HERE/'database-sources.json').read_text(encoding='utf-8'))
    for item in sources['sources']:
        assert digest(ROOT/item['path']) == item['sha256'], item['path']
    model = json.loads((ROOT/'docs/04-database/schema-model.json').read_text(encoding='utf-8'))
    adoc = (HERE/'数据库设计说明书.adoc').read_text(encoding='utf-8')
    for table in model:
        assert f"`{table['name']}`：" in adoc, table['name']
        for column in table['columns']:
            assert f"|`{column['name']}`" in adoc
    text = '\n'.join(p.get_text() for p in doc)
    assert '附录 A' in text and '附录 B' not in text
    assert '\ufffd' not in text
    figures=[]
    for idx,page in enumerate(doc):
        for image in page.get_image_info(xrefs=True):
            rect=fitz.Rect(image['bbox'])
            assert abs(rect.width-(210-32)*72/25.4)<2, (idx+1,rect)
            figures.append((idx+1,image))
    assert len(figures)==4, figures
    for (_,image),fig in zip(figures,sources['figures']):
        actual=fitz.Pixmap(doc,image['xref'])
        expected=fitz.Pixmap(str(HERE/'images'/fig['file']))
        assert (actual.width,actual.height,actual.samples)==(expected.width,expected.height,expected.samples),fig['file']
    qa = TMP/'qa'
    qa.mkdir(parents=True,exist_ok=True)
    bounds=[]; nonblack=[]; blank=[]; renders=[]
    for index,page in enumerate(doc):
        spans=[s for block in page.get_text('dict')['blocks'] if block.get('type')==0
               for line in block['lines'] for s in line['spans'] if s['text'].strip()]
        if len(page.get_text().strip())<15:
            blank.append(index+1)
        for s in spans:
            r=fitz.Rect(s['bbox'])
            if r.x0 < -1 or r.y0 < -1 or r.x1 > page.rect.width+1 or r.y1 > page.rect.height+1:
                bounds.append({'page':index+1,'text':s['text'],'rect':list(r)})
            if s['color'] != 0:
                nonblack.append({'page':index+1,'text':s['text'],'color':s['color']})
        file=qa/f'page-{index+1:02}.png'
        page.get_pixmap(matrix=fitz.Matrix(1,1),alpha=False).save(file)
        renders.append(file)
    assert not bounds, bounds
    assert not nonblack, nonblack
    assert not blank, blank
    # 全页缩略拼图用于人工检查分页、表格断行及空白。
    for start in range(0,len(renders),12):
        sheet=Image.new('RGB',(1000,4*390),'#dddddd')
        draw=ImageDraw.Draw(sheet)
        for offset,file in enumerate(renders[start:start+12]):
            thumb=Image.open(file).convert('RGB');thumb.thumbnail((310,360))
            x=(offset%3)*333+11;y=(offset//3)*390+22
            sheet.paste(thumb,(x,y));draw.text((x,y-17),f'Page {start+offset+1}',fill='black')
        sheet.save(qa/f'contact-{start//12+1}.png')
    result={'status':'PASSED','pages':len(doc),'bookmarks':len(doc.get_toc()),'chapters':len(chapters),
            'tables':sources['tables'],'fields':sources['columns'],'embeddedErFigures':len(figures),
            'figurePages':[p for p,_ in figures],'migrationColumnsMatch':True,
            'allTextBlack':True,'outOfPageText':bounds,'blankPages':blank,
            'pdfSha256':digest(PDF),'adocSha256':digest(HERE/'数据库设计说明书.adoc'),
            'htmlSha256':digest(ROOT/'output/html/RBAC权限管理系统数据库设计说明书.html')}
    (TMP/'validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    doc.close()
    print(json.dumps(result,ensure_ascii=False))


if __name__=='__main__':
    verify()
