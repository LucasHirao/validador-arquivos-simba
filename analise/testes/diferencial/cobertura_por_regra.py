"""Mapa de cobertura por regra: para cada ID do catálogo observável por mensagem, em quantos cenários o ORIGINAL emitiu a mensagem
com saída idêntica (multiconjunto) à da reimplementação COMPAT. Promove um caso mínimo por regra para <golden>/<ID>/ (esperado.txt = saída do ORIGINAL).
Uso: python3 cobertura_por_regra.py <OUT> <CORPORA> <golden_dir> <saida.json>"""
import collections, glob, json, os, re, shutil, sys

OUT, CORP, GOLD, JSON = sys.argv[1:5]
EXTRA = {"l01-agencia-nao-numerica": ["L-AG-07"], "l02-od-chave-nao-numerica": ["L-OD-00"], "l07-fail-fast-carga": ["E-07"]}  # sem mensagem própria
PRIO = ["cobertura", "lacunas", "golden", "dirigido", "cruz1", "cruz2", "cruz3"]

def mapa(rid, arq, msg):
    if rid == "-": return []
    if rid == "L-NEG":
        if msg.startswith("Agencia não localizada"): return ["L-CT-00"]
        if msg.startswith("Valor numérico inválido"): return ["L-PE-00"]
        if re.match(r"A DATA_INICIO_RELACIONAMENTO \(.*\) é inválida ou", msg): return ["L-TI-02"]
        if re.match(r"A DATA_FIM_RELACIONAMENTO \(.*\) é inválida ou", msg): return ["L-TI-03"]
        if re.match(r"A DATA_(INICIO|FIM)_RELACIONAMENTO é inválida:", msg): return ["L-TI-06"]
        if re.match(r"O (código COMPE|número da agência|número da conta|tipo de conta)|Conta não localizada\. Banco|Pessoa não localizada", msg): return ["L-TI-00"]
        return ["?" + msg[:30]]
    if rid == "L-NUM": return ["L-OD-00"] if "ORIGEM_DESTINO" in arq else ["L-EX-00"]
    if rid == "L-UNK": return ["E-13"]
    return [rid]

obs = collections.defaultdict(list)       # regra -> [(corpus, cenario, n_linhas_erro)]
for tsv in sorted(glob.glob(f"{OUT}/*.regras.tsv")):
    c = os.path.basename(tsv).split(".")[0]
    por_cen = collections.defaultdict(list)
    for l in open(tsv, encoding="utf8"):
        p = l.rstrip("\n").split("\t")
        if len(p) == 5: por_cen[p[0]].append(p[1:])
    for n, vs in por_cen.items():
        try:
            a = sorted(open(f"{OUT}/{c}.saidas/{n}.orig", encoding="utf8").read().splitlines())
            b = sorted(open(f"{OUT}/{c}.saidas/{n}.compat", encoding="utf8").read().splitlines())
        except FileNotFoundError: continue
        if a != b: continue
        regras = set(EXTRA.get(n, []))
        for rid, arq, lin, msg in vs: regras.update(mapa(rid, arq, msg))
        nerr = sum(1 for x in vs if x[0] != "-")
        for r in regras: obs[r].append((c, n, nerr))

res = {}
shutil.rmtree(GOLD, ignore_errors=True); os.makedirs(GOLD)
for r, lst in sorted(obs.items()):
    if r.startswith("?"): continue
    lst.sort(key=lambda x: (x[2], PRIO.index(x[0]) if x[0] in PRIO else 99, x[1]))
    c, n, nerr = lst[0]
    d = f"{GOLD}/{r}"; os.makedirs(d)
    for f in os.listdir(f"{CORP}/{c}/{n}"):
        if f.endswith(".txt") and f != "esperado.txt" or f == "investigados.tsv": shutil.copy(f"{CORP}/{c}/{n}/{f}", d)
    shutil.copy(f"{OUT}/{c}.saidas/{n}.orig", f"{d}/esperado.txt")
    open(f"{d}/ORIGEM.md", "w").write(f"corpus={c} cenario={n} linhas_de_erro={nerr}\n")
    res[r] = dict(cenarios=len(lst), caso_golden=f"{r}", origem=f"{c}/{n}", erros_no_caso=nerr)
json.dump(res, open(JSON, "w"), indent=1, ensure_ascii=False)
print(len(res), "regras observadas;", sum(v["cenarios"] for v in res.values()), "ocorrências")
