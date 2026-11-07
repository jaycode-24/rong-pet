from pathlib import Path
from docx import Document
from docx.shared import Pt, Cm
from docx.oxml.ns import qn
r=Path(__file__).resolve().parents[1]
source=r/'doc/成都宠物本地信任平台PRD-V1.3.docx'
doc=Document(source)
# 保留基线章节，在原位修正明确废止的导航与先支付描述，并追加完整生效规则。
replacements={
 '首页、发布、消息、我的':'首页、爱宠、消息、我的',
 '发布按钮为显著凸起入口，点击弹出发布类型选择':'发布入口位于首页寻宠／招领功能区，不再占底部导航；爱宠提供一键发布入口',
 '入口：个人中心"我的宠物"。支持添加多只宠物，每只宠物一份档案。':'入口：底部导航“爱宠”及个人中心。支持添加多只宠物，每只宠物一份档案，含真实档案与独立AI形象。',
 '支付成功后帖子状态变为"悬赏托管中"，并进入审核队列；审核通过后对外展示。':'帖子先审核，通过后可按普通帖展示；支付成功后才展示悬赏并进入“悬赏托管中”，不再次进入审核队列。',
 '审核通过后双方互见联系方式':'审核通过后按认领规则继续核验，确认后通过站内会话联系，不公开手机号',
 '确认某一申请后双方互见真实联系方式':'确认某一申请后双方通过站内会话沟通，不公开手机号',
}
def all_paragraphs(d):
 yield from d.paragraphs
 for table in d.tables:
  for row in table.rows:
   for cell in row.cells:
    yield from all_paragraphs(cell)
for p in all_paragraphs(doc):
 text=p.text
 for old,new in replacements.items():text=text.replace(old,new)
 if text!=p.text:p.text=text
 # 版本标记不改历史修订中的 V1.3。
 if p.text.startswith('文档版本：V1.3'):p.text=p.text.replace('V1.3','V1.4')
# 文档开头放置优先级说明，避免历史正文中的残留措辞覆盖最新规则。
p=doc.paragraphs[0].insert_paragraph_before('本版生效说明：V1.4新增与修订规则见第17章，涉及导航、站内联系、AI形象、反馈和悬赏退款的旧描述以第17章为准。未修订字段与规则沿用V1.3。')
p.runs[0].font.size=Pt(10)
doc.add_page_break()
doc.add_heading('17 V1.4 确认需求与验收补充',level=1)
md=(r/'doc/V1.4确认需求.md').read_text(encoding='utf-8')
for line in md.splitlines():
 if not line.strip():continue
 if line.startswith('# '):continue
 if line.startswith('## '):doc.add_heading(line[3:],level=2)
 else:doc.add_paragraph(line)
for style in ['Normal','Heading 1','Heading 2']:
 st=doc.styles[style];st.font.name='Microsoft YaHei';st._element.get_or_add_rPr().rFonts.set(qn('w:eastAsia'),'Microsoft YaHei')
doc.save(r/'doc/成都宠物本地信任平台PRD-V1.4.docx')
print('V1.4 DOCX created')
