#!/bin/bash
set -e

BASE_DIR="/mnt/c/Users/75328/OneDrive/桌面/xss"
REMOTE_URL="https://github.com/Redjanji/deploy.git"
TEMP_DIR="/tmp/xss-github-init"

echo "=========================================="
echo "  初始化 GitHub 仓库"
echo "=========================================="
echo "远程仓库: $REMOTE_URL"
echo "目录: $TEMP_DIR"
echo ""

rm -rf "$TEMP_DIR