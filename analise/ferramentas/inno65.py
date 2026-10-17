"""Leitor somente-leitura de blocos do Inno Setup 6.5.0 (baseado em issrc tag is-6_5_0).
Uso: inno65.py <exe> <diretório de saída para dumps de blocos>"""
import sys, struct, zlib, lzma, os
F, OUT = sys.argv[1], sys.argv[2]
d = open(F, "rb").read()

# --- tabela de offsets (TSetupLdrOffsetTable, versão 2) ---
i = d.find(b"rDlPtS\xcd\xe6\xd7{\x0b*")
ver, total, off_exe = struct.unpack_from("<IqQ", d, i + 12)
unc_exe, crc_exe, off0, off1 = struct.unpack_from("<IiQQ", d, i + 12 + 4 + 8 + 8)
pad, table_crc = struct.unpack_from("<Ii", d, i + 12 + 4 + 8 + 8 + 4 + 4 + 16)
calc = zlib.crc32(d[i:i + 12 + 4 + 8 + 8 + 4 + 4 + 16 + 4]) & 0xffffffff
print(f"offset table @ {i}: version={ver} total={total} off_exe={off_exe} off0={off0} off1={off1} crc_ok={calc == (table_crc & 0xffffffff)}")

# --- setup-0: ID + SetupEncryptionHeader + blocos ---
p = off0
print("ID:", d[p:p+64].rstrip(b"\0"))
p += 64
ehcrc = struct.unpack_from("<I", d, p)[0]; p += 4
eh = d[p:p+49]; p += 49
print("EncryptionHeader crc_ok:", zlib.crc32(eh) & 0xffffffff == ehcrc, "EncryptionUse:", eh[0], "KDFIterations:", struct.unpack_from("<i", eh, 17)[0])

def read_block(p):
    crc, stored, comp = struct.unpack_from("<IIB", d, p)
    assert zlib.crc32(d[p+4:p+9]) & 0xffffffff == crc, "CRC do header do bloco"
    raw = d[p+9:p+9+stored]
    out = bytearray(); q = 0
    while q < len(raw):
        c = struct.unpack_from("<I", raw, q)[0]; chunk = raw[q+4:q+4+4096]
        assert zlib.crc32(chunk) & 0xffffffff == c, "CRC de sub-bloco"
        out += chunk; q += 4 + len(chunk)
    if comp:
        props = out[0]; dict_size = struct.unpack_from("<I", out, 1)[0]
        lc = props % 9; lp = (props // 9) % 5; pb = props // 45
        dec = lzma.LZMADecompressor(lzma.FORMAT_RAW, filters=[{"id": lzma.FILTER_LZMA1, "dict_size": dict_size, "lc": lc, "lp": lp, "pb": pb}])
        out = dec.decompress(bytes(out[5:]))
    return bytes(out), p + 9 + stored, stored, comp

b1, p, s1, c1 = read_block(p); print(f"bloco1: stored={s1} compressed={c1} descomprimido={len(b1)}")
b2, p, s2, c2 = read_block(p); print(f"bloco2: stored={s2} compressed={c2} descomprimido={len(b2)}  fim em {p}")
os.makedirs(OUT, exist_ok=True)
open(os.path.join(OUT, "bloco1.bin"), "wb").write(b1); open(os.path.join(OUT, "bloco2.bin"), "wb").write(b2)
