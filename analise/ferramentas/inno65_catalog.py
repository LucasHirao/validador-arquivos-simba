"""Lê bloco1/bloco2 já descomprimidos e cataloga file entries + location entries (Inno 6.5.0)."""
import sys, struct, json
S = sys.argv[1]
b1 = open(S + "/bloco1.bin", "rb").read(); b2 = open(S + "/bloco2.bin", "rb").read()

# --- contagens do TSetupHeader: 34 strings UTF-16 + 4 AnsiStrings, depois 17 inteiros ---
p = 0; hs = []
for _ in range(34):
    n = struct.unpack_from("<I", b1, p)[0]; p += 4; hs.append(b1[p:p+n].decode("utf-16le")); p += n
for _ in range(4):
    n = struct.unpack_from("<I", b1, p)[0]; p += 4; p += n
names = "Language CustomMessage Permission Type Component Task Dir ISSigKey File FileLocation Icon Ini Registry InstallDelete UninstallDelete Run UninstallRun".split()
counts = dict(zip(names, struct.unpack_from("<17i", b1, p)))
print("AppName/AppVerName/AppId/Publisher/Version:", hs[0], "|", hs[1], "|", hs[2], "|", hs[4], "|", hs[9])
print("DefaultDirName:", hs[10], "| SevenZipLib:", repr(hs[33]))
print("contagens:", {k: v for k, v in counts.items() if v})

# --- location entries (85 bytes, packed) ---
LS = 85; assert len(b2) == counts["FileLocation"] * LS, (len(b2), counts["FileLocation"])
locs = []
for i in range(counts["FileLocation"]):
    fs, ls, so, cso, osz, csz = struct.unpack_from("<iiiqqq", b2, i * LS)
    sha = b2[i*LS+36:i*LS+68].hex(); flags = b2[i*LS+84]
    locs.append(dict(i=i, first=fs, last=ls, start=so, sub=cso, size=osz, csize=csz, sha256=sha, flags=flags))

# --- file entries: varredura por padrão (15 strings + 1 ansi + 33 + ... ) ---
FIXED = 33 + 10 + 10 + 4 + 4 + 8 + 2 + 5 + 1
def try_parse(o):
    q = o; strs = []
    for _ in range(15):
        if q + 4 > len(b1): return None
        n = struct.unpack_from("<I", b1, q)[0]
        if n > 2000 or n % 2: return None
        q += 4
        try: strs.append(b1[q:q+n].decode("utf-16le"))
        except UnicodeDecodeError: return None
        q += n
    n = struct.unpack_from("<I", b1, q)[0]
    if n > 5000: return None
    q += 4 + n
    if q + FIXED > len(b1): return None
    f = q + 33 + 20
    loc, attr, ext = struct.unpack_from("<iiq", b1, f)
    ftype = b1[q + FIXED - 1]
    if not (-1 <= loc < len(locs)) or ftype > 1: return None
    return dict(src=strs[0], dest=strs[1], loc=loc, opts=int.from_bytes(b1[q+FIXED-6:q+FIXED-1], "little"), ftype=ftype, end=q+FIXED)
files = []; o = 0
while o < len(b1) and len(files) < counts["File"]:
    r = try_parse(o)
    if r and (r["dest"] or r["src"]) and all(c.isprintable() for c in r["src"] + r["dest"]):
        files.append(r); o = r["end"]
    else: o += 1
print(f"file entries encontradas: {len(files)} de {counts['File']}")
json.dump(dict(header=dict(strings=hs, counts=counts), files=files, locs=locs), open(S + "/catalogo.json", "w"), indent=1)
import collections
for f in files[:25]:
    l = locs[f["loc"]] if f["loc"] >= 0 else None
    print(f'{f["dest"][:70]:70} loc={f["loc"]:>4} size={l["size"] if l else "-"}')
