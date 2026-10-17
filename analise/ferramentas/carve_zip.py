"""Percorre entradas ZIP locais (PK\x03\x04) em streaming; lista ou extrai. Uso: carve_zip.py <arquivo> <offset> [destino]"""
import sys, struct, zlib, os
f, off = sys.argv[1], int(sys.argv[2]); dest = sys.argv[3] if len(sys.argv) > 3 else None
d = open(f, "rb").read()
n = ok = bad = 0; tot = 0; stop = None
while True:
    if d[off:off+4] != b"PK\x03\x04":
        stop = off; break
    ver, flag, meth, t, dt, crc, csz, usz, nl, el = struct.unpack_from("<HHHHHIIIHH", d, off+4)
    name = d[off+30:off+30+nl].decode("utf-8", "replace")
    pos = off + 30 + nl + el
    if name.endswith("/"):
        data = b""; end = pos
    elif meth == 8:
        z = zlib.decompressobj(-15)
        try:
            data = z.decompress(d[pos:]); end = len(d) - len(z.unused_data)
            if not z.eof: raise zlib.error("truncado")
        except zlib.error as e:
            print("ERRO", name, e); stop = off; break
    elif meth == 0:
        data = d[pos:pos+csz]; end = pos + csz
    else:
        print("método desconhecido", meth, name); stop = off; break
    if flag & 8:  # data descriptor
        if d[end:end+4] == b"PK\x07\x08": end += 4
        crc, csz, usz = struct.unpack_from("<III", d, end); end += 12
    c = zlib.crc32(data) & 0xffffffff
    if c == crc: ok += 1
    else: bad += 1; print("CRC divergente", name)
    n += 1; tot += len(data)
    if dest is None:
        if n <= 15 or n % 500 == 0: print(f"{off:>10} {len(data):>9} {name}")
    elif not name.endswith("/"):
        p = os.path.join(dest, name)
        if not os.path.abspath(p).startswith(os.path.abspath(dest) + os.sep): print("caminho suspeito ignorado", name); off = end; continue
        os.makedirs(os.path.dirname(p), exist_ok=True); open(p, "wb").write(data)
    off = end
print(f"entradas={n} crc_ok={ok} crc_ruim={bad} bytes={tot} parou_em={stop} (arquivo tem {len(d)} bytes)")
if stop: print("bytes ali:", d[stop:stop+32].hex(" "))
