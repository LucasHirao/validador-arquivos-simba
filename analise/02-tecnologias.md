# 02 — Identificação tecnológica (Fase 1 concluída)

Artefato: original/Setup-SIMBA-Validador-6.2.6.exe — SHA-256 67c44d7e094fa135b31cfcbb70d4ea10b26d9f2d0d60f8ed8f3986fd8e8db6c2

## Instalador — CONFIRMADO
- PE32 (Windows GUI, i386), Inno Setup **6.5.0**, stub Delphi 36.0 (strings `Inno Setup Setup Data (6.5.0)`, `Embarcadero Delphi for Win32 compiler version 36.0`). Sem CLR (não é .NET).
- Layout do container (decodificado a partir do código-fonte Inno Setup tag `is-6_5_0`, `Projects/Src/Shared.Struct.pas`):
  - `TSetupLdrOffsetTable` (versão 2) em 878144: TotalSize=50.824.043 (= tamanho do arquivo), Offset0=49.793.011 (setup-0: headers), Offset1=882.176 (setup-1: dados); CRC da tabela confere.
  - setup-0: ID de 64 bytes + `SetupEncryptionHeader` (CRC ok; **EncryptionUse=0 = sem criptografia**; KDFIterations=220000 é só parâmetro) + 2 blocos LZMA (bloco1=208.645 B, bloco2=18.360 B); todos os CRCs conferem.
  - Dados: 1 chunk sólido LZMA2 (48,9 MB comprimido → 71,2 MB), 216 location entries (85 bytes cada).
  - 41 executáveis/DLLs usam o filtro `floCallInstructionOptimized` (x86 CALL/JMP, `TransformCallInstructions`).
- Todos os 216 arquivos únicos têm SHA-256 igual ao registrado no instalador (verificação independente da extração).
- `innoextract` 1.9 (apt) e HEAD do GitHub (fev/2025) NÃO suportam 6.5.0 (limite 6.0.5 / 6.4.0). Extração feita com scripts próprios em `analise/ferramentas/`.

## Aplicativo — CONFIRMADO: Java
- `app/simba-validador.jar` (540.746 B, 249 classes): Manifest `Implementation-Title: Validador SIMBA`, `Implementation-Version: 6.2.6`, `Build-Jdk-Spec: 25`, `Created-By: Maven JAR Plugin 3.4.2`.
- JRE embutido em `app/jre/` (`JAVA_VERSION="25"`; módulos java.base, java.desktop, java.sql, java.xml, java.naming, jdk.jfr, entre outros).
- Launcher: `app/simba-validador.exe` — PE32 GUI i386, 73.728 B (tecnologia/função NÃO DETERMINADA; hipótese: wrapper nativo que inicia o JRE).
- Dependências (`app/lib/`, Class-Path do manifesto): spea-client 2.4.4, spring-context/core/aop/beans/expression 6.2.18, hsqldb 2.7.3, gson 2.13.2, guava 33.4.8, httpclient5 5.5.2, httpcore5 5.3.6, logback 1.5.32, slf4j 2.0.17, commons-io/codec/lang3, zip4j 2.6.1, openpdf 3.0.0, openpdf-html 3.0.0, neko-htmlunit 4.14.0, velocity-engine-core 2.4.1, micrometer 1.15.11, jakarta.annotation/inject.
- Pacotes no JAR (contagem de .class): `br/mp/mpf/spea/simba/validador/{rules 46, carga 44, ui 22, enums 22, domain 20, cmd 18, to 4, validacao 2, util 1, desempenho 1}`, `telas` (59), `DAO` (3). Recursos: `certificados/` (AC_Equipamentos_MPF.crt, asspadev.cer, Autoridade_Certificadora_Raiz_Interna_MPF_v2.pem), `templates/`.

## Limitações / pendências
- Duas file entries anômalas (dest `{app}` / vazio, apontando para loc 0): natureza NÃO DETERMINADA; provavelmente efeito da varredura heurística de `inno65_catalog.py` (as 217 entradas bateram com o contador). Não afeta os 216 arquivos verificados por hash.
- Falta confirmar se as classes estão ofuscadas (nomes de pacotes legíveis; nomes de classes ainda não inspecionados).
