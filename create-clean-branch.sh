#!/bin/bash
set -e

BASE_DIR="/mnt/c/Users/75328/OneDrive/桌面/xss"
cd "$BASE_DIR"

echo "=========================================="
echo "  创建干净的部署分支"
echo "=========================================="

echo ""
echo "1. 创建临时目录用于新分支..."
TEMP_DIR="/tmp/xss-deploy-clean"
rm -rf "$TEMP_DIR"
mkdir -p "$TEMP_DIR"

echo ""
echo "2. 初始化新的git仓库..."
cd "$TEMP_DIR"
git init
git config user.email "redjanji@example.com"
git config user.name "redjanji"
git branch -m main

echo ""
echo "3. 复制部署文件..."
cp -r "${BASE_DIR}/one-click-deployment/deploy/." .

echo ""
echo "4. 创建.gitignore..."
cat > .gitignore << 'EOF'
*.tar.gz
*.tar
EOF

echo ""
echo "5. 添加所有文件..."
git add .

echo ""
echo "6. 提交..."
git commit -m "部署文件 - 11个业务服务镜像 (20260719)"

echo ""
echo "7. 添加远程仓库..."
git remote add origin "https://gitee.com/redjanji_admin/deploy.git"

echo ""
echo "8. 强制推送覆盖远程main分支..."
git push -f origin main

echo ""
echo "=========================================="
echo "  完成！远程仓库已清理"
echo "=========================================="

echo ""
echo "本地仓库文件列表:"
ls -lh
echo ""
echo "目录大小:"
du -sh .
