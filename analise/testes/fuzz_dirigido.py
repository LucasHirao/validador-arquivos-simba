"""Cenários dirigidos às regras com limiares (TED/DOC, mesmo banco, espécie, eleitoral, CPF/CNPJ de OD) e a regras de titularidade.
Gera /tmp/fuzz_dirigido/ com esperado.txt (modelo). Uso: python3 fuzz_dirigido.py [N=600] [semente=5]"""
import pathlib, random, shutil, sys
import test_caracterizacao as t
import validador_ref as r
from gerar_casos import comparavel

N = int(sys.argv[1]) if len(sys.argv) > 1 else 600
rnd = random.Random(int(sys.argv[2]) if len(sys.argv) > 2 else 5)
SAIDA = pathlib.Path("/tmp/fuzz_dirigido"); shutil.rmtree(SAIDA, ignore_errors=True); SAIDA.mkdir()
ORDEM = ["AGENCIAS", "CONTAS", "TITULARES", "EXTRATO", "ORIGEM_DESTINO"]
DEB, CRE = [117, 120, 123, 101, 127], [201, 209, 213, 220, 223]
VALORES = [9999, 10000, 999999, 1000000, 1000001, 4999999, 5000000, 9999999, 10000000]
DATAS = ["31122005", "01012006", "30062012", "01072012", "31122012", "01012013", "02012013", "30062013", "01072013", "15032020"]
CPFS = ["", t.CPF_SINT, "52998224724", "11222333000181", "11222333000182", "12ABC34501DE35", "123", "00052998224725", "1122233300018", "52998224725"]

def od_linha(chave, extr, valor, ident):
    banco, ag, conta, tp, cpf, nome, obs = ident
    return t.L(str(chave), str(extr), str(valor), "DOC", banco, ag, conta, "", tp, cpf, nome, "", "", "", "", "", "0", obs)

def cenario():
    a = t.caso_minimo()
    c = a["CONTAS"][0].split("\t"); c[4] = "01011990"; a["CONTAS"][0] = "\t".join(c)
    exts, ods = [], []
    for k in range(1, rnd.choice([2, 3, 4]) + 1):
        tipo = rnd.choice(DEB + CRE); nat = "D" if tipo in DEB else "C"
        if rnd.random() < 0.08: nat = rnd.choice(["C", "D", "*"])
        valor = rnd.choice(VALORES); data = rnd.choice(DATAS)
        exts.append(t.L(str(k), "001", "0001", "12345", "1", data, f"D{k}", "LANC", str(tipo), str(valor), nat, str(valor), "C", "AG"))
        partes = [valor] if rnd.random() < 0.8 else [valor // 2, valor - valor // 2]
        if rnd.random() < 0.06: partes = [valor + 1]
        for j, p in enumerate(partes):
            ident = (rnd.choice(["", "237", "000", "001"]), rnd.choice(["", "9999", "0", "1234", "0000"]),
                     rnd.choice(["", "55555", "00000", "99999999999999999999", "0"]), rnd.choice(["", "1", "2"]), rnd.choice(CPFS),
                     rnd.choice(["", "NOME OD"]), rnd.choice(["", "NAO-CORRENTISTA", "nao-correntista", "099 - NEGATIVA DE IDENTIFICACAO DO PORTADOR", "x"]))
            ods.append(od_linha(len(ods) + 1, k, p, ident))
    a["EXTRATO"], a["ORIGEM_DESTINO"] = exts, ods
    if rnd.random() < 0.3:   # segundo titular T (R-CT-07) ou tipo diferente
        tl = a["TITULARES"][0].split("\t"); tl[4] = rnd.choice(["T", "1", "R"]); tl[5] = rnd.choice(["0", "1"])
        tl[7] = rnd.choice([t.CPF_SINT, "11222333000181"]); tl[6] = "1" if tl[7] == t.CPF_SINT else "2"
        a["TITULARES"].append("\t".join(tl))
    if rnd.random() < 0.2:
        cc = a["CONTAS"][0].split("\t"); cc[6] = rnd.choice(["1", "2", "3"]); a["CONTAS"][0] = "\t".join(cc)
    if rnd.random() < 0.15:   # titular com dados faltando (R-TI-01)
        tl = a["TITULARES"][0].split("\t"); tl[rnd.choice([9, 10, 11, 12, 13, 14, 15, 16])] = ""; a["TITULARES"][0] = "\t".join(tl)
    return a

for i in range(N):
    a = cenario(); ele = rnd.random() < 0.3
    caso = "001-MPE-000001-00" if ele else "001-TST-000001-00"
    d = SAIDA / f"d{i:04d}"; d.mkdir()
    for tipo in ORDEM:
        (d / f"{caso}_{tipo}.txt").write_bytes(("\r\n".join(a[tipo]) + "\r\n").encode("iso-8859-1"))
    (d / "investigados.tsv").write_text(f"1\t{t.CPF_SINT}\tFULANO\t1\t1\t\t\n", encoding="iso-8859-1")
    st, res = t.rodar(a, r.Ctx(eleitoral=ele, numero_banco_atendimento=1), nome_caso=caso)
    (d / "esperado.txt").write_text(comparavel(st, res), encoding="utf-8")
print("gerados", N)
