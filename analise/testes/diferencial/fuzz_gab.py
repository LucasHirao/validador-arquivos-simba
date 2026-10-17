"""Cenários GAB aleatórios (largura fixa) -> /tmp/fuzz_gab/<cenario>/ (arquivos, layout.txt, investigados.tsv).
Uso: python3 fuzz_gab.py [N=400] [semente=3]"""
import pathlib, random, shutil, sys
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import gab_gen as g

N = int(sys.argv[1]) if len(sys.argv) > 1 else 400
rnd = random.Random(int(sys.argv[2]) if len(sys.argv) > 2 else 3)
SAIDA = pathlib.Path("/tmp/fuzz_gab"); shutil.rmtree(SAIDA, ignore_errors=True); SAIDA.mkdir()
DATAS = ["20200315", "20200230", "19800101", "00000000", "20201301", "abcdefgh", "2020031", "", "20200229", "20210229"]
NUMS = ["1", "2", "3", "0", "12", "-5", "+7", "1.5", "x", "", "99999", "1234567"]
DOCS = ["52998224725", "52998224724", "11222333000181", "11222333000182", "0", "00000000000", "123", "", "1122233300018", "12ABC34501DE35"]
CASO = "001-TST-000001-00"

def base(v):
    L = lambda nome, **kw: g.linha(nome, **kw)
    if v == 1:
        return dict(
            GAB109=[L("CamposGab109", DATA_DO_MOVIMENTO="20200315", DATA_DE_REFERENCIA="20200315", CODIGO_DO_CLIENTE="1", DATA_DE_LIQUIDACAO="20200317", DESCRICAO_DO_LANCAMENTO="COMPRA", VALOR_DO_LANCAMENTO="10000", NATUREZA_LANCAMENTO="C")],
            GAB112=[L("CamposGab112", CODIGO_DO_CLIENTE="1", DOCUMENTO_DO_CLIENTE="52998224725", DATA_DA_CRIACAO="20200101", DATA_DA_ATUALIZACAO="20200101", DATA_NASCIMENTO_FUNDACAO="19800101", TIPO_DE_PESSOA="F", NOME_DO_CLIENTE="FULANO", DOCUMENTO_DE_IDENTIFICACAO="1234567", CODIGO_DO_CLIENTE_MERCADO="1", ENDERECO="RUA", BAIRRO="CENTRO", CIDADE="BSB", ESTADO="DF", CEP="70000", COMPLEMENTO_DO_CEP="000", CONSTANTE_0000A="0000A", CODIGO_DA_ATIVIDADE="1", MERCADO="BOVESPA")],
            GAB800=[L("CamposGab800", CODIGO_DO_CLIENTE="1", NOME_DO_CLIENTE="FULANO", ENDERECO="RUA", DATA_DE_REFERENCIA="20200315", DATA_DE_LIQUIDACAO="20200317", DESCRICAO_DO_LANCAMENTO="SALDO", SALDO_INICIAL="10000", NATUREZA_DO_SALDO_INICIAL="C", VALOR_DO_LANCAMENTO="500", NATUREZA_DO_LANCAMENTO="D")])
    return dict(
        GAB109=[L("CamposGab109Ver2", DATA_DO_MOVIMENTO="20200315", DATA_DE_REFERENCIA="20200315", CODIGO_DO_CLIENTE="1", DATA_DE_LIQUIDACAO="20200317", DESCRICAO_DO_LANCAMENTO="COMPRA", VALOR_DO_LANCAMENTO="10000", NATUREZA_LANCAMENTO="C")],
        GAB112=[L("CamposGab112Ver2", CODIGO_DO_CLIENTE="1", DOCUMENTO_DO_CLIENTE="52998224725", DATA_DA_CRIACAO="20200101", DATA_DA_ATUALIZACAO="20200101", DATA_NASCIMENTO_FUNDACAO="19800101", TIPO_DE_PESSOA="F", NOME_DO_CLIENTE="FULANO", DOCUMENTO_DE_IDENTIFICACAO="1234567", CODIGO_DO_CLIENTE_MERCADO="1", ENDERECO="RUA", BAIRRO="CENTRO", CIDADE="BSB", ESTADO="DF", CEP="70000", COMPLEMENTO_DO_CEP="000", CONSTANTE_0000A="0000A", CODIGO_DA_ATIVIDADE="1", MERCADO="BOVESPA", DATA_SITUACAO="20200101")],
        GAB800=[L("CamposGab800Ver2", CODIGO_DO_CLIENTE="1", NOME_DO_CLIENTE="FULANO", ENDERECO="RUA", DATA_DE_REFERENCIA="20200315", DATA_DE_LIQUIDACAO="20200317", DESCRICAO_DO_LANCAMENTO="SALDO", SALDO_INICIAL="10000", NATUREZA_DO_SALDO_INICIAL="C", VALOR_DO_LANCAMENTO="500", NATUREZA_DO_LANCAMENTO="D")])

POOL = {"DATA": DATAS, "COD": NUMS, "NAT": ["C", "D", "X", "c", ""], "TIPO": ["F", "J", "X", ""], "UF": ["DF", "XX", "df", ""], "CONST": ["0000A", "0000B", ""],
        "MERC": ["BOVESPA", "BMF", "SWAP", "X", ""], "DOC": DOCS, "TXT": ["", "ABC", "TESTE LONGO"]}
EXTRAS_NUM = {"CHAVE_OD", "BANCO_ORIGEM", "AGENCIA_ORIGEM", "CONTA_ORIGEM", "BANCO_DESTINO", "AGENCIA_DESTINO", "CONTA_DESTINO", "MEMBRO_COMPENSACAO", "AGENTE_CUSTODIA", "CODIGO_CORRETORA"}
def tipo_de(nome):
    if nome in EXTRAS_NUM: return "COD"
    if nome == "DATA_SITUACAO": return "DATA"
    if nome.startswith("DATA_D") or nome.startswith("DATA_N"): return "DATA"
    if nome.startswith("NATUREZA"): return "NAT"
    if nome in ("CODIGO_DO_CLIENTE", "CODIGO_DO_CLIENTE_MERCADO", "CODIGO_DA_ATIVIDADE", "VALOR_DO_LANCAMENTO", "SALDO_INICIAL"): return "COD"
    return {"TIPO_DE_PESSOA": "TIPO", "ESTADO": "UF", "CONSTANTE_0000A": "CONST", "MERCADO": "MERC", "DOCUMENTO_DO_CLIENTE": "DOC"}.get(nome, "TXT")

for i in range(N):
    v = rnd.choice([1, 2]); a = base(v)
    lay = {k: g.LAY["CamposGab%s%s" % (k[3:], "Ver2" if v == 2 else "")] for k in a}
    if rnd.random() < 0.3 and v == 1:   # clientes diferentes entre arquivos (regras de conteúdo)
        pass
    for _ in range(rnd.choice([0, 1, 1, 2, 3])):
        k = rnd.choice(list(a)); nome, tam = rnd.choice(lay[k])
        if rnd.random() < 0.06:                       # largura errada
            a[k][0] = a[k][0][:-rnd.randint(1, 3)] if rnd.random() < 0.5 else a[k][0] + "X"
            continue
        # reconstrói a linha com o campo trocado
        pos = 0
        for n2, t2 in lay[k]:
            if n2 == nome: break
            pos += t2
        val = rnd.choice(POOL[tipo_de(nome)])
        campo = (val.rjust(tam, "0") if nome in g.NUM and val and rnd.random() < 0.7 else val.ljust(tam))[:tam]
        a[k][0] = a[k][0][:pos] + campo + a[k][0][pos + tam:]
    if rnd.random() < 0.3:                            # segunda linha em algum arquivo (cliente diferente)
        k = rnd.choice(list(a)); ln = a[k][0]
        cod = rnd.choice(["2", "3"]); pos = lay[k][[n for n, _ in lay[k]].index("CODIGO_DO_CLIENTE") if k != "GAB109" else 2][1] if False else 0
        a[k].append(ln)
    if rnd.random() < 0.08: a[rnd.choice(list(a))] = []
    d = SAIDA / f"g{i:04d}"
    g.escreve(d, CASO, a, v)
    invs = rnd.choice([["52998224725\t1\t1"], ["52998224725\t0\t1"], ["11222333000181\t1\t1"], ["52998224725\t1\t1", "11222333000181\t1\t1"], []])
    (d / "investigados.tsv").write_text("".join(f"1\t{x.split(chr(9))[0]}\tNOME\t{x.split(chr(9))[1]}\t{x.split(chr(9))[2]}\t\t\n" for x in invs), encoding="iso-8859-1")
print("gerados", N)
