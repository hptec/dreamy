#!/bin/bash

# 缓存清除功能快速验证脚本

echo "=== 缓存清除功能验证 ==="
echo ""

# 1. 检查后端服务
echo "1. 检查后端服务..."
curl -s http://localhost:8080/actuator/health | jq .
echo ""

# 2. 检查数据库表
echo "2. 检查 cache_invalidation_log 表..."
docker exec pd-mysql mysql -uroot -proot identity -e "DESC cache_invalidation_log;" 2>/dev/null
echo ""

# 3. 检查前端文件
echo "3. 检查前端文件..."
if [ -f "frontend/portal-admin/src/api/cache.ts" ]; then
    echo "✅ cache.ts 存在"
else
    echo "❌ cache.ts 不存在"
fi

if grep -q "保存并清除缓存" frontend/portal-admin/src/views/ProductEdit.vue; then
    echo "✅ ProductEdit.vue 已更新"
else
    echo "❌ ProductEdit.vue 未更新"
fi
echo ""

# 4. 检查后端文件
echo "4. 检查后端文件..."
if [ -f "backend/src/main/java/com/dreamy/infra/CdnInvalidationService.java" ]; then
    echo "✅ CdnInvalidationService.java 存在"
else
    echo "❌ CdnInvalidationService.java 不存在"
fi

if [ -f "backend/src/main/java/com/dreamy/config/AsyncConfig.java" ]; then
    echo "✅ AsyncConfig.java 存在"
else
    echo "❌ AsyncConfig.java 不存在"
fi
echo ""

# 5. 检查配置
echo "5. 检查配置..."
if grep -q "cdn:" backend/src/main/resources/application.yml; then
    echo "✅ application.yml CDN 配置存在"
else
    echo "❌ application.yml CDN 配置不存在"
fi
echo ""

echo "=== 验证完成 ==="
echo ""
echo "📋 文档位置："
echo "  - docs/cache-invalidation-monitor-implementation.md (方案 B 文档)"
echo "  - docs/cdn-cache-invalidation-final.md (最终实施总结)"
echo ""
echo "🚀 下一步："
echo "  1. 重启后端服务：cd backend && ./gradlew bootRun"
echo "  2. 访问前端：http://localhost:5174"
echo "  3. 编辑商品并保存，查看 CDN 清除日志"
echo "  4. 访问发布中心：http://localhost:5174/publish"
