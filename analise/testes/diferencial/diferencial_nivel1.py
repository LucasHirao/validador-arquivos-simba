"""Execução diferencial NÍVEL 1: compara o modelo (validador_ref) com funções puras do JAR original, rodadas em bwrap SEM REDE.
Uso: python3 diferencial_nivel1.py   (requer: javac/java, bwrap, original/extraido/app)"""
import itertools, os, pathlib, random, subprocess, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import validador_ref as r

RAIZ = pathlib.Path(__file__).resolve().parents[3]
APP = RAIZ / "original/extraido/app"
OUT = pathlib.Path(os.environ.get("DIFF_OUT", "/tmp/diff_nivel1")); OUT.mkdir(parents=True, exist_ok=True)
CP = ":".join([str(APP / "simba-validador.jar"), str(APP / "lib/spea-client-2.4.4.jar"), str(APP / "lib/slf4j-api-2.0.17.jar"),
               str(APP / "lib/spring-context-6.2.18.jar"), str(APP / "lib/spring-core-6.2.18.jar"), str(OUT)])
H = lambda s: s.encode().hex()
rnd = random.Random(7)
FUNCS = {'split','data','cpf','cnpj','cnabC','cnabD','cnabCD','rmzeros','zeros11','empty'}

vet = []
alf = "ab \t01"
for _ in range(1500):
    vet.append(("split", "".join(rnd.choice(alf) for _ in range(rnd.randint(0, 12)))))
vet += [("split", s) for s in ["a\tb\tc", "\tb\tc", "\t", "\t\t", "", "a\tb\t\t", "  a \t b ", " x\ty"]]
dig = "0123456789"
for _ in range(3000):
    vet.append(("data", "".join(rnd.choice(dig) for _ in range(8))))
for d, m, y in itertools.product([0, 1, 28, 29, 30, 31, 32], [0, 1, 2, 4, 12, 13], [1899, 1900, 2000, 2023, 2024, 2100]):
    vet.append(("data", f"{d:02d}{m:02d}{y:04d}"))
vet += [("data", s) for s in ["", "1", "0101200", "010120000", "ab012000", "0101 200", "+101200 ", "01012+00", "0101-100"]]
for _ in range(3000):
    vet.append(("cpf", "".join(rnd.choice(dig) for _ in range(rnd.choice([9, 10, 11, 11, 11])))))
    b = "".join(rnd.choice(dig) for _ in range(9)); vet.append(("cpf", b + r.dv_cpf(b)))
alfa = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
for _ in range(3000):
    b = "".join(rnd.choice(alfa) for _ in range(12)); vet.append(("cnpj", b + r.dv_cnpj(b)))
    vet.append(("cnpj", "".join(rnd.choice(alfa) for _ in range(rnd.choice([8, 12, 14, 14])))))
vet += [("cnpj", s) for s in ["", "11222333000181", "00000000000000", "ab", "12abc34501de35"]]
vet += [("cpf", s) for s in ["", "00000000000", "11111111111", "52998224725", "abc", "5299822472a"]]
for n in list(range(0, 1100)) + [-1, 99999]:
    for f in ("cnabC", "cnabD", "cnabCD"):
        vet.append((f, str(n)))
vet += [("rmzeros", s) for s in ["0012", "12", "0", "000", "", "0a"]]
vet += [("zeros11", s) for s in ["1", "12345678901", "123456789012", ""]]
vet += [("empty", s) for s in ["", " ", "\t", " ", " a", "\x01", "\x20"]]

inp = "\n".join(f"{f}\t{H(x)}" for f, x in vet) + "\n"
(OUT / "vetores.tsv").write_text(inp)
subprocess.run(["javac", "-d", str(OUT), "-cp", CP, str(pathlib.Path(__file__).parent / "Harness.java")], check=True)
cmd = ["bwrap", "--unshare-net", "--ro-bind", "/", "/", "--dev", "/dev", "--proc", "/proc", "--tmpfs", "/tmp", "--ro-bind", str(OUT), str(OUT),
       "java", "-Duser.language=pt", "-Duser.country=BR", "-cp", CP, "Harness"]
res = subprocess.run(cmd, input=inp, capture_output=True, text=True, timeout=600)
if res.returncode: print(res.stderr[-2000:]); sys.exit(1)
(OUT / "resultado_java.tsv").write_text(res.stdout)

def modelo(f, x):
    try:
        if f == "split": d = r.split_tab(x); return ",".join([str(len(d))] + [H(s) for s in d])
        if f == "data": return str(r.verifica_data(x)).lower()
        if f == "cpf": return str(r.validar_cpf(x)).lower()
        if f == "cnpj": return str(r.validar_cnpj(x)).lower()
        if f == "cnabC": return str(r.cnab_credito_invalido(int(x))).lower()
        if f == "cnabD": return str(r.cnab_debito_invalido(int(x))).lower()
        if f == "cnabCD": return str(r.cnab_credito_debito_invalido(int(x))).lower()
        if f == "rmzeros": return H(r.remover_zeros_esquerda(x))
        if f == "zeros11": return H(str(r.completar_zeros(x, 11)))
        if f == "empty": return str(r.is_empty(x)).lower()
    except r.JavaIndexError: return "EXC:StringIndexOutOfBoundsException"
    except Exception as e: return "EXC:" + type(e).__name__

linhas = [l for l in res.stdout.splitlines() if l.split("\t")[0] in FUNCS and l.count("\t") == 2]; assert len(linhas) == len(vet), (len(linhas), len(vet))
div = {}; tot = {}
for (f, x), l in zip(vet, linhas):
    java = l.split("\t")[2]; m = modelo(f, x); tot[f] = tot.get(f, 0) + 1
    if java != m: div.setdefault(f, []).append((x, java, m))
for f in tot: print(f"{f:8} vetores={tot[f]:5} divergências={len(div.get(f, []))}")
for f, lst in div.items():
    for x, j, m in lst[:6]: print("  DIV", f, repr(x), "java=", j, "modelo=", m)
sys.exit(1 if div else 0)
