"""Extrai arquivos do Inno 6.5.0 (somente leitura do .exe). Uso: inno65_extract.py <exe> <catalogo.json> <destino|-> """
import sys, json, struct, lzma, hashlib, os
exe, cat, dest = sys.argv[1:4]
d = open(exe, "rb").read(); C = json.load(open(cat))
OFF1 = 882176; locs = C["locs"]; files = C["files"]
cache = {}
def chunk_data(l):
    key = (l["start"], l["csize"]) 
    if key in cache: return cache[key]
    p = OFF1 + l["start"]; assert d[p:p+4] == b"zlb\x1a", d[p:p+4]
    raw = d[p+4:p+4+l["csize"]]
    if l["flags"] & 16:  # floChunkCompressed
        b0 = raw[0]; ds = (2 | (b0 & 1)) << (b0 // 2 + 11)  # LZMA2: 1º byte = código do dicionário
        out = lzma.LZMADecompressor(lzma.FORMAT_RAW, filters=[{"id": lzma.FILTER_LZMA2, "dict_size": ds}]).decompress(raw[1:])
    else: out = raw
    cache[key] = out; return out
def bcj_decode(data):  # port de TransformCallInstructions(Encode=False), Inno 6.5.0 (Compression.Base.pas), blocos de 64 KiB
    out = bytearray(); addr_off = 0
    for s in range(0, len(data), 65536):
        P = bytearray(data[s:s+65536]); size = len(P)
        if size >= 5:
            n = size - 4; i = 0
            while i < n:
                if P[i] in (0xE8, 0xE9):
                    i += 1
                    if P[i+3] in (0x00, 0xFF):
                        addr = (addr_off + i + 4) & 0xFFFFFF
                        rel = P[i] | (P[i+1] << 8) | (P[i+2] << 16)
                        rel = (rel - addr) & 0xFFFFFFFF
                        if rel & 0x800000: P[i+3] = (~P[i+3]) & 0xFF
                        P[i] = rel & 0xFF; P[i+1] = (rel >> 8) & 0xFF; P[i+2] = (rel >> 16) & 0xFF
                    i += 4
                else: i += 1
        addr_off = (addr_off + size) & 0xFFFFFFFF
        out += P
    return bytes(out)
ok = bad = 0; seen = set()
for f in files:
    if f["loc"] < 0 or not f["dest"] or f["dest"] in seen: continue
    l = locs[f["loc"]]; ch = chunk_data(l)
    data = ch[l["sub"]: l["sub"] + l["size"]]
    if l["flags"] & 4: data = bcj_decode(data)
    good = hashlib.sha256(data).hexdigest() == l["sha256"]
    ok += good; bad += (not good); seen.add(f["dest"])
    if not good: print("SHA256 divergente:", f["dest"], "flags", l["flags"])
    if dest != "-":
        if f["dest"].rstrip("\\") in ("{app}",): print("entrada anômala (dest sem nome de arquivo) ignorada: loc", f["loc"], "src", repr(f["src"])); continue
        rel = f["dest"].replace("{app}", "app").replace("\\", "/").lstrip("/")
        p = os.path.abspath(os.path.join(dest, rel))
        if not p.startswith(os.path.abspath(dest) + os.sep): print("ignorado (path):", rel); continue
        os.makedirs(os.path.dirname(p), exist_ok=True); open(p, "wb").write(data)
print(f"verificados: sha256_ok={ok} divergentes={bad}")
