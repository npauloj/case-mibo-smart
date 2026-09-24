#!/usr/bin/env bash
# Gera docs/PRODUCT.pdf a partir de docs/PRODUCT.md.
#
# Passa por HTML de propósito: `pandoc -o x.pdf` exigiria um motor TeX, que não é dependência deste
# projeto e não está instalado. pandoc e Chrome são ferramenta de máquina, não do build — nenhuma
# dependência do Gradle muda por causa disto.
#
# Uso:  bash tools/product-pdf.sh           -> docs/PRODUCT.pdf (documento, A4)
#       bash tools/product-pdf.sh --slides  -> docs/PRESENTATION.pdf (slides, 16:9)
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mode="doc"; [ "${1:-}" = "--slides" ] && mode="slides"
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

docs_dir="$(cd "$root/docs" && pwd -W 2>/dev/null || pwd)"

if [ "$mode" = "slides" ]; then
  # Os slides já são HTML: o layout 16:9 é do arquivo, não do markdown, então não passam pelo
  # pandoc. Só a impressão é compartilhada com o documento.
  "$chrome" --headless --disable-gpu --no-pdf-header-footer     --print-to-pdf="$docs_dir/PRESENTATION.pdf" "file:///$docs_dir/presentation.html" >/dev/null 2>&1
  echo "docs/PRESENTATION.pdf gerado. Abra e confira — não há verificação automática de aparência."
  exit 0
fi

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
