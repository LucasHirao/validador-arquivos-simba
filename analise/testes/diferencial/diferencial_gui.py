"""Diferencial NÍVEL 3: TELA REAL (frmSelecionaDiretorio) x Java compat, + hash MD5 gravado + linhas + arquivos copiados.
Roda na sessão gráfica do usuário (DISPLAY/XAUTHORITY herdados), em bwrap --unshare-net. A lista de órgãos é injetada (sem HTTP).
Uso: python3 diferencial_gui.py <pausa_ms> <pasta_cenarios> [<pasta_cenarios> ...] [--max N cada]"""
import hashlib, os, pathlib, re, shutil, subprocess, sys
RAIZ = pathlib.Path(__file__).resolve().parents[3]; APP = RAIZ / "original/extraido/app"
JC = RAIZ / "implementacao/validador-independente/target/classes"; W = pathlib.Path("/tmp/n3work")
pausa = sys.argv[1]; fontes = [a for a in sys.argv[2:] if not a.startswith("--") and not a.isdigit()]
mx = int(sys.argv[sys.argv.index("--max") + 1]) if "--max" in sys.argv else 10**9
shutil.rmtree(W, ignore_errors=True); (W / "cwd").mkdir(parents=True); (W / "cls").mkdir(); (W / "casos").mkdir()
for f in fontes:
    ds = [d for d in sorted(pathlib.Path(f).iterdir()) if d.is_dir() and not d.name.startswith("v3")][:mx]
    for d in ds: shutil.copytree(d, W / "casos" / (pathlib.Path(f).name + "-" + d.name))
subprocess.run(["javac", "-d", str(W / "cls"), "-cp", str(APP / "simba-validador.jar"), str(pathlib.Path(__file__).parent / "Nivel3.java")], check=True)
env = dict(os.environ)
cmd = ["bwrap", "--unshare-net", "--ro-bind", "/", "/", "--dev", "/dev", "--proc", "/proc", "--tmpfs", "/tmp", "--bind", str(W), str(W), "--bind", "/tmp/.X11-unix", "/tmp/.X11-unix",
       "--setenv", "DISPLAY", env.get("DISPLAY", ":0"), "--setenv", "XAUTHORITY", env.get("XAUTHORITY", ""), "--chdir", str(W / "cwd"),
       "java", f"-Dgui.pausa={pausa}", "-cp", f"{W / 'cls'}:{APP / 'simba-validador.jar'}", "Nivel3", str(W / "casos")]
r = subprocess.run(cmd, capture_output=True, text=True, timeout=3400); (W / "stderr.log").write_text(r.stderr + r.stdout)
subprocess.run(["java", "-cp", str(JC), "estudo.simba.validador.Cli", "--lote", str(W / "casos"), "--modo", "compat"], check=True)
ordem = ["AGENCIAS", "CONTAS", "TITULARES", "EXTRATO", "ORIGEM_DESTINO"]
tot = ig_msg = so_ordem = ig_hash = ig_copia = ig_linhas = falha = 0; div = []; hash_div = []
for d in sorted((W / "casos").iterdir()):
    if not d.is_dir(): continue
    tot += 1
    g = (d / "saida-gui.txt").read_text(encoding="utf-8") if (d / "saida-gui.txt").exists() else "#FALHA_GUI sem saida\n"
    j = (d / "saida-compat.txt").read_text(encoding="utf-8")
    if g.startswith("#FALHA"): falha += 1; div.append((d.name, g, j)); continue
    if g == j: ig_msg += 1
    elif sorted(g.splitlines()) == sorted(j.splitlines()): so_ordem += 1
    else: div.append((d.name, g, j))
    est = (d / "gui-estado.txt").read_text(encoding="utf-8")
    caso = re.search(r"(.*)_AGENCIAS\.txt", next(p.name for p in d.iterdir() if p.name.endswith("_AGENCIAS.txt"))).group(1)
    ok_h = ok_l = ok_c = True
    for i, tp in enumerate(ordem, 1):
        arq = d / f"{caso}_{tp}.txt"; b = arq.read_bytes()
        esperado = f"{int(hashlib.md5(b).hexdigest(), 16):032x}"
        m = re.search(rf"hash{i}=(\S+)\s+linhas{i}=(\S+)", est)
        if not m or m.group(1) != esperado: ok_h = False; hash_div.append((d.name, tp, m.group(1) if m else None, esperado))
        if m and m.group(2) != "null" and int(m.group(2)) != len([x for x in b.split(b"\n") if x != b""]): ok_l = False
        if f"copiado={arq.name}" not in est: ok_c = False
    ig_hash += ok_h; ig_linhas += ok_l; ig_copia += ok_c
print(f"cenarios={tot} | mensagens+conteudo identicos={ig_msg} so_ordem={so_ordem} | hash_ok={ig_hash} linhas_ok={ig_linhas} copia_ok={ig_copia} | falhas_gui={falha} | DIVERGENCIAS_REAIS={len(div)}")
for n, g, j in div[:6]:
    print("  -----", n); gl, jl = g.splitlines(), j.splitlines()
    for x in gl:
        if x not in jl: print("    só TELA:", x[:200])
    for x in jl:
        if x not in gl: print("    só JAVA:", x[:200])
for h in hash_div[:5]: print("  hash divergente:", h)
sys.exit(1 if div or hash_div else 0)
