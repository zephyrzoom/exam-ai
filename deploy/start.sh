#!/usr/bin/env bash
# 在部署包根目录执行：DB_PASSWORD=xxx ./start.sh
set -e
cd "$(dirname "$0")"

: "${DB_PASSWORD:?请先设置数据库密码，例如 DB_PASSWORD=xxx ./start.sh}"

nohup java -jar backend/exam-ai-backend.jar > backend.log 2>&1 &
echo "后端已启动（端口 8081），日志：backend.log"