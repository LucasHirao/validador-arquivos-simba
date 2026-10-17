"""Gera casos sintéticos em casos/<cenario>/ (arquivos .txt ISO-8859-1 + esperado.json) a partir do modelo de referência.

Uso: python3 gerar_casos.py [diretório_saída]     (padrão: ./casos)
Os arquivos servem como ENTRADA da futura execução diferencial (em ambiente isolado e autorizado).
esperado.json = saída do MODELO (hipótese), não do aplicativo real.
"""
import json
import pathlib
import sys

import test_caracterizacao as t
import validador_ref as r
from validador_ref import Ctx

ORDEM = ["AGENCIAS", "CONTAS", "TITULARES", "EXTRATO", "ORIGEM_DESTINO"]


def mut(a, arq, idx, valor, linha=0):
    c = a[arq][linha].split("\t")
    c[idx] = valor
    a[arq][linha] = "\t".join(c)
    return a


def cenarios():
    base = t.caso_minimo
    yield "00-valido", base(), False
    yield "01-colunas-agencia", {**base(), "AGENCIAS": ["001\t0001\tX"]}, False
    yield "02-primeiro-campo-vazio", {**base(), "AGENCIAS": [base()["AGENCIAS"][0][3:]]}, False
    yield "03-uf-invalida", mut(base(), "AGENCIAS", 5, "XX"), False
    yield "04-banco-vazio", mut(base(), "AGENCIAS", 0, " "), False
    yield "05-conta-tipo-invalido", mut(base(), "CONTAS", 3, "5"), False
    yield "06-cpf-invalido", mut(base(), "TITULARES", 7, "52998224724"), False
    yield "07-cpf-so-zeros", mut(base(), "TITULARES", 7, "00000000000"), False
    yield "08-extrato-cnab-invalido", mut(mut(base(), "EXTRATO", 8, "101"), "EXTRATO", 10, "C"), False
    yield "09-conta-mov2-com-extrato", mut(base(), "CONTAS", 6, "2"), False
    yield "10-extrato-fora-periodo", mut(base(), "EXTRATO", 5, "15032005"), False
    yield "11-valor-extrato-vs-od", mut(base(), "ORIGEM_DESTINO", 2, "40000"), False
    a = base(); a["AGENCIAS"] = a["AGENCIAS"] * 2
    yield "12-agencia-duplicada", a, False
    yield "13-eleitoral-deposito-sem-cpf", base(), True
    a = mut(mut(mut(base(), "CONTAS", 4, "01011990"), "EXTRATO", 8, "120"), "EXTRATO", 10, "D")
    a = mut(mut(a, "EXTRATO", 9, "1000000"), "EXTRATO", 11, "1000000")
    a = mut(a, "ORIGEM_DESTINO", 2, "1000000")
    yield "14-ted-sem-identificacao", a, False
    yield "15-od-tipo-pessoa-invalido", mut(base(), "ORIGEM_DESTINO", 8, "3"), False


def comparavel(st, res):
    """Mesmo formato que Reports.comparavel (Java): linhas de erro + '#CONTEUDO ...'."""
    linhas = [e.pretty() for e in st.erros]
    if res is None:
        linhas.append("#CONTEUDO null")
    else:
        linhas.append("#CONTEUDO " + ",".join(f"{k}={str(v).lower()}" for k, v in res.items()))
    return "\n".join(linhas) + "\n"


def main():
    saida = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "casos")
    for nome, arqs, eleitoral in cenarios():
        d = saida / nome
        d.mkdir(parents=True, exist_ok=True)
        caso = "001-MPE-000001-00" if eleitoral else "001-TST-000001-00"
        for tipo in ORDEM:
            (d / f"{caso}_{tipo}.txt").write_bytes(("\r\n".join(arqs[tipo]) + ("\r\n" if arqs[tipo] else "")).encode("iso-8859-1"))
        st, res = t.rodar(arqs, Ctx(eleitoral=eleitoral, numero_banco_atendimento=1))
        (d / "investigados.tsv").write_text(f"1\t{t.CPF_SINT}\tFULANO DE TESTE\t1\t1\t\t\n", encoding="iso-8859-1")
        (d / "esperado.txt").write_text(comparavel(st, res), encoding="utf-8")
        (d / "esperado.json").write_text(json.dumps(
            dict(caso=caso, eleitoral=eleitoral, fonte="modelo de referencia (hipotese, nao confirmado por execucao)",
                 conteudo=res, erros=[e.pretty() for e in st.erros]), ensure_ascii=False, indent=1), encoding="utf-8")
    print("gerados:", len(list(saida.iterdir())), "cenarios em", saida)


if __name__ == "__main__":
    main()
