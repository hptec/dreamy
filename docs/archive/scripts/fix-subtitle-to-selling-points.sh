#!/bin/bash
# 批量替换 subtitle 为 sellingPoints 的脚本

echo "开始批量替换 subtitle → sellingPoints..."

# 1. 替换 .setSubtitle 和 .getSubtitle
find src/main/java src/test/java -name "*.java" -type f -exec sed -i '' \
  -e 's/\.setSubtitle(/\.setSellingPoints(/g' \
  -e 's/\.getSubtitle()/\.getSellingPoints()/g' \
  {} +

# 2. 替换 record 参数中的 subtitle
find src/main/java src/test/java -name "*.java" -type f -exec sed -i '' \
  -e 's/String subtitle,/List<String> sellingPoints,/g' \
  -e 's/String subtitle)/List<String> sellingPoints)/g' \
  {} +

# 3. 替换 XML mapper 中的 subtitle
find src/main/resources -name "*Mapper.xml" -type f -exec sed -i '' \
  -e 's/subtitle,/selling_points,/g' \
  -e 's/#{subtitle}/#{sellingPoints,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler}/g' \
  {} +

echo "批量替换完成！"
echo "请手动检查以下文件："
echo "1. AdminProductService.java - 检查 toEntity 方法"
echo "2. StoreProductService.java - 检查 assembleDetail 方法"
echo "3. TradingPortConfig.java - 检查 ProductBrief 构造"
echo "4. CatalogSeedInitializer.java - 删除 subtitle 种子数据"
