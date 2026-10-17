"""Teste cruzado: modelo Python (oráculo, modo COMPAT) x implementação Java (--modo compat) em cenários sintéticos aleatórios.
Uso: python3 fuzz_cruzado.py [N=400] [semente=1]   (requer: implementacao/validador-independente/target/classes)"""
import datetime as dt, pathlib, random, shutil, subprocess, sys
import test_caracterizacao as t
import validador_ref as r
from gerar_casos import comparavel

N = int(sys.argv[1]) if len(sys.argv) > 1 else 400
rnd = random.Random(int(sys.argv[2]) if len(sys.argv) > 2 else 1)
RAIZ = pathlib.Path(__file__).resolve().parents[2]
CLASSES = RAIZ / "implementacao/validador-independente/target/classes"
SAIDA = pathlib.Path("/tmp/fuzz_cruzado"); shutil.rmtree(SAIDA, ignore_errors=True); SAIDA.mkdir()
ORDEM = ["AGENCIAS", "CONTAS", "TITULARES", "EXTRATO", "ORIGEM_DESTINO"]
GEN = ["", " ", "x", "0", "00", "9", "1", "2", "3", "-1", "12abc", "ÁÉ", "N" * 25, "9999", "99999999999999999999", "0000"]
DATAS = ["01012010", "31022020", "15032005", "29022024", "00000000", "01011899", "ab012000", "", "31122005", "01012006", "01012013", "02012013", "01072013", "15032021", "99999999"]
NUM = ["", "0", "1", "50000", "40000", "-1", "1000000", "999999", "5000000", "10000", "ABC", "12abc", "117", "120", "123", "201", "999", "101", "116", "230"]
CPFS = ["", t.CPF_SINT, "52998224724", "00000000000", "11222333000181", "12ABC34501DE35", "123", "abc", "11111111111", "52998224725", "0052998224725"]
POOL = {
    "AGENCIAS": {0: ["001", "1", "237", "", "ab"], 1: ["0001", "1", "2", "", "x"], 5: ["DF", "df", "--", "XX", "", "SP"], 9: DATAS, 10: DATAS},
    "CONTAS": {0: ["001", "1", "2", ""], 1: ["0001", "2", "x", ""], 3: ["1", "2", "3", "4", "5", "x", ""], 4: DATAS, 5: DATAS, 6: ["1", "2", "3", "4", "0", "x", ""]},
    "TITULARES": {0: ["001", "2", ""], 1: ["0001", "9", ""], 3: ["1", "2", "9", ""], 4: ["T", "1", "9", "Z", "", "R"], 5: ["0", "1", "2", ""], 6: ["1", "2", "3", ""],
                  7: CPFS, 8: ["", "NOME"], 17: ["", "100", "12.5", "abc", "-1", "1,5"], 18: DATAS, 19: DATAS, 20: DATAS},
    "EXTRATO": {0: ["1", "2", "x", "", "-5"], 1: ["001", "2"], 2: ["0001", "9"], 3: ["12345", "99999", ""], 5: DATAS, 8: NUM, 9: NUM, 10: ["C", "D", "*", "X", ""], 11: NUM, 12: ["C", "D", "X", ""]},
    "ORIGEM_DESTINO": {0: ["1", "2", "x", ""], 1: ["1", "2", "9"], 2: NUM, 4: ["", "237", "000", "ab", "1234"], 5: ["", "9999", "0", "1234", "x"], 6: ["", "55555", "00000", "99999999999999999999"],
                       7: ["", "1", "x"], 8: ["", "1", "2", "3"], 9: CPFS, 10: ["", "NOME"], 16: ["0", "1", ""], 17: ["", "NAO-CORRENTISTA", "nao-correntista", "099 - NEGATIVA DE IDENTIFICACAO DO PORTADOR", "x"]},
}

def cenario(i):
    a = t.caso_minimo()
    # linhas extras para exercitar E-06 e regras entre registros
    if rnd.random() < 0.25:
        a["EXTRATO"].append(t.L("2", "001", "0001", "12345", "1", "16032020", "DOC2", "DEPOSITO", "201", "100", "C", "100", "C", "AG"))
        a["ORIGEM_DESTINO"].append(t.L("2", "2", "100", "DOC2", "", "", "", "", "", "", "", "", "", "", "", "", "0", ""))
    if rnd.random() < 0.15:
        a["CONTAS"].append(t.L("001", "0001", "777", "1", "01012010", "", "3"))
    if rnd.random() < 0.15:
        a["AGENCIAS"] += a["AGENCIAS"]
    for _ in range(rnd.choice([0, 1, 1, 2, 3])):
        arq = rnd.choice(ORDEM)
        if not a[arq]: continue
        li = rnd.randrange(len(a[arq])); c = a[arq][li].split("\t")
        k = rnd.random()
        if k < 0.07: c = c[:-1]
        elif k < 0.12: c = c + ["extra"]
        else:
            idx = rnd.choice(list(POOL[arq]))
            if idx < len(c): c[idx] = rnd.choice(POOL[arq][idx] + GEN[:6])
        a[arq][li] = "\t".join(c)
    eleitoral = rnd.random() < 0.2
    caso = "001-MPE-000001-00" if eleitoral else "001-TST-000001-00"
    # investigado, às vezes com período de afastamento
    ini = fim = None
    if rnd.random() < 0.5:
        ini, fim = dt.date(2020, 1, 1), dt.date(2020, rnd.choice([2, 6]), 28)
    inv = [dict(cpf=t.CPF_SINT, teve_rel=1, teve_conta=1, ini=ini, fim=fim)]
    return a, caso, eleitoral, inv

for i in range(N):
    a, caso, ele, inv = cenario(i)
    d = SAIDA / f"c{i:04d}"; d.mkdir()
    for tipo in ORDEM:
        (d / f"{caso}_{tipo}.txt").write_bytes(("\r\n".join(a[tipo]) + ("\r\n" if a[tipo] else "")).encode("iso-8859-1"))
    fmt = lambda x: x.strftime("%d%m%Y") if x else ""
    (d / "investigados.tsv").write_text(f"1\t{t.CPF_SINT}\tFULANO\t1\t1\t{fmt(inv[0]['ini'])}\t{fmt(inv[0]['fim'])}\n", encoding="iso-8859-1")
    try:
        st, res = t.rodar(a, r.Ctx(eleitoral=ele, numero_banco_atendimento=1), investigados=inv, nome_caso=caso)
        exp = comparavel(st, res)
    except Exception as e:      # o modelo não cobre este caso (ex.: arquivo vazio) — registra e ignora
        exp = None
    (d / "esperado.txt").write_text(exp or "#MODELO_FALHOU\n", encoding="utf-8")

subprocess.run(["java", "-cp", str(CLASSES), "estudo.simba.validador.Cli", "--lote", str(SAIDA), "--modo", "compat"], check=True)
ok = ign = 0; div = []
for d in sorted(SAIDA.iterdir()):
    exp = (d / "esperado.txt").read_text(encoding="utf-8")
    if exp.startswith("#MODELO_FALHOU"): ign += 1; continue
    got = (d / "saida-compat.txt").read_text(encoding="utf-8")
    if got == exp: ok += 1
    else: div.append((d.name, exp, got))
print(f"cenarios={N} iguais={ok} ignorados(modelo)={ign} divergentes={len(div)}")
for nome, e, g in div[:5]:
    print("-----", nome); el, gl = e.splitlines(), g.splitlines()
    for x in el:
        if x not in gl: print("  só no modelo:", x[:230])
    for x in gl:
        if x not in el: print("  só no java  :", x[:230])
sys.exit(1 if div else 0)
