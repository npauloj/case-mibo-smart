#!/usr/bin/env bash
# Gera docs/PRODUCT.pdf a partir de docs/PRODUCT.md.
#
# Passa por HTML de propósito: `pandoc -o x.pdf` exigiria um motor TeX, que não é dependência deste
# projeto e não está instalado. pandoc e Chrome são ferramenta de máquina, não do build — nenhuma
# dependência do Gradle muda por causa disto.
#
# Uso:  bash tools/product-pdf.sh
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

chrome="${CHROME:-}"
if [ -z "$chrome" ]; then
  for c in "/c/Program Files/Google/Chrome/Application/chrome.exe" \
           "/c/Program Files (x86)/Google/Chrome/Application/chrome.exe" \
           "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
           "$(command -v google-chrome || true)" "$(command -v chromium || true)"; do
    [ -x "$c" ] && chrome="$c" && break
  done
fi
[ -n "$chrome" ] || { echo "Chrome não encontrado; defina CHROME=<caminho>" >&2; exit 1; }

# A primeira linha do PRODUCT.md é o H1 do documento, e o bloco de título do pandoc o substitui —
# sem isto o título sai duas vezes na primeira página.
head -1 "$root/docs/PRODUCT.md" | grep -q '^# ' \
  || { echo "esperava um H1 na primeira linha de docs/PRODUCT.md" >&2; exit 1; }
tail -n +2 "$root/docs/PRODUCT.md" > "$tmp/body.md"

pandoc "$tmp/body.md" -s --embed-resources --css "$root/tools/product-pdf.css" \
  --metadata title="Case Mibo Smart" \
  --metadata subtitle="Documento de produto e arquitetura" \
  -o "$tmp/product.html"

url="file:///$(cd "$tmp" && pwd -W 2>/dev/null || pwd)/product.html"
"$chrome" --headless --disable-gpu --no-pdf-header-footer \
  --print-to-pdf="$(cd "$root/docs" && pwd -W 2>/dev/null || pwd)/PRODUCT.pdf" "$url" >/dev/null 2>&1

echo "docs/PRODUCT.pdf gerado. Abra e confira — não há verificação automática de aparência."
