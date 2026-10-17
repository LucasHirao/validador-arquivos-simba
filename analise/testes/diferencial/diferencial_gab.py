"""Diferencial GAB: ORIGINAL (Nivel2Gab) x Java. Duas comparações:
 (A) defeito reproduzido:  original padrão            x Java --defeito-gab true   (COMPAT)
 (B) lógica pretendida:    original com remendo em memória (-Dgab.semdefeito) x Java COMPAT --defeito-gab false
Uso: python3 diferencial_gab.py <pasta_cenarios>"""
import pathlib, shutil, subprocess, sys
RAIZ = pathlib.Path(__file__).resolve().parents[3]; APP = RAIZ / "original/extraido/app"
JC = RAIZ / "implementacao/validador-independente/target/classes"; W = pathlib.Path("/tmp/n2g")
src = pathlib.Path(sys.argv[1])
shutil.rmtree(W, ignore_errors=True); (W / "cwd").mkdir(parents=True); (W / "cls").mkdir(); shutil.copytree(src, W / "casos")
subprocess.run(["javac", "-d", str(W / "cls"), "-cp", str(APP / "simba-validador.jar"), str(pathlib.Path(__file__).parent / "Nivel2Gab.java")], check=True)
def orig(extra):
    r = subprocess.run(["bwrap", "--unshare-net", "--ro-bind", "/", "/", "--dev", "/dev", "--proc", "/proc", "--tmpfs", "/tmp", "--bind", str(W), str(W), "--chdir", str(W / "cwd"),
                        "java", "-Djava.awt.headless=true"] + extra + ["-cp", f"{W / 'cls'}:{APP / 'simba-validador.jar'}", "Nivel2Gab", str(W / "casos")], capture_output=True, text=True, timeout=3000)
    return r
orig([]); orig(["-Dgab.semdefeito=1"])
for flag in ("true", "false"):
    subprocess.run(["java", "-cp", str(JC), "estudo.simba.validador.Cli", "--tipo", "corretora", "--lote", str(W / "casos"), "--modo", "compat", "--defeito-gab", flag], check=True)
def comparar(nome, o, j):
    tot = ig = so = 0; div = []
    for d in sorted((W / "casos").iterdir()):
        if not d.is_dir(): continue
        a = (d / o).read_text(encoding="utf-8"); b = (d / j).read_text(encoding="utf-8"); tot += 1
        infra = lambda t: t.startswith('#FALHA_HARNESS') or 'R-INFRA' in t or 'falha de infraestrutura' in t
        if a == b: ig += 1
        elif infra(a) and infra(b): ig += 1
        elif sorted(a.splitlines()) == sorted(b.splitlines()): so += 1
        else: div.append((d.name, a, b))
    print(f"{nome}: cenarios={tot} identicos={ig} so_ordem={so} DIVERGENCIAS_REAIS={len(div)}")
    for n, a, b in div[:6]:
        print("  -----", n); al, bl = a.splitlines(), b.splitlines()
        for x in al:
            if x not in bl: print("    só ORIGINAL:", x[:230])
        for x in bl:
            if x not in al: print("    só JAVA    :", x[:230])
    return len(div)
r = comparar("(A) defeito reproduzido", "saida-original.txt", "saida-compat-defeito.txt") + comparar("(B) logica pretendida ", "saida-original-semdefeito.txt", "saida-compat-semdefeito.txt")
sys.exit(1 if r else 0)
