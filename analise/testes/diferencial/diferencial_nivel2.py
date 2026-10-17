"""Execução diferencial NÍVEL 2: ORIGINAL (pipeline real em HSQLDB, headless, sem rede) x Java compat x modelo Python.
Uso: python3 diferencial_nivel2.py <pasta_com_cenarios> [--mostrar N]
Cada cenário: subpasta com <caso>_*.txt, investigados.tsv e esperado.txt (modelo). Resultado: saida-original.txt, saida-compat.txt."""
import pathlib, shutil, subprocess, sys

RAIZ = pathlib.Path(__file__).resolve().parents[3]
APP = RAIZ / "original/extraido/app"
HARNESS = pathlib.Path(__file__).parent / "Nivel2.java"
JAVA_CLASSES = RAIZ / "implementacao/validador-independente/target/classes"
W = pathlib.Path("/tmp/n2work")
src = pathlib.Path(sys.argv[1]); mostrar = int(sys.argv[sys.argv.index("--mostrar") + 1]) if "--mostrar" in sys.argv else 5

shutil.rmtree(W, ignore_errors=True); (W / "cwd").mkdir(parents=True); (W / "cls").mkdir()
shutil.copytree(src, W / "casos")
subprocess.run(["javac", "-d", str(W / "cls"), "-cp", str(APP / "simba-validador.jar"), str(HARNESS)], check=True)
cp = f"{W / 'cls'}:{APP / 'simba-validador.jar'}"
r = subprocess.run(["bwrap", "--unshare-net", "--ro-bind", "/", "/", "--dev", "/dev", "--proc", "/proc", "--tmpfs", "/tmp", "--bind", str(W), str(W),
                    "--chdir", str(W / "cwd"), "java", "-Djava.awt.headless=true", "-cp", cp, "Nivel2", str(W / "casos")],
                   capture_output=True, text=True, timeout=3000)
(W / "stderr.log").write_text(r.stderr)
subprocess.run(["java", "-cp", str(JAVA_CLASSES), "estudo.simba.validador.Cli", "--lote", str(W / "casos"), "--modo", "compat"], check=True)

tot = ig_java = ig_py = falha = so_ordem = 0; div = []
for d in sorted((W / "casos").iterdir()):
    if not d.is_dir(): continue
    orig = (d / "saida-original.txt").read_text(encoding="utf-8") if (d / "saida-original.txt").exists() else "#SEM_SAIDA\n"
    java = (d / "saida-compat.txt").read_text(encoding="utf-8")
    py = (d / "esperado.txt").read_text(encoding="utf-8") if (d / "esperado.txt").exists() else None
    tot += 1
    if orig.startswith("#FALHA") or orig.startswith("#SEM"): falha += 1; div.append((d.name, orig, java, py)); continue
    ig_java += orig == java; ig_py += (py == orig) if py else 0
    if orig != java:
        # consultas SQL sem ORDER BY: a ordem das linhas de erro não faz parte do contrato -> compara como multiconjunto
        if sorted(orig.splitlines()) == sorted(java.splitlines()): so_ordem += 1
        else: div.append((d.name, orig, java, py))
print(f"cenarios={tot}  identicos(com ordem):{ig_java}  so_ordem_diferente:{so_ordem}  original==modelo:{ig_py}  falhas_harness:{falha}  DIVERGENCIAS_REAIS:{len(div)}")
for nome, o, j, p in div[:mostrar]:
    print("-----", nome); ol, jl = o.splitlines(), j.splitlines()
    for x in ol:
        if x not in jl: print("  só ORIGINAL:", x[:260])
    for x in jl:
        if x not in ol: print("  só JAVA    :", x[:260])
    if len(ol) == len(jl) and sorted(ol) == sorted(jl): print("  (mesmas linhas, ordem diferente)")
sys.exit(1 if div else 0)
