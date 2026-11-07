"""把原型扩展内联，保证用户只打开 index.html 就能体验。"""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
p=r/'prototype/index.html';s=p.read_text(encoding='utf-8')
start='<!-- V14_EXTENSION_START -->';end='<!-- V14_EXTENSION_END -->'
extension=(r/'prototype/v14.js').read_text(encoding='utf-8')
if '</script' in extension.lower():raise ValueError('扩展中不能包含脚本结束标签')
block=start+'<script>\n'+extension+'\n</script>'+end
if start in s:s=s[:s.index(start)]+block+s[s.index(end)+len(end):]
else:s=s.replace('<script src="v14.js"></script>',block)
p.write_text(s,encoding='utf-8')
