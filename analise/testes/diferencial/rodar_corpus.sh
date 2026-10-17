#!/bin/bash
# Executa o nível 2 (original, sandbox sem rede) sobre um corpus e guarda, por cenário, a saída do original e da reimplementação (COMPAT)
# e as regras disparadas. Uso: OUT=<dir> CORPORA=<dir> rodar_corpus.sh <nome_do_corpus>
# Pré-requisitos: implementacao/validador-independente/target/classes (mvn package) e MapaRegras compilado em $OUT/cls:
#   javac -d $OUT/cls -cp implementacao/validador-independente/target/classes analise/testes/diferencial/MapaRegras.java
set -e
R="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$R/analise/testes/diferencial"
python3 diferencial_nivel2.py "$CORPORA/$1" --mostrar 3 > "$OUT/$1.resumo.txt" 2>&1 || true
java -cp "$OUT/cls:$R/implementacao/validador-independente/target/classes" estudo.simba.validador.MapaRegras /tmp/n2work/casos > "$OUT/$1.regras.tsv"
mkdir -p "$OUT/$1.saidas"
for d in /tmp/n2work/casos/*/; do n=$(basename "$d"); cp "$d/saida-original.txt" "$OUT/$1.saidas/$n.orig"; cp "$d/saida-compat.txt" "$OUT/$1.saidas/$n.compat"; done
