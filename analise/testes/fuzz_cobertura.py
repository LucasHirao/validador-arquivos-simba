"""Cenários DETERMINÍSTICOS dirigidos aos ramos que a cobertura (JaCoCo) mostrou nunca executados + cenários de volume.
Gera /tmp/fuzz_cobertura/<nome>/ . Pequenos: com esperado.txt (modelo). Grandes (--grande): sem esperado (original x Java)."""
import pathlib, shutil, sys
import test_caracterizacao as t
import validador_ref as r
from gerar_casos import comparavel

SAIDA = pathlib.Path("/tmp/fuzz_cobertura"); shutil.rmtree(SAIDA, ignore_errors=True); SAIDA.mkdir()
ORDEM = ["AGENCIAS", "CONTAS", "TITULARES", "EXTRATO", "ORIGEM_DESTINO"]
CNPJ = "11222333000181"
L = t.L

def mut(a, arq, idx, v, linha=0):
    c = a[arq][linha].split("\t"); c[idx] = v; a[arq][linha] = "\t".join(c); return a

def conta_extra(a, conta, mov, cpf, tipo_pessoa, investigada, titular="T"):
    a["CONTAS"].append(L("001", "0001", conta, "1", "01012010", "", str(mov)))
    a["TITULARES"].append(L("001", "0001", conta, "1", titular, investigada, tipo_pessoa, cpf, "OUTRO", "RG", "1", "RUA", "CID", "DF", "BR", "70000000", "6133334444", "", "", "01012010", ""))
    n = len(a["EXTRATO"]) + 1
    a["EXTRATO"].append(L(str(n), "001", "0001", conta, "1", "16032020", f"D{n}", "LANC", "201", "100", "C", "100", "C", "AG"))
    a["ORIGEM_DESTINO"].append(L(str(n), str(n), "100", f"D{n}", "", "", "", "", "", "", "", "", "", "", "", "", "0", ""))
    return a

def novo(nome, a, ele=False, inv=None, grande=False):
    caso = "001-MPE-000001-00" if ele else "001-TST-000001-00"
    d = SAIDA / nome; d.mkdir()
    for tp in ORDEM:
        with open(d / f"{caso}_{tp}.txt", "wb") as f:
            f.write(("\r\n".join(a[tp]) + ("\r\n" if a[tp] else "")).encode("iso-8859-1"))
    invs = inv if inv is not None else [(t.CPF_SINT, 1, 1)]
    (d / "investigados.tsv").write_text("".join(f"1\t{c}\tFULANO\t{rel}\t{cont}\t\t\n" for c, rel, cont in invs), encoding="iso-8859-1")
    if not grande:
        st, res = t.rodar(a, r.Ctx(eleitoral=ele, numero_banco_atendimento=1),
                          investigados=[dict(cpf=c, teve_rel=rel, teve_conta=cont) for c, rel, cont in invs], nome_caso=caso)
        (d / "esperado.txt").write_text(comparavel(st, res), encoding="utf-8")

B = t.caso_minimo
a = B(); mut(a, "CONTAS", 2, "123-4"); mut(a, "TITULARES", 2, "123-4"); mut(a, "EXTRATO", 3, "123-4"); novo("s01-separador", a)
a = B(); a["CONTAS"] = a["CONTAS"] * 2; novo("s02-conta-duplicada", a)
a = B(); mut(a, "TITULARES", 5, "0"); novo("s03-titular-nao-investigado", a)
a = conta_extra(B(), "777", 3, CNPJ, "2", "0"); novo("s04-extrato-em-conta-mov3", a)
a = B(); a["EXTRATO"] = []; a["ORIGEM_DESTINO"] = []; novo("s05-mov1-sem-extrato", a)
a = B(); mut(a, "CONTAS", 6, "2"); a["EXTRATO"] = []; a["ORIGEM_DESTINO"] = []; novo("s05b-mov2-sem-extrato-valido", a)
novo("s06-investigado-ausente-do-arquivo", B(), inv=[(t.CPF_SINT, 1, 1), (CNPJ, 1, 1)])
a = B(); mut(a, "TITULARES", 4, "1"); novo("s07-investigada-sem-titular-T", a)
a = B(); mut(a, "AGENCIAS", 9, ""); mut(a, "AGENCIAS", 10, "01012020"); novo("s08-fechamento-sem-abertura", a)
a = B(); mut(a, "TITULARES", 2, ""); novo("s09a-titularidade-conta-vazia", a)
a = B(); mut(a, "TITULARES", 1, ""); novo("s09b-titularidade-agencia-vazia", a)
a = B(); mut(a, "TITULARES", 3, ""); novo("s09c-titularidade-tipoconta-vazio", a)
a = B(); mut(a, "EXTRATO", 10, "*"); mut(a, "EXTRATO", 8, "150"); novo("s10-cnab-asterisco", a)
a = B(); mut(a, "EXTRATO", 10, "*"); mut(a, "EXTRATO", 8, "201"); novo("s10b-cnab-asterisco-valido", a)
a = conta_extra(B(), "888", 2, CNPJ, "2", "1", titular="R"); novo("s11-conta-mov2-com-extrato", a, inv=[(t.CPF_SINT, 1, 1), (CNPJ, 1, 1)])

def volume(n):
    a = B()
    a["CONTAS"] = [L("001", "0001", f"1{i:05d}", "1", "01012010", "", "1") for i in range(n)]
    a["TITULARES"] = [L("001", "0001", f"1{i:05d}", "1", "T", "1", "1", t.CPF_SINT, "FULANO DE TESTE", "RG", "1234567", "RUA X", "CIDADE", "DF", "BRASIL", "70000000", "6133334444", "", "", "01012010", "") for i in range(n)]
    a["EXTRATO"] = [L(str(i + 1), "001", "0001", f"1{i:05d}", "1", "15032020", f"D{i}", "DEPOSITO", "201", "100", "C", "100", "C", "AG") for i in range(n)]
    a["ORIGEM_DESTINO"] = [L(str(i + 1), str(i + 1), "100", f"D{i}", "", "", "", "", "", "", "", "", "", "", "", "", "0", "") for i in range(n)]
    return a
novo("v1-lote-1200-valido", volume(1200))
a = volume(1200); mut(a, "EXTRATO", 8, "101", 700); novo("v2-lote-1200-erro-na-linha-701", a)
if "--grande" in sys.argv:
    n = 250_005
    a = B(); a["EXTRATO"] = [L(str(i + 1), "001", "0001", "12345", "1", "15032020", f"D{i}", "DEPOSITO", "201", "100", "C", "100", "C", "AG") for i in range(n)]
    a["ORIGEM_DESTINO"] = [L(str(i + 1), str(i + 1), "100", f"D{i}", "", "", "", "", "", "", "", "", "", "", "", "", "0", "") for i in range(n)]
    novo("v3-commit-250k-valido", a, grande=True)
print("gerados:", len(list(SAIDA.iterdir())))
