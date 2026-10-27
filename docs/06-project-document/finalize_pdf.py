"""从生成后的真实书签取得页码，补全印刷目录；保留PDF内嵌图片和可点击导航。"""
from pathlib import Path
import json,sys
import pymupdf as fitz

ROOT=Path(__file__).resolve().parents[2]
pdf=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else ROOT/'output/pdf/集中权限管理系统项目设计说明书.pdf'
doc=fitz.open(pdf)
toc=[t for t in doc.get_toc() if t[0]==1]
assert len(toc)==9
page=doc[1];right=page.rect.width-46
font=fitz.Font(fontfile='C:/Windows/Fonts/simfang.ttf')
page.insert_font(fontname='FangSong',fontfile='C:/Windows/Fonts/simfang.ttf')
for _,title,number in toc:
    matches=page.search_for(title)
    assert len(matches)==1,(title,matches)
    rect=matches[0]
    if rect.x1+12<right-22:
        page.draw_line((rect.x1+9,rect.y1-3),(right-22,rect.y1-3),color=(.76,.82,.88),width=.35,dashes='[1 3] 0')
    text=str(number)
    width=font.text_length(text,fontsize=10)
    page.insert_text((right-width,rect.y1-2),text,fontsize=10,fontname='FangSong',color=(0,0,0))
# 封面仅保留两行标题，删除浏览器打印在封面底部的页码及页脚。
cover=doc[0]
cover.add_redact_annot(fitz.Rect(0,cover.rect.height-35,cover.rect.width,cover.rect.height),fill=(1,1,1))
cover.apply_redactions(images=0,graphics=0,text=0)
assert ''.join(cover.get_text().split())=='RBAC权限管理系统项目设计说明书'
doc.set_metadata({'title':'RBAC权限管理系统项目设计说明书','author':'项目小组 A / B / C / D','subject':'需求V1.3、结构化数据流、43表数据库、接口与模块设计','keywords':'RBAC,AsciiDoc,DFD,ER,权限管理','creator':'Asciidoctor.js 3.0.4 + local Edge','producer':'Microsoft Edge / PyMuPDF'})
summary={'pages':len(doc),'tocEntries':len(toc),'embeddedFigures':sum(len(p.get_image_info()) for p in doc)}
doc.subset_fonts()
temporary=ROOT/'.tmp/project-document/finalized.pdf'
doc.save(temporary,garbage=4,deflate=True)
doc.close()
temporary.replace(pdf)
print(json.dumps(summary,ensure_ascii=False))
