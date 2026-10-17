"""Hash MD5 do original (Arquivo.obterHashArquivo) x modelo, para receptor >=4 (com zeros à esquerda) e <4 (sem). Sem rede."""
import hashlib, pathlib, subprocess, sys, itertools
RAIZ = pathlib.Path(__file__).resolve().parents[3]; APP = RAIZ / "original/extraido/app"
W = pathlib.Path("/tmp/hashwork"); W.mkdir(exist_ok=True)
arqs = []
for n in itertools.count():                       # arquivos sintéticos cujo MD5 começa por 0 (efeito do zero à esquerda) e outros
    p = W / f"a{n}.txt"; p.write_bytes(f"conteudo sintetico {n}\n".encode())
    arqs.append(p)
    if len(arqs) >= 400: break
zero = [p for p in arqs if hashlib.md5(p.read_bytes()).hexdigest().startswith("0")]
vazio = W / "vazio.txt"; vazio.write_bytes(b""); arqs.append(vazio)
subprocess.run(["javac", "-d", str(W), "-cp", str(APP / "simba-validador.jar"), str(pathlib.Path(__file__).parent / "HashHarness.java")], check=True)
div = 0
for ver, padded in (("4.0.0", True), ("3.9.0", False), ("1.0.0", False)):
    r = subprocess.run(["bwrap", "--unshare-net", "--ro-bind", "/", "/", "--dev", "/dev", "--proc", "/proc", "--tmpfs", "/tmp", "--bind", str(W), str(W),
                        "java", "-Djava.awt.headless=true", "-cp", f"{W}:{APP / 'simba-validador.jar'}", "HashHarness", ver] + [str(p) for p in arqs],
                       capture_output=True, text=True, timeout=600)
    saida = {l.split("\t")[0]: l.split("\t")[1] for l in r.stdout.splitlines() if l.count("\t") == 1 and l.startswith(str(W))}
    for p in arqs:
        h = int(hashlib.md5(p.read_bytes()).hexdigest(), 16)
        esperado = f"{h:032x}" if padded else format(h, "x")
        if saida.get(str(p)) != esperado: div += 1; print("DIV", ver, p.name, saida.get(str(p)), esperado)
    print(f"receptor {ver}: arquivos={len(arqs)} (com MD5 iniciado em 0: {len(zero)}) formato={'%032x' if padded else 'sem padding'}")
print("divergencias:", div); sys.exit(1 if div else 0)
