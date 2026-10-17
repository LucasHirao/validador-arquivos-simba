"""Testes de caracterização do modelo de referência (dados 100% sintéticos).

Rodar:  python3 -I -m unittest -v test_caracterizacao   (dentro de analise/testes)

IMPORTANTE: estes testes verificam o MODELO derivado da leitura do código decompilado. Eles NÃO provam que o
aplicativo real se comporta assim. A confirmação exige execução diferencial em ambiente isolado e autorizado
(05-testes.md, seção "Execução diferencial pendente").  Os vetores de CPF/CNPJ são sintéticos/públicos.
"""
import random
import unittest

import validador_ref as r
from validador_ref import Ctx, Estado


# ---------------------------------------------------------------------------------------
# Algoritmo "oficial" independente (peso/módulo 11), escrito sem olhar o port do aplicativo
# ---------------------------------------------------------------------------------------
def oficial_cpf_dv(base9: str) -> str:
    dvs = ""
    num = base9
    for _ in range(2):
        pesos = range(len(num) + 1, 1, -1)
        resto = sum(int(c) * p for c, p in zip(num, pesos)) % 11
        d = 0 if resto < 2 else 11 - resto
        dvs += str(d)
        num += str(d)
    return dvs


def oficial_cnpj_dv(base12: str) -> str:
    """Valor de cada caractere = ord(c) - 48 (regra do CNPJ alfanumérico); pesos 2..9 da direita para a esquerda."""
    num = base12
    dvs = ""
    for _ in range(2):
        soma, peso = 0, 2
        for c in reversed(num):
            soma += (ord(c) - 48) * peso
            peso = 2 if peso == 9 else peso + 1
        resto = soma % 11
        d = 0 if resto < 2 else 11 - resto
        dvs += str(d)
        num += str(d)
    return dvs


class PrimitivasJava(unittest.TestCase):
    def test_split_tab_equivale_a_sonda_java(self):
        # Saídas observadas em SplitProbe.java (JDK 25 local, código próprio — não é código do aplicativo)
        casos = {
            "a\tb\tc": ["a", "b", "c"],
            "\tb\tc": ["b", "c"],           # campo vazio INICIAL some (3 colunas viram 2)
            "a\t\tc": ["a", "", "c"],
            "a\tb\t": ["a", "b", ""],
            "a\tb\t\t": ["a", "b", "", ""],
            "": [""],
            "\t": [""],
            "\t\t": ["", ""],
            "  a \t b ": ["a", "b"],
        }
        for entrada, esperado in casos.items():
            with self.subTest(entrada=entrada):
                self.assertEqual(r.split_tab(entrada), esperado)

    def test_verifica_data(self):
        validas = ["01012000", "29022024", "31121999", "01011900"]
        invalidas = ["", "1012000", "010120000", "29022023", "00012000", "32012000", "01132000", "31042021",
                     "01011899", "ab012000", "0101200a", "01 12000"]
        for v in validas:
            self.assertTrue(r.verifica_data(v), v)
        for v in invalidas:
            self.assertFalse(r.verifica_data(v), v)

    def test_remover_zeros_esquerda_falha_para_so_zeros(self):
        self.assertEqual(r.remover_zeros_esquerda("00123"), "123")
        self.assertEqual(r.remover_zeros_esquerda("123"), "123")
        with self.assertRaises(r.JavaIndexError):
            r.remover_zeros_esquerda("00000000000")
        with self.assertRaises(r.JavaIndexError):
            r.remover_zeros_esquerda("")

    def test_completar_zeros(self):
        self.assertEqual(r.completar_zeros("7", 3), "007")
        self.assertEqual(r.completar_zeros("1234", 3), "1234")
        self.assertIsNone(r.completar_zeros(None, 3))


class CpfCnpj(unittest.TestCase):
    def test_vetores_conhecidos(self):
        self.assertTrue(r.validar_cpf("52998224725"))
        self.assertTrue(r.validar_cpf("5299822472" + "5"))
        self.assertFalse(r.validar_cpf("52998224724"))
        self.assertTrue(r.validar_cnpj("11222333000181"))
        self.assertFalse(r.validar_cnpj("11222333000182"))
        self.assertTrue(r.validar_cnpj("12ABC34501DE35"))  # exemplo público de CNPJ alfanumérico

    def test_repetidos_sao_invalidos_mas_so_a_base_e_checada(self):
        for d in "0123456789":
            self.assertFalse(r.validar_cpf(d * 11), d)
        self.assertFalse(r.validar_cnpj("00000000000000"))
        self.assertFalse(r.validar_cnpj("11111111111111"))

    def test_zeros_a_esquerda_sao_completados(self):
        self.assertTrue(r.validar_cpf("0" + "5299822472" + "5"[:0]) is False or True)  # sem asserção: ver abaixo
        # CPF com menos de 11 dígitos é completado com zeros à esquerda antes do cálculo
        base = "000123456"
        cpf = base + oficial_cpf_dv(base)
        self.assertTrue(r.validar_cpf(cpf))
        self.assertTrue(r.validar_cpf(cpf[2:]))   # 9 dígitos -> completado com 2 zeros

    def test_diferencial_contra_algoritmo_oficial_cpf(self):
        rnd = random.Random(20260101)
        for _ in range(3000):
            base = "".join(rnd.choice("0123456789") for _ in range(9))
            if len(set(base)) == 1:
                continue
            cpf = base + oficial_cpf_dv(base)
            self.assertTrue(r.validar_cpf(cpf), cpf)
            errado = base + oficial_cpf_dv(base)[::-1] if oficial_cpf_dv(base)[0] != oficial_cpf_dv(base)[1] else cpf[:-1] + str((int(cpf[-1]) + 1) % 10)
            self.assertFalse(r.validar_cpf(errado), errado)

    def test_diferencial_contra_algoritmo_oficial_cnpj_alfanumerico(self):
        rnd = random.Random(20260102)
        alfabeto = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        for _ in range(3000):
            base = "".join(rnd.choice(alfabeto) for _ in range(12))
            if len(set(base)) == 1:
                continue
            cnpj = base + oficial_cnpj_dv(base)
            self.assertTrue(r.validar_cnpj(cnpj), cnpj)
            self.assertEqual(r.dv_cnpj(base), oficial_cnpj_dv(base))


class Cnab(unittest.TestCase):
    def test_credito(self):
        for c in (201, 210, 223, 999):
            self.assertFalse(r.cnab_credito_invalido(c), c)
        for c in (200, 224, 101, 0):
            self.assertTrue(r.cnab_credito_invalido(c), c)

    def test_debito(self):
        for c in (101, 115, 117, 127, 999):
            self.assertFalse(r.cnab_debito_invalido(c), c)
        for c in (100, 116, 128, 201):
            self.assertTrue(r.cnab_debito_invalido(c), c)

    def test_credito_debito(self):
        for c in (101, 127, 201, 223, 999):
            self.assertFalse(r.cnab_credito_debito_invalido(c), c)
        for c in (100, 116, 128, 200, 224):
            self.assertTrue(r.cnab_credito_debito_invalido(c), c)


# ---------------------------------------------------------------------------------------
# Caso mínimo SINTÉTICO (tudo fictício)
# ---------------------------------------------------------------------------------------
CPF_SINT = "52998224725"


def L(*campos):
    return "\t".join(campos)


def caso_minimo():
    ag = L("001", "0001", "AGENCIA TESTE", "RUA TESTE 100", "CIDADE TESTE", "DF", "BRASIL", "70000000", "6133334444", "01012000", "")
    ct = L("001", "0001", "12345", "1", "01012010", "", "1")
    tit = L("001", "0001", "12345", "1", "T", "1", "1", CPF_SINT, "FULANO DE TESTE", "RG", "1234567", "RUA X", "CIDADE", "DF",
            "BRASIL", "70000000", "6133334444", "", "", "01012010", "")
    ex = L("1", "001", "0001", "12345", "1", "15032020", "DOC1", "DEPOSITO", "201", "50000", "C", "50000", "C", "AG")
    od = L("1", "1", "50000", "DOC1", "", "", "", "", "", "", "", "", "", "", "", "", "0", "")
    return dict(AGENCIAS=[ag], CONTAS=[ct], TITULARES=[tit], EXTRATO=[ex], ORIGEM_DESTINO=[od])


def rodar(arquivos, ctx=None, validar=True, investigados=None, nome_caso="001-TST-000001-00"):
    st = Estado(ctx or Ctx(eleitoral=False, numero_banco_atendimento=1), nome_caso=nome_caso)
    st.investigados = investigados if investigados is not None else [dict(cpf=CPF_SINT, teve_rel=1, teve_conta=1)]
    for nome in ("AGENCIAS", "CONTAS"):
        if r.carregar(st, nome, arquivos[nome]):
            return st, None
    if r.carregar_titulares(st, arquivos["TITULARES"]):
        return st, None
    for nome in ("EXTRATO", "ORIGEM_DESTINO"):
        if r.carregar(st, nome, arquivos[nome]):
            return st, None
    return st, (r.validar_conteudo(st) if validar else {})


def msgs(st):
    return [e.msg for e in st.erros]


class CargaEstrutural(unittest.TestCase):
    def test_caso_minimo_valido(self):
        st, res = rodar(caso_minimo())
        self.assertEqual(msgs(st), [])
        self.assertTrue(all(res.values()), res)

    def test_quantidade_de_colunas(self):
        a = caso_minimo()
        a["AGENCIAS"] = ["001\t0001\tX"]
        st, _ = rodar(a)
        self.assertEqual(msgs(st), ["Quantidade de colunas inválida. Esperado: 11 Atual: 3"])
        self.assertEqual(st.erros[0].arquivo, "001-TST-000001-00_AGENCIAS.txt")

    def test_primeiro_campo_vazio_reduz_colunas(self):
        a = caso_minimo()
        a["AGENCIAS"] = [a["AGENCIAS"][0][3:]]  # linha começa com TAB: NUMERO_BANCO vazio
        st, _ = rodar(a)
        self.assertEqual(msgs(st), ["Quantidade de colunas inválida. Esperado: 11 Atual: 10"])

    def test_tamanho_maximo_do_campo(self):
        a = caso_minimo()
        c = a["AGENCIAS"][0].split("\t")
        c[5] = "DFX"
        a["AGENCIAS"] = ["\t".join(c)]
        st, _ = rodar(a)
        self.assertEqual(msgs(st), ["Tamanho do campo ENDERECO_UF excedido. Tamanho máximo permitido: 2"])

    def test_apos_primeiro_erro_linhas_seguintes_so_tem_validacao_estrutural(self):
        a = caso_minimo()
        base = a["AGENCIAS"][0].split("\t")
        l1 = list(base); l1[5] = "XX"          # erro semântico (UF)
        l2 = list(base); l2[5] = "YY"          # também semântico: NÃO é reportado (hasErros global)
        l3 = "001\t0001"                       # estrutural: reportado
        l4 = list(base); l4[2] = "N" * 51      # tamanho: reportado
        a["AGENCIAS"] = ["\t".join(l1), "\t".join(l2), l3, "\t".join(l4)]
        st, _ = rodar(a)
        self.assertEqual([(e.linha, e.msg) for e in st.erros], [
            (1, "ENDERECO_UF:XX não é uma sigla válida."),
            (3, "Quantidade de colunas inválida. Esperado: 11 Atual: 2"),
            (4, "Tamanho do campo NOME_AGENCIA excedido. Tamanho máximo permitido: 50"),
        ])

    def test_numero_banco_vazio_gera_dois_erros(self):
        # Integer.valueOf("") lança NumberFormatException -> "Erro desconhecido" além do erro de obrigatórios
        a = caso_minimo()
        c = a["AGENCIAS"][0].split("\t"); c[0] = " "
        a["AGENCIAS"] = ["\t".join(c)]
        st, _ = rodar(a)
        self.assertEqual(len(st.erros), 2)
        self.assertTrue(st.erros[0].msg.startswith("Um dos campos obrigatórios (NUMERO_BANCO, NUMERO_AGENCIA"))
        self.assertEqual(st.erros[1].msg, r.DESCONHECIDO)


class CargaPorArquivo(unittest.TestCase):
    def test_agencia_banco_diferente_do_atendimento(self):
        st, _ = rodar(caso_minimo(), Ctx(numero_banco_atendimento=237))
        self.assertEqual(msgs(st), ["NUMERO_BANCO diferente do informado em atendimento."])

    def test_agencia_eleitoral_exige_so_tres_campos(self):
        a = caso_minimo()
        a["AGENCIAS"] = [L("001", "0001", "AGENCIA ELEIT", "", "", "", "", "", "", "", "")]
        st, _ = rodar(a, Ctx(eleitoral=True), validar=False)
        self.assertEqual(msgs(st), [])
        st, _ = rodar(a, Ctx(eleitoral=False), validar=False)
        self.assertTrue(msgs(st)[0].startswith("Um dos campos obrigatórios"))

    def test_agencia_uf_case_sensitive_e_traco_duplo(self):
        for uf, ok in (("DF", True), ("df", False), ("--", True), ("XX", False)):
            a = caso_minimo()
            c = a["AGENCIAS"][0].split("\t"); c[5] = uf
            a["AGENCIAS"] = ["\t".join(c)]
            st, _ = rodar(a)
            self.assertEqual(msgs(st) == [], ok, uf)

    def test_agencia_fechamento_antes_da_abertura(self):
        a = caso_minimo()
        c = a["AGENCIAS"][0].split("\t"); c[9], c[10] = "01012020", "01012019"
        a["AGENCIAS"] = ["\t".join(c)]
        st, _ = rodar(a)
        self.assertEqual(msgs(st), ["DATA_ABERTURA_AGENCIA posterior à DATA_FECHAMENTO_AGENCIA."])

    def test_conta_agencia_inexistente(self):
        a = caso_minimo()
        c = a["CONTAS"][0].split("\t"); c[1] = "0002"
        a["CONTAS"] = ["\t".join(c)]
        st, _ = rodar(a)
        self.assertEqual(msgs(st), ["Agencia não localizada. Banco: 001 Agencia: 0002"])

    def test_conta_tipo_e_movimentacao_invalidos(self):
        for campo, idx, valor, msg in (("tipo", 3, "5", "Tipo de conta inválido. Valores possíveis: 1, 2, 3 e 4."),
                                       ("tipo", 3, "x", "Tipo de conta inválido. Valores possíveis: 1, 2, 3 e 4."),
                                       ("mov", 6, "4", "Movimentação de Conta inválido. Valores possíveis: 1, 2 e 3")):
            a = caso_minimo()
            c = a["CONTAS"][0].split("\t"); c[idx] = valor
            a["CONTAS"] = ["\t".join(c)]
            st, _ = rodar(a)
            self.assertEqual(msgs(st), [msg], (campo, valor))

    def test_conta_agencia_com_zeros_a_esquerda_equivale(self):
        a = caso_minimo()
        c = a["CONTAS"][0].split("\t"); c[0], c[1] = "1", "1"
        a["CONTAS"] = ["\t".join(c)]
        st, _ = rodar(a)
        self.assertEqual(msgs(st), [])

    def test_extrato_naturezas_e_cnab(self):
        casos = [("C", 201, True), ("C", 101, False), ("D", 117, True), ("D", 116, False), ("D", 999, True),
                 ("*", 110, True), ("*", 210, True), ("*", 999, True), ("*", 150, False), ("*", 250, False),
                 ("*", 116, False), ("X", 201, False)]
        for nat, tipo, ok in casos:
            a = caso_minimo()
            e = a["EXTRATO"][0].split("\t"); e[8], e[10] = str(tipo), nat
            a["EXTRATO"] = ["\t".join(e)]
            st, _ = rodar(a, validar=False)
            self.assertEqual(msgs(st) == [], ok, (nat, tipo, msgs(st)))

    def test_extrato_valor_negativo_e_conta_nao_localizada(self):
        a = caso_minimo()
        e = a["EXTRATO"][0].split("\t"); e[9] = "-1"
        a["EXTRATO"] = ["\t".join(e)]
        st, _ = rodar(a, validar=False)
        self.assertEqual(msgs(st), ["VALOR_LANCAMENTO e VALOR_SALDO não podem ser negativos."])
        a = caso_minimo()
        e = a["EXTRATO"][0].split("\t"); e[3] = "99999"
        a["EXTRATO"] = ["\t".join(e)]
        st, _ = rodar(a, validar=False)
        self.assertEqual(msgs(st), ["Conta não localizada no arquivo de CONTAS."])

    def test_extrato_valor_nao_numerico(self):
        a = caso_minimo()
        e = a["EXTRATO"][0].split("\t"); e[9] = "ABC"
        a["EXTRATO"] = ["\t".join(e)]
        st, _ = rodar(a, validar=False)
        self.assertEqual(msgs(st)[0], "Campo VALOR_LANCAMENTO não é um número.")

    def test_pessoa_cpf_invalido_e_tipo(self):
        a = caso_minimo()
        t = a["TITULARES"][0].split("\t"); t[7] = "52998224724"
        a["TITULARES"] = ["\t".join(t)]
        st, _ = rodar(a)
        self.assertEqual(msgs(st), ["O CPF_CNPJ_PESSOA: 52998224724 está inválido."])
        a = caso_minimo()
        t = a["TITULARES"][0].split("\t"); t[6] = "3"
        a["TITULARES"] = ["\t".join(t)]
        st, _ = rodar(a)
        self.assertEqual(msgs(st), ["Tipo de pessoa inválido. Valores possíveis: 1 e 2."])

    def test_pessoa_cpf_so_zeros_gera_erro_desconhecido(self):
        a = caso_minimo()
        t = a["TITULARES"][0].split("\t"); t[7] = "00000000000"
        a["TITULARES"] = ["\t".join(t)]
        st, _ = rodar(a)
        self.assertEqual(msgs(st), [r.DESCONHECIDO])

    def test_pessoa_data_renda_invalida_e_atribuida_a_extrato(self):
        a = caso_minimo()
        t = a["TITULARES"][0].split("\t"); t[18] = "31022020"
        a["TITULARES"] = ["\t".join(t)]
        st, _ = rodar(a)
        self.assertEqual(len(st.erros), 1)
        self.assertEqual(st.erros[0].arquivo, "EXTRATO")   # peculiaridade do aplicativo (L-PE-02)

    def test_titularidade_tipo_e_investigada(self):
        for idx, valor, msg in ((4, "Z", "Tipo de titular inválido. Valores possíveis: T, 1 até 9, R, L, P e O."),
                                (5, "2", "Tipo de conta investigada inválido. Valores possíveis: 0 e 1.")):
            a = caso_minimo()
            t = a["TITULARES"][0].split("\t"); t[idx] = valor
            a["TITULARES"] = ["\t".join(t)]
            st, _ = rodar(a)
            self.assertEqual(msgs(st), [msg], valor)

    def test_titularidade_conta_inexistente_e_datas(self):
        a = caso_minimo()
        t = a["TITULARES"][0].split("\t"); t[2] = "99999"
        a["TITULARES"] = ["\t".join(t)]
        st, _ = rodar(a)
        self.assertEqual(msgs(st), ["Conta não localizada. Banco: 001 Agencia: 0001 Conta: 99999 TipoConta: 1"])
        a = caso_minimo()
        t = a["TITULARES"][0].split("\t"); t[19], t[20] = "01012020", "01012019"
        a["TITULARES"] = ["\t".join(t)]
        st, _ = rodar(a)
        self.assertEqual(msgs(st), ["A DATA_INICIO_RELACIONAMENTO (01012020) é posterior a DATA_FIM_RELACIONAMENTO (01012019)."])

    def test_origem_destino_quirks(self):
        a = caso_minimo()
        o = a["ORIGEM_DESTINO"][0].split("\t"); o[8] = "3"      # TIPO_PESSOA_OD inválido -> DUAS mensagens (uma com texto de conta)
        a["ORIGEM_DESTINO"] = ["\t".join(o)]
        st, _ = rodar(a, validar=False)
        self.assertEqual(msgs(st), ["TIPO_CONTA_OD inválido. Valores possíveis: 1, 2, 3 ou 4.",
                                    "TIPO_PESSOA_OD inválido. Valores possíveis: 1 ou 2."])
        a = caso_minimo()
        o = a["ORIGEM_DESTINO"][0].split("\t"); o[16] = "1"
        a["ORIGEM_DESTINO"] = ["\t".join(o)]
        st, _ = rodar(a, validar=False)
        self.assertEqual(msgs(st), ["SITUACAO_IDENTIFICACAO inválido. Valor possível: 0."])
        a = caso_minimo()
        o = a["ORIGEM_DESTINO"][0].split("\t"); o[0] = "ABC"    # Long.valueOf direto
        a["ORIGEM_DESTINO"] = ["\t".join(o)]
        st, _ = rodar(a, validar=False)
        self.assertEqual(msgs(st), [r.DESCONHECIDO])


class RegrasDeConteudo(unittest.TestCase):
    def _erros(self, a, ctx=None):
        st, res = rodar(a, ctx)
        self.assertEqual(st.erros and st.erros[0].arquivo != "", True if st.erros else True)
        return st, res

    def test_agencia_duplicada(self):
        a = caso_minimo(); a["AGENCIAS"] = a["AGENCIAS"] * 2
        st, res = rodar(a)
        self.assertEqual([(e.linha, e.msg) for e in st.erros], [(2, "Agência duplicada: NUMERO_BANCO: 001, NUMERO_AGENCIA: 0001")])
        self.assertFalse(res["AGENCIAS"])
        self.assertNotIn("CONTAS", res)  # laço interrompe no 1º arquivo com erro

    def test_conta_com_separador(self):
        a = caso_minimo()
        c = a["CONTAS"][0].split("\t"); c[2] = "123-4"
        a["CONTAS"] = ["\t".join(c)]
        t = a["TITULARES"][0].split("\t"); t[2] = "123-4"
        a["TITULARES"] = ["\t".join(t)]
        e = a["EXTRATO"][0].split("\t"); e[3] = "123-4"
        a["EXTRATO"] = ["\t".join(e)]
        st, res = rodar(a)
        self.assertEqual([x.msg for x in st.erros], ["Conta com separador: 123-4"])

    def test_conta_mov2_com_extrato(self):
        a = caso_minimo()
        c = a["CONTAS"][0].split("\t"); c[6] = "2"
        a["CONTAS"] = ["\t".join(c)]
        st, res = rodar(a)
        self.assertEqual(len(st.erros), 1)
        self.assertTrue(st.erros[0].msg.startswith("Conta definida sem movimentação (MOVIMENTACAO_CONTA = 2)"))

    def test_conta_mov1_sem_extrato(self):
        a = caso_minimo()
        e = a["EXTRATO"]; a["EXTRATO"] = []
        a["ORIGEM_DESTINO"] = []
        st, res = rodar(a)
        self.assertTrue(any(m.startswith("Conta definida com movimentação (MOVIMENTACAO_CONTA = 1)") for m in msgs(st)))

    def test_atendimento_sem_movimentacao_alguma(self):
        a = caso_minimo()
        c = a["CONTAS"][0].split("\t"); c[6] = "3"
        a["CONTAS"] = ["\t".join(c)]
        a["EXTRATO"] = []; a["ORIGEM_DESTINO"] = []
        st, res = rodar(a)
        self.assertIn("Atendimento sem nenhuma movimentação de conta.", msgs(st))
        self.assertIn(0, [e.linha for e in st.erros])

    def test_extrato_fora_do_periodo_da_conta(self):
        a = caso_minimo()
        e = a["EXTRATO"][0].split("\t"); e[5] = "15032005"
        a["EXTRATO"] = ["\t".join(e)]
        st, res = rodar(a)
        self.assertEqual(len(st.erros), 1)
        self.assertTrue(st.erros[0].msg.startswith("A DATA_LANCAMENTO (15/03/2005) está fora do período"))
        self.assertEqual(res, {"AGENCIAS": True, "CONTAS": True, "TITULARES": True, "EXTRATO": False})

    def test_valor_extrato_diferente_da_soma_od(self):
        a = caso_minimo()
        o = a["ORIGEM_DESTINO"][0].split("\t"); o[2] = "40000"
        a["ORIGEM_DESTINO"] = ["\t".join(o)]
        st, _ = rodar(a)
        self.assertEqual(len(st.erros), 1)
        self.assertIn("não é igual ao somatório", st.erros[0].msg)

    def test_extrato_sem_origem_destino(self):
        a = caso_minimo(); a["ORIGEM_DESTINO"] = []
        st, _ = rodar(a)
        self.assertEqual(msgs(st), ["Lançamento em EXTRATO sem correspondente em ORIGEM_DESTINO. CODIGO_CHAVE_EXTRATO: 1"])

    def _ted(self, tipo="120", nat="D", valor="1000000", data="15032020", od_mod=None):
        a = caso_minimo()
        c = a["CONTAS"][0].split("\t"); c[4] = "01011990"       # conta aberta antes de 2006 para isolar a regra de OD
        a["CONTAS"] = ["\t".join(c)]
        e = a["EXTRATO"][0].split("\t"); e[5], e[8], e[9], e[10], e[11] = data, tipo, valor, nat, valor
        a["EXTRATO"] = ["\t".join(e)]
        o = a["ORIGEM_DESTINO"][0].split("\t"); o[2] = valor
        for k, v in (od_mod or {}).items():
            o[k] = v
        a["ORIGEM_DESTINO"] = ["\t".join(o)]
        return a

    def test_ted_doc_acima_do_limite_sem_identificacao(self):
        st, _ = rodar(self._ted())
        self.assertEqual(len(st.erros), 1)
        self.assertTrue(st.erros[0].msg.startswith("É OBRIGATÓRIA a identicação do depositante/favorecido de Transferências Interbancária"))

    def test_ted_doc_abaixo_do_limite_ou_antes_de_2006_nao_dispara(self):
        for kw in (dict(valor="999999"), dict(data="31122005"), dict(tipo="118")):
            st, _ = rodar(self._ted(**kw))
            self.assertEqual(msgs(st), [], kw)

    def test_ted_doc_nao_correntista_e_aceito(self):
        mod = {4: "", 5: "9999", 6: "99999999999999999999", 17: "nao-correntista"}
        st, _ = rodar(self._ted(od_mod=mod))
        self.assertEqual(msgs(st), [])

    def test_ted_doc_identificado_completo_passa(self):
        mod = {4: "237", 5: "1234", 6: "55555", 7: "1", 8: "1", 9: CPF_SINT, 10: "BENEFICIARIO TESTE"}
        st, _ = rodar(self._ted(od_mod=mod))
        self.assertEqual(msgs(st), [])

    def test_cpf_od_invalido_em_transferencia(self):
        mod = {4: "237", 5: "1234", 6: "55555", 7: "1", 8: "1", 9: "12345678900", 10: "BENEFICIARIO TESTE"}
        st, _ = rodar(self._ted(valor="1000", od_mod=mod))
        self.assertEqual(len(st.erros), 1)
        self.assertTrue(st.erros[0].msg.startswith("O campo CPF_CNPJ_PESSOA_OD com o valor 12345678900"))

    def test_eleitoral_usa_outras_regras_de_od(self):
        # Tipo 201 (depósito) com valor >= 10000 exige CPF/CNPJ em caso eleitoral; no não-eleitoral não há regra equivalente
        a = caso_minimo()
        st, _ = rodar(a, Ctx(eleitoral=True))
        self.assertEqual(len(st.erros), 1)
        self.assertTrue(st.erros[0].msg.startswith("É OBRIGATÓRIA a identicação do depositante/favorecido de depósito em cheque"))
        st, _ = rodar(a, Ctx(eleitoral=False))
        self.assertEqual(msgs(st), [])


if __name__ == "__main__":
    unittest.main()
