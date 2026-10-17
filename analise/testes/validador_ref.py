"""Modelo de referência INDEPENDENTE do Validador SIMBA 6.2.6.

Reescrito a partir da leitura estática do código decompilado (analise/decompilado*). Não executa nem
importa nenhum código do aplicativo original. Cada função cita a evidência (classe decompilada) e o ID
da regra do catálogo (analise/04-regras-validacao.md).

STATUS: hipótese fundamentada em leitura de código. A equivalência com o aplicativo real só será
CONFIRMADA por execução diferencial em ambiente isolado e autorizado (ver 05-testes.md).
"""
from __future__ import annotations

import datetime as dt
import re
from dataclasses import dataclass, field

# --------------------------------------------------------------------------------------
# Primitivas Java reproduzidas
# --------------------------------------------------------------------------------------
_JTRIM = "".join(chr(i) for i in range(0x21))  # String.trim() remove chars <= U+0020


def jtrim(s: str) -> str:
    return s.strip(_JTRIM)


def is_empty(s) -> bool:
    """br.mp.mpf.pgr.util.StringUtil.isEmpty: null ou trim vazio."""
    return s is None or jtrim(s) == ""


def split_tab(line: str) -> list[str]:
    """util.Util.tabSeparatedStringToArray = text.split("(?=\\t)") + trim de cada item.

    Verificado com sonda Java independente (SplitProbe): um TAB na posição 0 NÃO gera campo vazio inicial.
    """
    if line == "":
        return [""]
    parts, start = [], 0
    for i, ch in enumerate(line):
        if ch == "\t" and i > 0:
            parts.append(line[start:i])
            start = i
    parts.append(line[start:])
    return [jtrim(p) for p in parts]


def completar_zeros(numero, tamanho):
    """StringUtil.completarZerosEsquerda"""
    if numero is None:
        return None
    return numero.rjust(tamanho, "0") if len(numero) < tamanho else numero


class JavaIndexError(Exception):
    """StringIndexOutOfBoundsException."""


def remover_zeros_esquerda(numero: str) -> str:
    """StringUtil.removerZerosEsquerda: lança exceção para string só de zeros ou vazia."""
    i = 0
    while True:
        if i >= len(numero):
            raise JavaIndexError(numero)
        if numero[i] != "0":
            break
        i += 1
    return numero[i:]


def verifica_data(s: str) -> bool:
    """asspa.client.Util.verificaData: 8 chars, ano >= 1900, ddMMyyyy não-leniente."""
    if len(s) != 8:
        return False
    if not re.fullmatch(r"[+-]?\d+", s[4:8]):
        return False
    if int(s[4:8]) < 1900:
        return False
    if not s.isascii() or not s.isdigit():
        return False
    try:
        dt.date(int(s[4:8]), int(s[2:4]), int(s[0:2]))
    except ValueError:
        return False
    return True


def parse_data(s: str) -> dt.date:
    return dt.date(int(s[4:8]), int(s[2:4]), int(s[0:2]))


# --------------------------------------------------------------------------------------
# CPF / CNPJ  (DigitoVerificador, CpfCnpjUtil)
# --------------------------------------------------------------------------------------
def _somatorio(num: str, base: int | None = None) -> int:
    n = len(num)
    if base is None:
        return sum((ord(c) - 48) * (n + 1 - i) for i, c in enumerate(num))
    return sum((ord(c) - 48) * (base - i) for i, c in enumerate(num))


def _mod11(soma: int) -> str:
    r = soma % 11
    return "0" if r < 2 else str(11 - r)


def dv_cpf(numero: str) -> str:
    d1 = _mod11(_somatorio(numero))
    d2 = _mod11(_somatorio(numero + d1))
    return d1 + d2


def dv_cnpj(numero: str) -> str:
    d1 = _mod11(_somatorio(numero[:4]) + _somatorio(numero[4:]))
    n2 = numero + d1
    d2 = _mod11(_somatorio(n2[:5], 6) + _somatorio(n2[5:]))
    return d1 + d2


def _same_char(t: str) -> bool:
    return all(c == t[0] for c in t)


def validar_dv_cpf(numero: str) -> bool:
    if is_empty(numero):
        return False
    sem, dv = numero[:-2], numero[-2:]
    if is_empty(sem) or is_empty(dv):
        return False
    if _same_char(sem):
        return False
    if re.fullmatch(r"[0-9]{6,}", sem) and re.fullmatch(r"[0-9]{2}", dv):
        return dv == dv_cpf(sem)
    return False


def validar_dv_cnpj(numero: str) -> bool:
    if is_empty(numero):
        return False
    sem, dv = numero[:-2], numero[-2:]
    if is_empty(sem) or is_empty(dv):
        return False
    if _same_char(sem):
        return False
    if re.fullmatch(r"[0-9A-Z]{7,}", sem) and re.fullmatch(r"[0-9]{2}", dv):
        return dv == dv_cnpj(sem)
    return False


def validar_cpf(cpf):
    return validar_dv_cpf(completar_zeros(cpf, 11))


def validar_cnpj(cnpj):
    return validar_dv_cnpj(completar_zeros(cnpj, 14))


# --------------------------------------------------------------------------------------
# CnabRule (retorna True quando o TIPO_LANCAMENTO é INVÁLIDO)
# --------------------------------------------------------------------------------------
def cnab_credito_invalido(c: int) -> bool:
    return (c < 201 or c > 223) and c != 999


def cnab_debito_invalido(c: int) -> bool:
    return (c < 101 or c > 127 or c == 116) and c != 999


def cnab_credito_debito_invalido(c: int) -> bool:
    return (c < 101 or (c > 127 and c < 201) or c > 223 or c == 116) and c != 999


UFS = set("AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO".split())

# --------------------------------------------------------------------------------------
# Layouts (CarregarArquivo*Cmd.getCamposArquivo): (nome, maxLength)
# --------------------------------------------------------------------------------------
LAYOUTS = {
    "AGENCIAS": [("NUMERO_BANCO", 3), ("NUMERO_AGENCIA", 4), ("NOME_AGENCIA", 50), ("ENDERECO_LOGRADOURO", 80),
                 ("ENDERECO_CIDADE", 40), ("ENDERECO_UF", 2), ("ENDERECO_PAIS", 40), ("ENDERECO_CEP", 8),
                 ("TELEFONE_AGENCIA", 20), ("DATA_ABERTURA_AGENCIA", 8), ("DATA_FECHAMENTO_AGENCIA", 8)],
    "CONTAS": [("NUMERO_BANCO", 3), ("NUMERO_AGENCIA", 4), ("NUMERO_CONTA", 20), ("TIPO_CONTA", 1),
               ("DATA_ABERTURA_CONTA", 8), ("DATA_ENCERRAMENTO_CONTA", 8), ("MOVIMENTACAO_CONTA", 1)],
    "TITULARES": [("NUMERO_BANCO", 3), ("NUMERO_AGENCIA", 4), ("NUMERO_CONTA", 20), ("TIPO_CONTA", 1),
                  ("TIPO_TITULAR", 1), ("PESSOA_INVESTIGADA", 1), ("TIPO_PESSOA_TITULAR", 1),
                  ("CPF_CNPJ_TITULAR", 14), ("NOME_TITULAR", 80), ("NOME_DOC_IDENTIFICACAO", 50),
                  ("NUMERO_DOC_IDENTIFICACAO", 20), ("ENDERECO_LOGRADOURO", 80), ("ENDERECO_CIDADE", 40),
                  ("ENDERECO_UF", 2), ("ENDERECO_PAIS", 40), ("ENDERECO_CEP", 8), ("TELEFONE_PESSOA", 20),
                  ("VALOR_RENDA", 14), ("DATA_ATUALIZACAO_RENDA", 8), ("DATA_INICIO_RELACIONAMENTO", 8),
                  ("DATA_FIM_RELACIONAMENTO", 8)],
    "EXTRATO": [("CODIGO_CHAVE_EXTRATO", 18), ("NUMERO_BANCO", 3), ("NUMERO_AGENCIA", 4), ("NUMERO_CONTA", 20),
                ("TIPO_CONTA", 1), ("DATA_LANCAMENTO", 8), ("NUMERO_DOCUMENTO", 20), ("DESCRICAO_LANCAMENTO", 50),
                ("TIPO_LANCAMENTO", 3), ("VALOR_LANCAMENTO", 14), ("NATUREZA_LANCAMENTO", 1), ("VALOR_SALDO", 14),
                ("NATUREZA_SALDO", 1), ("LOCAL_TRANSACAO", 80)],
    "ORIGEM_DESTINO": [("CODIGO_CHAVE_OD", 18), ("CODIGO_CHAVE_EXTRATO", 18), ("VALOR_TRANSACAO", 14),
                       ("NUMERO_DOCUMENTO_TRANSACAO", 20), ("NUMERO_BANCO_OD", 3), ("NUMERO_AGENCIA_OD", 4),
                       ("NUMERO_CONTA_OD", 20), ("TIPO_CONTA_OD", 1), ("TIPO_PESSOA_OD", 1), ("CPF_CNPJ_OD", 14),
                       ("NOME_PESSOA_OD", 80), ("NOME_DOC_IDENTIFICACAO_OD", 50), ("NUMERO_DOC_IDENTIFICACAO_OD", 20),
                       ("CODIGO_DE_BARRAS", 100), ("NOME_ENDOSSANTE_CHEQUE", 80), ("DOC_ENDOSSANTE_CHEQUE", 50),
                       ("SITUACAO_IDENTIFICACAO", 1), ("OBSERVACÃO", 250)],
}
DESCONHECIDO = "Erro desconhecido. Consulte o log do sistema."


class Negocio(Exception):
    """NegocioException: mensagem vai para o relatório e interrompe a linha."""


class Inesperado(Exception):
    """Qualquer outra exceção (NumberFormatException etc.) -> 'Erro desconhecido'."""


@dataclass
class Ctx:
    eleitoral: bool = False          # Context.isEleitoral: caso começa com 001-MPE ou 038-TSE
    numero_banco_atendimento: int = 1


@dataclass
class Erro:
    linha: int
    arquivo: str
    msg: str

    def pretty(self) -> str:
        return f"Erro no arquivo: {self.arquivo}, linha: {self.linha}, erro: {self.msg}"


@dataclass
class Estado:
    """Equivale ao HSQLDB em memória + ErrorReportCmd (lista GLOBAL de erros)."""
    ctx: Ctx
    nome_caso: str = "CASO"
    erros: list[Erro] = field(default_factory=list)
    agencias: list[dict] = field(default_factory=list)
    contas: list[dict] = field(default_factory=list)
    pessoas: list[dict] = field(default_factory=list)
    titularidades: list[dict] = field(default_factory=list)
    extratos: list[dict] = field(default_factory=list)
    ods: list[dict] = field(default_factory=list)
    investigados: list[dict] = field(default_factory=list)

    def rep(self, linha, arquivo, msg):
        self.erros.append(Erro(linha, arquivo, msg))


def _int(s):
    """Integer.valueOf: NumberFormatException -> Inesperado."""
    if not re.fullmatch(r"[+-]?\d+", s):
        raise Inesperado(s)
    return int(s)


def _is_number(s: str) -> bool:
    """Aproximação de StringUtil.isNumber (NumberFormat.parse, LENIENTE e dependente de locale): aceita prefixo numérico."""
    return re.match(r"-?\d", s) is not None


def _get_long(st: Estado, dado, linha, campo, arquivo_abs):
    if _is_number(dado):
        return _int(dado)  # Long.valueOf; '12abc' e '1.000' viram 'Erro desconhecido'
    st.rep(linha, arquivo_abs, f"Campo {campo} não é um número.")
    return 0


# --------------------------------------------------------------------------------------
# Carga linha-a-linha (AbstractCarregarArquivoCmd.carregar)
# --------------------------------------------------------------------------------------
def carregar(st: Estado, nome: str, linhas: list[str], nome_fisico: str | None = None) -> bool:
    """Retorna True se houve erro (CargaInfo.temErro). `nome` ∈ LAYOUTS (tabela PESSOA/TITULARIDADE via TITULARES)."""
    layout_nome = "TITULARES" if nome in ("PESSOA", "TITULARIDADE") else nome
    fn = nome_fisico or f"{st.nome_caso}_{layout_nome}.txt"
    tem_erro = False
    handler = {"AGENCIAS": _agencia, "CONTAS": _conta, "EXTRATO": _extrato, "ORIGEM_DESTINO": _od,
               "PESSOA": _pessoa, "TITULARIDADE": _titularidade}
    campos = LAYOUTS[layout_nome]
    for n, linha in enumerate(linhas, 1):
        dados = split_tab(linha)
        if len(dados) != len(campos):
            st.rep(n, fn, f"Quantidade de colunas inválida. Esperado: {len(campos)} Atual: {len(dados)}")
            tem_erro = True
            continue
        for (cn, mx), d in zip(campos, dados):
            if len(d) > mx:
                st.rep(n, layout_nome, f"Tamanho do campo {cn} excedido. Tamanho máximo permitido: {mx}")
                tem_erro = True
        if st.erros:            # ErrorReportCmd.hasErros() é GLOBAL: após o 1º erro, convert/validar não rodam mais
            tem_erro = True
            continue
        try:
            rec = handler[nome](st, dados, n, layout_nome, fn)
            if st.erros:
                tem_erro = True
                continue
            _inserir(st, nome, rec)
        except Negocio as e:
            st.rep(n, fn, str(e))
            tem_erro = True
        except (Inesperado, JavaIndexError, ValueError, KeyError):
            st.rep(n, fn, DESCONHECIDO)
            tem_erro = True
    return tem_erro


def _inserir(st, nome, rec):
    # PRIMARY KEY/FOREIGN KEY do HSQLDB (Mapa.sqlCriaTabela*): violação => SQLException => "Erro desconhecido" [PROVÁVEL]
    if nome == "EXTRATO" and any(e["chave"] == rec["chave"] for e in st.extratos):
        raise Inesperado("PK EXTRATO")
    if nome == "ORIGEM_DESTINO" and (any(o["chave"] == rec["chave"] for o in st.ods)
                                     or not any(e["chave"] == rec["chave_extrato"] for e in st.extratos)):
        raise Inesperado("PK/FK ORIGEM_DESTINO")
    {"AGENCIAS": st.agencias, "CONTAS": st.contas, "EXTRATO": st.extratos, "ORIGEM_DESTINO": st.ods,
     "PESSOA": st.pessoas, "TITULARIDADE": st.titularidades}[nome].append(rec)


def carregar_titulares(st: Estado, linhas: list[str]) -> bool:
    """CarregarArquivoTitularesCmd: passada PESSOA; se houver erro, TITULARIDADE não é carregada."""
    if carregar(st, "PESSOA", linhas):
        return True
    return carregar(st, "TITULARIDADE", linhas)


# ---- AGENCIAS (CarregarArquivoAgenciasCmd) ------------------------------------------
def _agencia(st, d, n, arq, fn):
    n0 = len(st.erros)
    ctx = st.ctx
    rec = dict(banco=d[0], agencia=d[1], nome=d[2], uf=d[5], abertura=d[9], fechamento=d[10], linha=n)
    # validarCamposObrigatorios [L-AG-01]
    if ctx.eleitoral:
        obrig = [d[0], d[1], d[2]]
        lista = "NUMERO_BANCO, NUMERO_AGENCIA, NOME_AGENCIA"
    else:
        obrig = [d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8]]
        lista = ("NUMERO_BANCO, NUMERO_AGENCIA, NOME_AGENCIA, ENDERECO_LOGRADOURO, ENDERECO_CIDADE, ENDERECO_UF, "
                 "ENDERECO_PAIS, ENDERECO_CEP, TELEFONE_AGENCIA")
    if any(is_empty(x) for x in obrig):
        st.rep(n, arq, f"Um dos campos obrigatórios ({lista}) não está preenchido.")
    # validarDataAberturaAgencia / validarDataFechamentoAgencia [L-AG-02/03]
    for nome, v in (("DATA_ABERTURA_AGENCIA", rec["abertura"]), ("DATA_FECHAMENTO_AGENCIA", rec["fechamento"])):
        if not is_empty(v) and not verifica_data(v):
            st.rep(n, arq, f"A {nome} ({v}) é inválida ou o ano é anterior a 1900.")
    # validarNumeroBanco [L-AG-04]: Integer.valueOf lança exceção para vazio/não numérico
    if ctx.numero_banco_atendimento != _int(rec["banco"]):
        st.rep(n, arq, "NUMERO_BANCO diferente do informado em atendimento.")
    # validarDatas [L-AG-05] (não eleitoral)
    if not ctx.eleitoral and not is_empty(rec["fechamento"]):
        if is_empty(rec["abertura"]):
            st.rep(n, arq, "DATA_FECHAMENTO_AGENCIA informada, mas não há DATA_ABERTURA_AGENCIA.")
        else:
            try:  # SimpleDateFormat padrão é LENIENTE aqui
                a, f = _parse_lenient(rec["abertura"]), _parse_lenient(rec["fechamento"])
                if f < a:
                    st.rep(n, arq, "DATA_ABERTURA_AGENCIA posterior à DATA_FECHAMENTO_AGENCIA.")
            except ValueError:
                st.rep(n, arq, "Data inválida nos campos DATA_ABERTURA_AGENCIA e/ou DATA_FECHAMENTO_AGENCIA.")
    # validarUf [L-AG-06] (não eleitoral; comparação exata, case-sensitive)
    if not ctx.eleitoral and rec["uf"] not in UFS and rec["uf"] != "--":
        st.rep(n, arq, f"ENDERECO_UF:{rec['uf']} não é uma sigla válida.")
    # setParametros (só alcançado se a linha não teve erro): NUMERO_AGENCIA -> Integer.valueOf
    if len(st.erros) == n0 and not is_empty(rec["agencia"]):
        _int(rec["agencia"])
    return rec


def _parse_lenient(s: str) -> int:
    """SimpleDateFormat('ddMMyyyy') LENIENTE (sdf padrão do loader): 31022020 -> 02/03/2020. Devolve nº de dias (sem limite de ano).
    Não numérico -> ValueError."""
    if not re.fullmatch(r"\d{8}", s):
        raise ValueError(s)
    d, m, y = int(s[0:2]), int(s[2:4]), int(s[4:8])
    y += (m - 1) // 12
    m = (m - 1) % 12 + 1
    # dias desde 0000-03-01 (algoritmo days_from_civil de H. Hinnant) para o 1º dia do mês, mais (d-1)
    yy = y - (m <= 2)
    era = yy // 400
    yoe = yy - era * 400
    doy = (153 * (m + (-3 if m > 2 else 9)) + 2) // 5
    doe = yoe * 365 + yoe // 4 - yoe // 100 + doy
    return era * 146097 + doe + (d - 1)


# ---- CONTAS (CarregarArquivoContasCmd) ---------------------------------------------
def _find_agencia(st, banco, agencia):
    b, a = _int(banco), _int(agencia)
    for i, x in enumerate(st.agencias):
        if int(x["banco"]) == b and int(x["agencia"]) == a:
            return i + 1
    return -1


def _conta(st, d, n, arq, fn):
    rec = dict(banco=d[0], agencia=d[1], conta=d[2], tipo=d[3], abertura=d[4], encerramento=d[5], mov=d[6], linha=n)
    idag = _find_agencia(st, d[0], d[1])          # em convert(): lança NegocioException
    if idag == -1:
        raise Negocio(f"Agencia não localizada. Banco: {d[0]} Agencia: {d[1]}")
    rec["idag"] = idag
    if any(is_empty(x) for x in (d[0], d[2], d[3], d[6])):  # [L-CT-01]
        st.rep(n, arq, "Um dos campos obrigatórios (NUMERO_CONTA, TIPO_CONTA, MOVIMENTACAO_CONTA) não está preenchido.")
    for nome, v in (("DATA_ABERTURA_CONTA", d[4]), ("DATA_ENCERRAMENTO_CONTA", d[5])):   # [L-CT-02/03]
        if not is_empty(v) and not verifica_data(v):
            st.rep(n, arq, f"A {nome} ({v}) é inválida ou o ano é anterior a 1900.")
    try:                                                                                  # [L-CT-04]
        if not 1 <= int(d[3]) <= 4 or not re.fullmatch(r"[+-]?\d+", d[3]):
            raise ValueError
    except ValueError:
        st.rep(n, arq, "Tipo de conta inválido. Valores possíveis: 1, 2, 3 e 4.")
    try:                                                                                  # [L-CT-05]
        if not 1 <= int(d[6]) <= 3 or not re.fullmatch(r"[+-]?\d+", d[6]):
            raise ValueError
    except ValueError:
        st.rep(n, arq, "Movimentação de Conta inválido. Valores possíveis: 1, 2 e 3")
    return rec


# ---- EXTRATO (CarregarArquivoExtratoCmd) -------------------------------------------
def _find_conta(st, banco, agencia, conta, tipo):
    # ContaLocalizerCmd.findIdConta(String...): converte banco, agência e tipo para int ANTES de consultar
    _int(banco), _int(agencia), _int(tipo)
    idag = _find_agencia(st, banco, agencia)
    if idag == -1:
        return -1
    t = _int(tipo)
    for i, c in enumerate(st.contas):
        if c["idag"] == idag and c["conta"] == conta and _int(c["tipo"]) == t:
            return i + 1
    return -1


def _extrato(st, d, n, arq, fn):
    rec = dict(chave=_get_long(st, d[0], n, "CODIGO_CHAVE_EXTRATO", fn), data=d[5], doc=d[6], desc=d[7],
               tipo=_get_long(st, d[8], n, "TIPO_LANCAMENTO", fn), valor=_get_long(st, d[9], n, "VALOR_LANCAMENTO", fn),
               nat=d[10], saldo=_get_long(st, d[11], n, "VALOR_SALDO", fn), nat_saldo=d[12], linha=n)
    rec["idconta"] = _find_conta(st, d[1], d[2], d[3], d[4])
    if rec["idconta"] <= 0:                                                                   # [L-EX-01]
        st.rep(n, arq, "Conta não localizada no arquivo de CONTAS.")
    if is_empty(rec["data"]) or is_empty(rec["doc"]) or is_empty(rec["desc"]):                # [L-EX-02]
        st.rep(n, arq, "Um dos campos obrigatórios (DATA_LANCAMENTO, NUMERO_DOCUMENTO, DESCRICAO_LANCAMENTO, "
                       "TIPO_LANCAMENTO, VALOR_LANCAMENTO, NATUREZA_LANCAMENTO, VALOR_SALDO, NATUREZA_SALDO) não está preenchido.")
    if not is_empty(rec["data"]) and not verifica_data(rec["data"]):                          # [L-EX-03]
        st.rep(n, arq, f"A DATA_LANCAMENTO ({rec['data']}) é inválida ou o ano é anterior a 1900.")
    if not re.fullmatch(r"[CD*]", rec["nat"]):                                                # [L-EX-04]
        st.rep(n, arq, "Natureza de Lancamento inválida. Valores possíveis: C, D e *(asterisco).")
    if not re.fullmatch(r"[CD]", rec["nat_saldo"]):                                           # [L-EX-05]
        st.rep(n, arq, "Natureza de saldo inválida. Valores possíveis: C e D.")
    t = rec["tipo"]
    if rec["nat"] == "C" and cnab_credito_invalido(t):                                        # [L-EX-06]
        st.rep(n, arq, "TIPO_LANCAMENTO inválido para NATUREZA_LANCAMENTO='C'. Veja tabela CNAB-240.")
    if rec["nat"] == "D" and cnab_debito_invalido(t):
        st.rep(n, arq, "TIPO_LANCAMENTO inválido para NATUREZA_LANCAMENTO='D'. Veja tabela CNAB-240.")
    if rec["nat"] == "*" and cnab_credito_debito_invalido(t):
        st.rep(n, arq, "TIPO_LANCAMENTO inválido para NATUREZA_LANCAMENTO='*'. Veja tabela CNAB-240.")
    if rec["valor"] < 0 or rec["saldo"] < 0:                                                  # [L-EX-07]
        st.rep(n, arq, "VALOR_LANCAMENTO e VALOR_SALDO não podem ser negativos.")
    return rec


# ---- TITULARES: PESSOA e TITULARIDADE ----------------------------------------------
def _pessoa(st, d, n, arq, fn):
    try:
        renda = None if is_empty(d[17]) else float(d[17])
    except ValueError:
        raise Negocio(f"Valor numérico inválido no campo Valor Renda: {d[17]}")
    rec = dict(tipo=d[6], cpf=d[7].upper(), nome=d[8], logradouro=d[11], cidade=d[12], uf=d[13], pais=d[14],
               cep=d[15], tel=d[16], doc_nome=d[9], doc_num=d[10], renda=renda, data_renda=d[18], linha=n)
    if is_empty(rec["tipo"]) or is_empty(rec["cpf"]) or is_empty(rec["nome"]):                # [L-PE-01]
        st.rep(n, arq, "Um dos campos obrigatórios (TIPO_PESSOA, CPF_CNPJ_PESSOA, NOME_PESSOA, PESSOA_INVESTIGADA, "
                       "DATA_INICIO_RELACIONAMENTO) não está preenchido.")
    if not is_empty(rec["data_renda"]) and not verifica_data(rec["data_renda"]):              # [L-PE-02] (atribuído a EXTRATO!)
        st.rep(n, "EXTRATO", f"A DATA_ATUALIZACAO_RENDA ({rec['data_renda']}) é inválida ou o ano é anterior a 1900.")
    if rec["tipo"] == "1":                                                                    # [L-PE-03]
        if not validar_cpf(remover_zeros_esquerda(rec["cpf"])):
            st.rep(n, arq, f"O CPF_CNPJ_PESSOA: {rec['cpf']} está inválido.")
    elif rec["tipo"] == "2" and not validar_cnpj(rec["cpf"]):
        st.rep(n, arq, f"O CPF_CNPJ_PESSOA: {rec['cpf']} está inválido.")
    if rec["tipo"] not in ("1", "2"):                                                         # [L-PE-04]
        st.rep(n, arq, "Tipo de pessoa inválido. Valores possíveis: 1 e 2.")
    inv = next((i for i in st.investigados if completar_zeros(i["cpf"], 14) == completar_zeros(rec["cpf"], 14)), None)
    rec["investigado"] = inv
    return rec


def _titularidade(st, d, n, arq, fn):
    rec = dict(tipo=d[4], investigada=d[5], inicio=d[19], fim=d[20], linha=n)
    for msg, v in (("O código COMPE da instituição financeira não foi informado", d[0]),
                   ("O número da agência não foi informado", d[1]),
                   ("O número da conta não foi informado", d[2]),
                   ("O tipo de conta não foi informado", d[3])):
        if v == "":
            raise Negocio(msg)
    idc = _find_conta(st, d[0], d[1], d[2], d[3])
    if idc == -1:
        raise Negocio(f"Conta não localizada. Banco: {d[0]} Agencia: {d[1]} Conta: {d[2]} TipoConta: {d[3]}")
    rec["idconta"] = idc
    rec["idpessoa"] = n  # PessoaLocalizerCmd.findIdPessoa(linha): pessoa inserida na mesma linha
    # validarCamposObrigatorios e ..Eleitoral têm a MESMA condição e mensagem (ramo eleitoral não relaxa nada)
    if is_empty(rec["tipo"]) or is_empty(rec["inicio"]) or is_empty(rec["investigada"]):      # [L-TI-01]
        st.rep(n, arq, "Um dos campos obrigatórios (TIPO_PESSOA, CPF_CNPJ_PESSOA, NOME_PESSOA, PESSOA_INVESTIGADA, "
                       "DATA_INICIO_RELACIONAMENTO) não está preenchido.")
    if not is_empty(rec["inicio"]) and not verifica_data(rec["inicio"]):                      # [L-TI-02] lança Negocio
        raise Negocio(f"A DATA_INICIO_RELACIONAMENTO ({rec['inicio']}) é inválida ou o ano é anterior a 1900.")
    if rec["fim"] != "" and not verifica_data(rec["fim"]):                                    # [L-TI-03] lança Negocio
        raise Negocio(f"A DATA_FIM_RELACIONAMENTO ({rec['fim']}) é inválida ou o ano é anterior a 1900.")
    if not is_empty(rec["tipo"]) and not re.fullmatch(r"[T1-9RLPO]", rec["tipo"]):             # [L-TI-04]
        st.rep(n, arq, "Tipo de titular inválido. Valores possíveis: T, 1 até 9, R, L, P e O.")
    if not is_empty(rec["investigada"]) and not re.fullmatch(r"[01]", rec["investigada"]):    # [L-TI-05]
        st.rep(n, arq, "Tipo de conta investigada inválido. Valores possíveis: 0 e 1.")
    if not verifica_data_leniente_ok(rec["inicio"]):                                          # [L-TI-06] lança Negocio
        raise Negocio(f"A DATA_INICIO_RELACIONAMENTO é inválida: {rec['inicio']}")
    if rec["fim"] != "":
        if not verifica_data_leniente_ok(rec["fim"]):
            raise Negocio(f"A DATA_FIM_RELACIONAMENTO é inválida: {rec['fim']}")
        if parse_data(rec["fim"]) < parse_data(rec["inicio"]):
            st.rep(n, arq, f"A DATA_INICIO_RELACIONAMENTO ({rec['inicio']}) é posterior a DATA_FIM_RELACIONAMENTO ({rec['fim']}).")
    return rec


def verifica_data_leniente_ok(s: str) -> bool:
    """SimpleDateFormat leniente padrão: falha só se o texto não for parseável (vazio/não numérico)."""
    return re.fullmatch(r"\d{8}", s or "") is not None and verifica_data(s)


# ---- ORIGEM_DESTINO (CarregarArquivoOrigemDestinoCmd) ------------------------------
def _od(st, d, n, arq, fn):
    n0 = len(st.erros)
    chave = _int(d[0])                                       # Long.valueOf direto: não numérico -> 'Erro desconhecido'
    rec = dict(chave=chave, chave_extrato=_get_long(st, d[1], n, "CODIGO_CHAVE_EXTRATO", fn),
               valor=_get_long(st, d[2], n, "VALOR_OPERACAO", fn), doc=d[3], banco=d[4],
               agencia=None if d[5] == "" else _get_long(st, d[5], n, "NUMERO_AGENCIA", fn), conta=d[6],
               tipo_conta=d[7], tipo_pessoa=d[8], cpf=d[9], nome=d[10], obs=d[17], linha=n,
               situacao=_get_long(st, d[16], n, "SITUACAO_IDENTIFICACAO", fn))
    if not is_empty(rec["tipo_pessoa"]) and not re.fullmatch(r"[12]", rec["tipo_pessoa"]):    # [L-OD-01] (checa PESSOA, msg de CONTA!)
        st.rep(n, arq, "TIPO_CONTA_OD inválido. Valores possíveis: 1, 2, 3 ou 4.")
    if not is_empty(rec["tipo_pessoa"]) and not re.fullmatch(r"[12]", rec["tipo_pessoa"]):    # [L-OD-02]
        st.rep(n, arq, "TIPO_PESSOA_OD inválido. Valores possíveis: 1 ou 2.")
    if rec["situacao"] != 0:                                                                  # [L-OD-03]
        st.rep(n, arq, "SITUACAO_IDENTIFICACAO inválido. Valor possível: 0.")
    # setParametros (só sem erro na linha): TIPO_CONTA_OD/TIPO_PESSOA_OD via Integer.valueOf; BANCO_OD numeric(3) via setString
    if len(st.erros) == n0:
        for v in (d[7], d[8], d[4]):
            if not is_empty(v):
                _int(v)
    # [L-OD-04] NUMERO_BANCO_OD com >3 chars depende de Computador.getVersaoReceptor() (externo): NÃO MODELADO
    return rec


# ======================================================================================
# Regras de CONTEÚDO (consultas SQL em HSQLDB) — modelo em memória.  [R-*]
# Ordem de execução = validacao/ValidarAtendimento.java. Todas as regras de um arquivo rodam
# (validar() não lança); o arquivo falha se ErrorReportCmd.hasErros() (global).
# ======================================================================================
def _n(x):
    return None if is_empty(x) else x


def _od_norm(od):
    return dict(od, banco=None if _n(od["banco"]) is None else int(od["banco"]), conta=_n(od["conta"]),
                cpf=_n(od["cpf"]), nome=_n(od["nome"]), obs=_n(od["obs"]), tipo_pessoa=_n(od["tipo_pessoa"]))


def _ext_by_id(st):
    return {e["chave"]: e for e in st.extratos}


def _fmt_conta(st, c):
    ag = st.agencias[c["idag"] - 1]
    return (f"NUMERO_BANCO: {completar_zeros(str(int(ag['banco'])), 3)}, NUMERO_AGENCIA: "
            f"{completar_zeros(str(int(ag['agencia'])), 4)}, NUMERO_CONTA: {c['conta']}, TIPO_CONTA: {int(c['tipo'])}.")


def _contas_com_titular(st, pred=lambda t: True):
    return {t["idconta"] for t in st.titularidades if pred(t)}


def validar_agencias(st):
    g = {}
    for a in st.agencias:                                         # [R-AG-01]
        g.setdefault((int(a["banco"]), int(a["agencia"])), []).append(a["linha"])
    for (b, ag), ls in g.items():
        if len(ls) > 1:
            st.rep(max(ls), "AGENCIAS", f"Agência duplicada: NUMERO_BANCO: {str(b).zfill(3)[-3:]}, NUMERO_AGENCIA: {str(ag).zfill(4)[-4:]}")


def validar_contas(st):
    ext_contas = {e["idconta"] for e in st.extratos}
    com_tit = _contas_com_titular(st)
    com_inv = _contas_com_titular(st, lambda t: t["investigada"] == "1")
    com_T = _contas_com_titular(st, lambda t: t["tipo"] == "T")
    for c in st.contas:                                            # [R-CT-01]
        if any(ch in c["conta"] for ch in ".\\/-"):
            st.rep(c["linha"], "CONTAS", f"Conta com separador: {c['conta']}")
    g = {}
    for c in st.contas:                                            # [R-CT-02]
        g.setdefault((c["idag"], c["conta"], int(c["tipo"])), []).append(c["linha"])
    for (idag, conta, tipo), ls in g.items():
        if len(ls) > 1:
            ag = st.agencias[idag - 1]
            st.rep(max(ls), "null", f"Conta duplicada - Linha: {max(ls)}, NUMERO_AGENCIA: {int(ag['agencia'])}, NUMERO_CONTA: {conta}, TIPO_CONTA: {tipo}")
    for i, c in enumerate(st.contas, 1):                           # [R-CT-03]
        if int(c["mov"]) in (1, 2, 3) and i not in com_tit:
            st.rep(c["linha"], "CONTAS", "Conta sem registro correspondente em Titulares. " + _fmt_conta(st, c))
    for i, c in enumerate(st.contas, 1):                           # [R-CT-04]
        if int(c["mov"]) in (1, 2) and i not in com_inv:
            st.rep(c["linha"], "CONTAS", "Conta sem pessoa investigada. " + _fmt_conta(st, c))
    if not any(int(c["mov"]) in (1, 2) for c in st.contas):        # [R-CT-05]
        st.rep(0, "CONTAS", "Atendimento sem nenhuma movimentação de conta.")
    for t in st.titularidades:                                     # [R-CT-06] (uma linha de erro por titularidade: JOIN)
        c = st.contas[t["idconta"] - 1]
        if int(c["mov"]) != 3 and t["idconta"] not in com_inv:
            st.rep(c["linha"], "CONTAS", "Conta com atributo MOVIMENTACAO_CONTA incorreto. Deve ser 3, pois não é conta de investigado. " + _fmt_conta(st, c))
    cont_T = {}
    for t in st.titularidades:
        if t["tipo"] == "T":
            cont_T[t["idconta"]] = cont_T.get(t["idconta"], 0) + 1
    for idc, q in cont_T.items():                                  # [R-CT-07]
        if q > 1:
            c = st.contas[idc - 1]
            st.rep(c["linha"], "CONTAS", "Conta com mais de um Titular tipo T. " + _fmt_conta(st, c))
    for t in st.titularidades:                                     # [R-CT-08]
        c = st.contas[t["idconta"] - 1]
        if t["investigada"] == "1" and int(c["mov"]) not in (1, 2):
            st.rep(c["linha"], "CONTAS", "Conta com atributo MOVIMENTACAO_CONTA incorreto. Deve ser 1 ou 2, pois é um investigado. " + _fmt_conta(st, c))
    for i, c in enumerate(st.contas, 1):                           # [R-CT-09] / [R-CT-10]
        if int(c["mov"]) == 1 and i not in ext_contas:
            st.rep(c["linha"], "CONTAS", "Conta definida com movimentação (MOVIMENTACAO_CONTA = 1), porém não possui lançamentos em EXTRATO. " + _fmt_conta(st, c))
        if int(c["mov"]) == 2 and i in ext_contas:
            st.rep(c["linha"], "CONTAS", "Conta definida sem movimentação (MOVIMENTACAO_CONTA = 2), porém possui lançamentos em EXTRATO. " + _fmt_conta(st, c))


def validar_titulares(st):
    com_T = _contas_com_titular(st, lambda t: t["tipo"] == "T")
    for t in st.titularidades:
        p = st.pessoas[t["idpessoa"] - 1]
        if t["investigada"] == "1" and t["tipo"] == "T" and any(_n(p[k]) is None for k in
                                                              ("doc_nome", "doc_num", "logradouro", "cidade", "uf", "pais", "cep", "tel")):
            st.rep(t["linha"], "TITULARES", "O titular de conta investigada deve ter os seguintes dados preenchidos: TIPO_PESSOA, CPF_CNPJ, NOME_PESSOA, "
                                          "NOME_DOC_IDENTIFICACAO, NUMERO_DOC_IDENTIFICACAO, ENDERECO_LOGRADOURO, CIDADE, UF, PAIS, CEP, TELEFONE_PESSOA")   # [R-TI-01]
    for t in st.titularidades:
        c = st.contas[t["idconta"] - 1]
        ini, fim = parse_data(t["inicio"]), (parse_data(t["fim"]) if t["fim"] else None)
        ab = parse_data(c["abertura"]) if c["abertura"] else None
        if ab and (ini < ab or (fim and fim < ab)):                                                                   # [R-TI-02]
            st.rep(t["linha"], "TITULARES", "A DATA_INICIO_RELACIONAMENTO ou DATA_FIM_RELACIONAMENTO está menor que a DATA_ABERTURA_CONTA (" + ab.isoformat())
    for t in st.titularidades:
        c = st.contas[t["idconta"] - 1]
        ini, fim = parse_data(t["inicio"]), (parse_data(t["fim"]) if t["fim"] else None)
        en = parse_data(c["encerramento"]) if c["encerramento"] else None
        if en and (ini > en or (fim and fim > en)):                                                                   # [R-TI-03]
            st.rep(t["linha"], "TITULARES", "A DATA_INICIO_RELACIONAMENTO ou DATA_FIM_RELACIONAMENTO está maior que a DATA_ENCERRAMENTO_CONTA (" + en.isoformat())
    inv_arquivo = {completar_zeros(st.pessoas[t["idpessoa"] - 1]["cpf"], 14) for t in st.titularidades if t["investigada"] == "1"}
    inv_cad = {completar_zeros(i["cpf"], 14) for i in st.investigados if i["teve_rel"] == 1 and i["teve_conta"] == 1}
    for i in st.investigados:                                                                                         # [R-TI-04]
        if i["teve_rel"] == 1 and i["teve_conta"] == 1 and completar_zeros(i["cpf"], 14) not in inv_arquivo:
            st.rep(-1, "TITULARES", f"O CPF_CNPJ {i['cpf']} cadastrado no atendimento como investigado, não foi encontrado no arquivo TITULARES como PESSOA_INVESTIGADA = 1")
    for t in st.titularidades:                                                                                        # [R-TI-05]
        p = st.pessoas[t["idpessoa"] - 1]
        if p["investigado"] and p["investigado"]["teve_rel"] == 1 and p["investigado"]["teve_conta"] == 1 and t["investigada"] == "0":
            st.rep(p["linha"], "TITULARES", f"O CPF_CNPJ {p['cpf']} cadastrado no atendimento como investigado, deve possuir o atributo PESSOA_INVESTIGADA = 1")
    for t in st.titularidades:                                                                                        # [R-TI-06]
        p = st.pessoas[t["idpessoa"] - 1]
        if t["investigada"] == "1" and completar_zeros(p["cpf"], 14) not in inv_cad:
            st.rep(p["linha"], "TITULARES", f"O CPF_CNPJ {p['cpf']} informado como PESSOA_INVESTIGADA = 1 não foi cadastrado no atendimento.")
    for t in st.titularidades:                                                                                        # [R-TI-07]
        if t["investigada"] == "1" and t["idconta"] not in com_T:
            c = st.contas[t["idconta"] - 1]
            st.rep(t["linha"], "TITULARES", "Conta investigada sem Titular do tipo T. " + _fmt_conta(st, c))


def validar_extrato(st):
    for e in st.extratos:                                                                                              # [R-EX-01]
        d = parse_data(e["data"])
        for t in st.titularidades:
            if t["idconta"] == e["idconta"] and t["investigada"] == "1":
                inv = st.pessoas[t["idpessoa"] - 1]["investigado"]
                if inv and inv.get("ini") and inv.get("fim") and (d < inv["ini"] or d > inv["fim"]):
                    st.rep(e["linha"], "EXTRATO", f"Lançamento em {d:%d/%m/%Y} fora do período de afastamento ({inv['ini']:%d/%m/%Y} a {inv['fim']:%d/%m/%Y}) do investigado: CPF_CNPJ ({inv['cpf']})")
    for e in st.extratos:
        c = st.contas[e["idconta"] - 1]
        d = parse_data(e["data"])
        ab = parse_data(c["abertura"]) if c["abertura"] else None
        en = parse_data(c["encerramento"]) if c["encerramento"] else None
        if (ab and d < ab) or (en and d > en):                                                                         # [R-EX-02]
            ab_s = ab.strftime("%d/%m/%Y")
            fim_s = f") e DATA_ENCERRAMENTO_CONTA ({en:%d/%m/%Y}" if en else ") e sem DATA_ENCERRAMENTO_CONTA"
            st.rep(e["linha"], "EXTRATO", f"A DATA_LANCAMENTO ({d:%d/%m/%Y}) está fora do período de DATA_ABERTURA_CONTA ({ab_s}{fim_s}).")
    ods_por_ext = {}
    for od in st.ods:
        ods_por_ext.setdefault(od["chave_extrato"], []).append(od)
    for e in st.extratos:                                                                                              # [R-EX-03]
        if e["chave"] not in ods_por_ext:
            st.rep(e["linha"], "EXTRATO", f"Lançamento em EXTRATO sem correspondente em ORIGEM_DESTINO. CODIGO_CHAVE_EXTRATO: {e['chave']}")
    for e in st.extratos:                                                                                              # [R-EX-04]
        if int(st.contas[e["idconta"] - 1]["mov"]) == 3:
            st.rep(e["linha"], "EXTRATO", "No arquivo CONTAS, se o campo MOVIMENTAÇÃO DA CONTA estiver com o parâmetro \"3\"(conta não investigada) não pode haver nenhum registro para essa conta no arquivo EXTRATO")
    for e in st.extratos:                                                                                              # [R-EX-05]
        if e["chave"] in ods_por_ext and e["valor"] != sum(o["valor"] for o in ods_por_ext[e["chave"]]):
            st.rep(e["linha"], "EXTRATO", f"O Valor do lancamento em EXTRATO (CODIGO_CHAVE: {e['chave']}) não é igual ao somatório dos registros correspondentes em ORIGEM_DESTINO.")


def _ident_ausente(od, repeat_len_concat=True):
    conta_zeros = od["conta"] is not None and set(od["conta"]) <= {"0"} and od["conta"] != ""
    return (od["cpf"] is None or od["nome"] is None or od["banco"] is None or od["agencia"] is None or od["conta"] is None
            or od["banco"] == 0 or od["agencia"] == 0 or conta_zeros)


def validar_origem_destino(st):
    """Cada regra é uma consulta SEPARADA sobre todos os registros (ordem: R-OD-01, 02, 03 ou E1, E2; depois 04)."""
    ext = _ext_by_id(st)
    d2006 = dt.date(2006, 1, 1)

    def linhas():
        for raw in st.ods:
            e = ext.get(raw["chave_extrato"])
            if e is not None:
                yield raw, _od_norm(raw), e, parse_data(e["data"]), e["tipo"], e["valor"]

    if not st.ctx.eleitoral:
        for raw, od, e, dl, tipo, valor in linhas():                                                                      # [R-OD-01]
            nao_corr = (od["agencia"] == 9999 and od["conta"] == "99999999999999999999" and od["obs"] is not None
                        and od["obs"].upper() == "NAO-CORRENTISTA")
            if tipo in (120, 209) and dl >= d2006 and valor >= 1000000 and _ident_ausente(od) and not nao_corr:
                st.rep(od["linha"], "ORIGEM_DESTINO", "É OBRIGATÓRIA a identicação do depositante/favorecido de Transferências Interbancária (TED, DOC) nos campos CPF_CNPJ_OD, NOME_PESSOA_OD, NUMERO_BANCO_OD, NUMERO_AGENCIA_OD e NUMERO_CONTA_OD. Caso o depositante/favorecido não possua conta no banco, os campos NUMERO_AGENCIA_OD, NUMERO_CONTA_OD e OBSERVACAO_OD devem possuir, respectivamente, os seguintes valores: 9999, 99999999999999999999 e NAO-CORRENTISTA.")
        for raw, od, e, dl, tipo, valor in linhas():                                                                      # [R-OD-02]
            if tipo in (117, 213) and dl >= d2006 and valor >= 1000000 and _ident_ausente(od):
                st.rep(od["linha"], "ORIGEM_DESTINO", "É OBRIGATÓRIA a identicação do depositante/favorecido de Transferências entre Contas nos campos CPF_CNPJ_OD, NOME_PESSOA_OD, NUMERO_BANCO_OD, NUMERO_AGENCIA_OD e NUMERO_CONTA_OD.")
        for raw, od, e, dl, tipo, valor in linhas():                                                                      # [R-OD-03]
            if tipo in (123, 220) and ((dl >= dt.date(2013, 7, 1) and valor >= 1000000)
                                       or (dl >= dt.date(2013, 1, 2) and valor >= 5000000)
                                       or (dl >= dt.date(2012, 7, 1) and valor >= 10000000)):
                if od["nome"] is None and (od["obs"] is None or od["obs"].upper() != "099 - NEGATIVA DE IDENTIFICACAO DO PORTADOR"):
                    st.rep(od["linha"], "ORIGEM_DESTINO", "É obrigatória a identificação do depositante/favorecido de transações em espécie, campo NOME_PESSOA_OD. Caso o depositante/favorecido recusou se identificar, o campo OBSERVACAO_OD deve conter o seguinte valor: '099 - NEGATIVA DE IDENTIFICACAO DO PORTADOR'.")
    else:
        for raw, od, e, dl, tipo, valor in linhas():                                                                      # [R-OD-E1]
            if tipo in (117, 120, 209, 213) and (od["cpf"] is None or od["banco"] is None or od["agencia"] is None
                                                 or od["conta"] is None or od["banco"] == 0 or od["agencia"] == 0
                                                 or (od["conta"] and set(od["conta"]) <= {"0"})):
                st.rep(od["linha"], "ORIGEM_DESTINO", "É OBRIGATÓRIA a identicação do depositante/favorecido de transferências no mesmo banco ou transferências interbancárias (TED/DOC) nos campos CPF_CNPJ_OD, NUMERO_BANCO_OD, NUMERO_AGENCIA_OD e NUMERO_CONTA_OD.")
        for raw, od, e, dl, tipo, valor in linhas():                                                                      # [R-OD-E2]
            if tipo == 201 and valor >= 10000 and od["cpf"] is None:
                st.rep(od["linha"], "ORIGEM_DESTINO", "É OBRIGATÓRIA a identicação do depositante/favorecido de depósito em cheque e/ou dinheiro no campo CPF_CNPJ_OD.")
    for raw in st.ods:                                                                                                    # [R-OD-04]
        od = _od_norm(raw)
        e = ext.get(od["chave_extrato"])
        if e is None or e["tipo"] not in (117, 120, 209, 213) or parse_data(e["data"]) < d2006 or od["cpf"] is None:
            continue
        tp, cpf = od["tipo_pessoa"], od["cpf"]
        if tp == "1":
            ok = validar_cpf(cpf)
        elif tp == "2":
            cpf = completar_zeros(cpf, 14)               # o original reatribui a variável ANTES de reportar
            ok = validar_cnpj(cpf)
        else:
            cpf = completar_zeros(cpf, 14)
            ok = validar_cnpj(cpf)
            if not ok:
                cpf = cpf[3:]
                ok = validar_cpf(cpf)
        if not ok:
            if is_empty(tp):
                sufixo = " é inválido."
            else:
                sufixo = f" é inválido para TIPO_PESSOA_OD = {tp} ({'Pessoa Física' if tp == '1' else 'Pessoa Jurídica'})."
            st.rep(od["linha"], "ORIGEM_DESTINO", f"O campo CPF_CNPJ_PESSOA_OD com o valor {cpf}" + sufixo)


def validar_conteudo(st) -> dict:
    """Replica o laço de frmSelecionaDiretorio: valida AGENCIAS..ORIGEM_DESTINO na ordem e PARA no primeiro arquivo com erro."""
    fases = [("AGENCIAS", validar_agencias), ("CONTAS", validar_contas), ("TITULARES", validar_titulares),
             ("EXTRATO", validar_extrato), ("ORIGEM_DESTINO", validar_origem_destino)]
    res = {}
    for nome, fn in fases:
        fn(st)
        res[nome] = not st.erros
        if st.erros:
            break
    return res
