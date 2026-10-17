# 01 — Inventário de artefatos

## Originais
| Artefato | Caminho | Tamanho (bytes) | SHA-256 |
|---|---|---|---|
| Setup-SIMBA-Validador-6.2.6.exe | original/Setup-SIMBA-Validador-6.2.6.exe | 50.824.043 | 67c44d7e094fa135b31cfcbb70d4ea10b26d9f2d0d60f8ed8f3986fd8e8db6c2 |

O arquivo foi movido de `./` para `./original/` (hash conferido antes e depois). Nada do original foi executado ou modificado.

## Derivados (gerados nesta investigação)
| Caminho | Origem | Observação |
|---|---|---|
| original/extraido/ (215 arquivos, 69 MB) | Extração do instalador com `analise/ferramentas/inno65_extract.py` | Cada arquivo confere com o SHA-256 registrado no instalador (216 únicos verificados). **Binários de terceiros: não executar.** |
| original/extraido/app/simba-validador.jar | idem | 540.746 B; 249 classes; Java 25 (classfile 69) |
| original/extraido/app/lib/*.jar (40) e app/jre/ | idem | dependências e JRE 25 embutido |
| analise/decompilado/ | `simba-validador.jar` + Vineflower 1.12.0 (SHA-1 conferido no Maven Central) | 159 `.java`, sem falhas |
| analise/decompilado-libs/spea-client/ | `app/lib/spea-client-2.4.4.jar` + Vineflower | 83 `.java` |
| analise/catalogo-instalador.json | `inno65_catalog.py` | file entries + location entries do Inno |
| analise/ferramentas/ | scripts próprios | `inno65*.py`, `carve_zip.py` |
| analise/testes/ | modelo de referência, testes e casos sintéticos | ver 05 |

## Ferramentas
Disponíveis: file, sha256sum, strings, 7z, objdump, readelf, xxd, javap/javac/java (OpenJDK 25.0.4.1).
Instaladas nesta sessão (apt): cmake, g++, make, libboost-*-dev, liblzma-dev, innoextract 1.9 (não suporta Inno 6.5; compilação do HEAD também falhou — ver 02).
Baixadas da rede (com sua autorização): código-fonte `innoextract` (GitHub), `issrc` tag `is-6_5_0` (referência de formato), Vineflower 1.12.0 (Maven Central). Ficaram no scratchpad da sessão, fora do projeto.
