"""Exporta os .md de docs/ para HTML, DOCX e PDF (offline, via LibreOffice). Uso: python3 exportar.py [saida=export]"""
import html, pathlib, re, subprocess, sys

AQUI = pathlib.Path(__file__).resolve().parent
SAIDA = AQUI / (sys.argv[1] if len(sys.argv) > 1 else "export"); SAIDA.mkdir(exist_ok=True)
DOCS = ["CATALOGO_REGRAS", "DOCUMENTACAO_FUNCIONAL", "DOCUMENTACAO_TECNICA", "MAPA_COBERTURA_REGRAS", "REVISAO_DOCUMENTACAO", "REVISAO_DOCUMENTACAO_2"]
CSS = """body{font-family:Arial,Helvetica,sans-serif;font-size:10pt;line-height:1.35}h1{font-size:18pt}h2{font-size:14pt;border-bottom:1px solid #999}h3{font-size:12pt}
table{border-collapse:collapse;width:100%;margin:6pt 0}th,td{border:1px solid #888;padding:2pt 4pt;vertical-align:top;font-size:8pt}th{background:#e8e8e8}
pre{background:#f3f3f3;padding:4pt;font-size:8pt;white-space:pre-wrap}code{font-family:Courier New,monospace;font-size:9pt}"""

def inline(s):
    s = html.escape(s, quote=False)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", s)
    s = re.sub(r"(?<![\w*])\*([^*\s][^*]*)\*(?![\w*])", r"<i>\1</i>", s)
    return re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', s)

def celulas(l):
    l = l.strip().strip("|")
    return [c.strip().replace("\\|", "|") for c in re.split(r"(?<!\\)\|", l)]

def md2html(md):
    out, lin, i = [], md.split("\n"), 0
    while i < len(lin):
        l = lin[i]
        if l.startswith("```"):
            j = i + 1; buf = []
            while j < len(lin) and not lin[j].startswith("```"): buf.append(lin[j]); j += 1
            out.append("<pre>" + html.escape("\n".join(buf)) + "</pre>"); i = j + 1; continue
        if l.startswith("|") and i + 1 < len(lin) and re.match(r"^\|[\s:|-]+\|?$", lin[i + 1].strip()):
            cab = celulas(l); i += 2; rows = []
            while i < len(lin) and lin[i].startswith("|"): rows.append(celulas(lin[i])); i += 1
            out.append("<table><tr>" + "".join(f"<th>{inline(c)}</th>" for c in cab) + "</tr>" +
                       "".join("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>" for r in rows) + "</table>"); continue
        m = re.match(r"^(#{1,6}) (.*)", l)
        if m: out.append(f"<h{len(m.group(1))}>{inline(m.group(2))}</h{len(m.group(1))}>"); i += 1; continue
        if re.match(r"^---+$", l): out.append("<hr/>"); i += 1; continue
        if re.match(r"^\s*([-*]|\d+\.) ", l):
            tag = "ol" if re.match(r"^\s*\d+\.", l) else "ul"; itens = []
            while i < len(lin) and re.match(r"^\s*([-*]|\d+\.) ", lin[i]):
                ind = len(lin[i]) - len(lin[i].lstrip())
                itens.append(f'<li style="margin-left:{ind * 8}pt">{inline(re.sub(r"^\s*([-*]|\d+\.) ", "", lin[i]))}</li>'); i += 1
            out.append(f"<{tag}>" + "".join(itens) + f"</{tag}>"); continue
        if l.startswith(">"): out.append("<blockquote>" + inline(l.lstrip("> ")) + "</blockquote>"); i += 1; continue
        if l.strip(): out.append("<p>" + inline(l) + "</p>")
        i += 1
    return "\n".join(out)

for nome in DOCS:
    md = (AQUI / f"{nome}.md").read_text(encoding="utf-8")
    h = SAIDA / f"{nome}.html"
    h.write_text(f'<!doctype html><html><head><meta charset="utf-8"><title>{nome}</title><style>{CSS}</style></head><body>{md2html(md)}</body></html>', encoding="utf-8")
    subprocess.run(["soffice", "--headless", "--convert-to", "docx:MS Word 2007 XML", "--outdir", str(SAIDA), str(h)], check=True, capture_output=True)
    subprocess.run(["soffice", "--headless", "--convert-to", "pdf:writer_web_pdf_Export", "--outdir", str(SAIDA), str(h)], check=True, capture_output=True)
    print(nome, "ok")
