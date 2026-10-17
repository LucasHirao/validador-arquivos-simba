"""Gerador de arquivos GAB sintéticos (largura fixa) — usado nos diferenciais do fluxo de corretoras."""
import json, pathlib
LAY = json.load(open(pathlib.Path(__file__).parent / "gab_layouts.json"))

def linha(layout, **v):
    out = ""
    for nome, tam in LAY[layout]:
        x = str(v.get(nome, ""))
        out += (x.ljust(tam) if nome not in NUM else x.rjust(tam, "0"))[:tam] if len(x) <= tam else x
    return out

NUM = {"CODIGO_DO_CLIENTE", "DOCUMENTO_DO_CLIENTE", "CODIGO_DO_CLIENTE_MERCADO", "CODIGO_DA_ATIVIDADE", "VALOR_DO_LANCAMENTO", "SALDO_INICIAL"}

def caso_v1():
    g109 = linha("CamposGab109", DATA_DO_MOVIMENTO="20200315", DATA_DE_REFERENCIA="20200315", CODIGO_DO_CLIENTE="1", DATA_DE_LIQUIDACAO="20200317",
                 DESCRICAO_DO_LANCAMENTO="COMPRA TESTE", VALOR_DO_LANCAMENTO="10000", NATUREZA_LANCAMENTO="C")
    g112 = linha("CamposGab112", CODIGO_DO_CLIENTE="1", DOCUMENTO_DO_CLIENTE="52998224725", DATA_DA_CRIACAO="20200101", DATA_DA_ATUALIZACAO="20200101",
                 DATA_NASCIMENTO_FUNDACAO="19800101", TIPO_DE_PESSOA="F", NOME_DO_CLIENTE="FULANO DE TESTE", DOCUMENTO_DE_IDENTIFICACAO="1234567",
                 CODIGO_DO_CLIENTE_MERCADO="1", ENDERECO="RUA TESTE", NUMERO_E_COMPLEMENTO="100", BAIRRO="CENTRO", CIDADE="BRASILIA", ESTADO="DF",
                 CEP="70000", COMPLEMENTO_DO_CEP="000", CONSTANTE_0000A="0000A", CODIGO_DA_ATIVIDADE="1", MERCADO="BOVESPA")
    g800 = linha("CamposGab800", CODIGO_DO_CLIENTE="1", NOME_DO_CLIENTE="FULANO DE TESTE", ENDERECO="RUA TESTE", ENDERECO_COMPLEMENTO="APTO 1",
                 DATA_DE_REFERENCIA="20200315", DATA_DE_LIQUIDACAO="20200317", DESCRICAO_DO_LANCAMENTO="SALDO", SALDO_INICIAL="10000",
                 NATUREZA_DO_SALDO_INICIAL="C", VALOR_DO_LANCAMENTO="500", NATUREZA_DO_LANCAMENTO="D")
    return dict(GAB109=[g109], GAB112=[g112], GAB800=[g800])

def escreve(d, caso, arqs, layout=1):
    d = pathlib.Path(d); d.mkdir(parents=True, exist_ok=True)
    for k, ls in arqs.items():
        (d / f"{caso}_{k}.txt").write_bytes(("\r\n".join(ls) + ("\r\n" if ls else "")).encode("iso-8859-1"))
    (d / "layout.txt").write_text(str(layout))
    (d / "investigados.tsv").write_text("1\t52998224725\tFULANO DE TESTE\t1\t1\t\t\n", encoding="iso-8859-1")
