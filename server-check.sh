#!/bin/bash

echo "=== 检查网关日志 ==="
echo ""
echo "1. 查看网关容器日志（最近100行）"
docker logs xss-gateway --tail 100

echo ""
echo "=== 检查 Nacos 服务注册 ==="
echo ""
echo "2. 查看 Nacos 中的服务列表"
curl -s http://localhost:8848/nacos/v1/ns/service/list?pageNo=1\&pageSize=100 | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8848/nacos/v1/ns/service/list?pageNo=1\&pageSize=100

echo ""
echo "=== 检查网络连通性 ==="
echo ""
echo "3. 从网关容器测试到 auth-service 的连通性"
docker exec xss-gateway curl -s http://auth-service:8083/auth/health 2>&1 || docker exec xss-gateway ping -c 2 auth-service

echo ""
echo "=== 检查服务健康状态 ==="
echo ""
echo "4. 查看所有容器状态"
docker compose ps

echo ""
echo "=== 检查 auth-service 日志 ==="
echo ""
echo "5. 查看 auth-service 日志（最近50行）"
docker logs xss-auth --tail 50