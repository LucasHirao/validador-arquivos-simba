"""Cenários que cobrem lacunas do mapa de cobertura por regra (nível 2): L-AG-07, L-OD-00 (E-13), R-EX-03, E-02, E-07.
Uso: python3 gerar_lacunas.py <pasta_saida>   (gera subpastas com os 5 arquivos + investigados.tsv; sem esperado: o esperado vem do original)"""
import pathlib, shutil, sys
import test_caracterizacao as t

ORDEM = ["AGENCIAS", "CONTAS", "TITULARES", "EXTRATO", "ORIGEM_DESTINO"]
L, B = t.L, t.caso_minimo
SAIDA = pathlib.Path(sys.argv[1]); shutil.rmtree(SAIDA, ignore_errors=True); SAIDA.mkdir(parents=True)

def mut(a, arq, idx, v, linha=0):
    c = a[arq][linha].split("\t"); c[idx] = v; a[arq][linha] = "\t".join(c); return a

def novo(nome, a):
    d = SAIDA / nome; d.mkdir()
    for tp in ORDEM:
        (d / f"001-TST-000001-00_{tp}.txt").write_bytes(("\r\n".join(a[tp]) + ("\r\n" if a[tp] else "")).encode("iso-8859-1"))
    (d / "investigados.tsv").write_text(f"1\t{t.CPF_SINT}\tFULANO\t1\t1\t\t\n", encoding="iso-8859-1")

novo("l01-agencia-nao-numerica", mut(B(), "AGENCIAS", 1, "AB"))
novo("l02-od-chave-nao-numerica", mut(B(), "ORIGEM_DESTINO", 0, "X"))
a = B()
a["EXTRATO"].append(L("2", "001", "0001", "12345", "1", "15032020", "DOC2", "DEPOSITO", "201", "700", "C", "700", "C", "AG"))
novo("l03-extrato-sem-origem-destino", a)
a = B(); a["AGENCIAS"] = []; novo("l04-agencias-vazio", a)
a = B(); a["CONTAS"] = []; novo("l05-contas-vazio", a)
a = B(); a["TITULARES"] = []; novo("l06-titulares-vazio", a)
# E-07: erro em AGENCIAS + estrutura inválida em CONTAS; se CONTAS fosse carregado, E-03 apareceria
a = mut(B(), "AGENCIAS", 5, "XX"); a["CONTAS"][0] = "\t".join(a["CONTAS"][0].split("\t")[:3]); novo("l07-fail-fast-carga", a)
print("gerados:", len(list(SAIDA.iterdir())))
