#!/usr/bin/env bash
# 打包工作空间源码为 zip：./scripts/package.sh [输出目录]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="${1:-$ROOT/release}"
mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
ZIP="$OUT_DIR/exam-ai-$(date +%Y%m%d-%H%M%S).zip"

cd "$ROOT"
zip -rq "$ZIP" . \
  -x '.git/*' \
     'poc/*' \
     'node_modules/*' '*/node_modules/*' \
     'target/*' '*/target/*' \
     'dist/*' '*/dist/*' \
     'release/*' \
     '.idea/*' '*/.idea/*' '.vscode/*' '*/.vscode/*' '*.iml' \
     '.DS_Store' '*/.DS_Store' \
     '*.log' '*.class' '*.jar' '*.war' \
     '.env' '*/.env' '*.local' \
     '*/application-local.yml'

echo "打包完成：$ZIP"
echo "大小：$(du -h "$ZIP" | cut -f1)  文件数：$(unzip -l "$ZIP" | tail -1 | awk '{print $2}')"