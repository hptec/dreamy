# fabric_composition 迁移至 EAV 系统

## 已完成的工作

### 1. 后端代码改动

#### 删除固定字段
- ✅ `Product.java` - 除 fabricComposition 字段及注解
- ✅ `ProductDBConst.java` - 删除 FABRIC_COMPOSITION 常量
- ✅ `AdminProductUpsert.java` - 删除 DTO 字段
- ✅ `AdminProductDetail.java` - 删除 DTO 字段
- ✅ `StoreProductDetail.java` - 删除 DTO 字段

#### 删除服务层引用
- ✅ `AdminProductService.java` - 删除 setFabricComposition 调用和 DTO 装配
- ✅ `StoreProductService.java` - 删除 DTO 装配中的 getFabricComposition
- ✅ `ProductUpsertValidator.java` - 删除长度校验

#### 添加 EAV 属性定义
- ✅ `CatalogSeedInitializer.java` - 新增 fabric_composition 属性定义（TEXT 类型）
- ✅ 在三个属性集中添加：
  - Bridal Gown Attributes (OPTIONAL)
  - Occasion Dress Attributes (OPTIONAL)
  - Accessory Attributes (OPTIONAL)

### 2. 前端代码改动

#### 类型定义
- ✅ `frontend/portal-admin/src/api/types.ts` - 删除 fabricComposition 字段
- ✅ `frontend/portal-store/lib/api/store-types.ts` - 删除 fabricComposition 字段

#### UI 组件
- ✅ `frontend/portal-admin/src/views/ProductEdit.vue` - 删除表单字段和赋值逻辑
- ✅ `frontend/portal-store/components/product/product-buy-box.tsx` - 删除渲染逻辑

### 3. 数据库迁移脚本
- ✅ `scripts/sql/drop-fabric-composition-column.sql` - 创建删除列的脚本

## 后续步骤

### 步骤 1：清库重启

```bash
# 1. 停止服务
docker-compose down

# 2. 删除数据库数据
docker volume rm dreamy_mysql_data  # 或手动删除

# 3. 重新启动（会触发种子数据初始化）
docker-compose up -d
cd /Volumes/MAC/workspace/dreamy
./gradlew bootRun

# 4. 启动前端
cd frontend/portal-admin && pnpm dev
cd frontend/portal-store && pnpm dev
```

### 步骤 2：验证迁移

1. **后台验证：**
   - 访问属性字典页面，确认 `fabric_composition` 属性存在
   - 编辑商品，在动态属性区域看到 "Fabric Composition" 文本框
   - 保存商品，确认数据存入 `product_attribute_value` 表

2. **消费端验证：**
   - 访问商品详情页
   - 在 "Fabric & Care" 区域看到动态属性渲染的面料成分

3. **数据库验证：**
   ```sql
   -- 确认属性定义存在
   SELECT * FROM attribute_def WHERE key = 'fabric_composition';
   
   -- 确认商品属性值存储
   SELECT * FROM product_attribute_value WHERE attribute_id = (SELECT id FROM attribute_def WHERE key = 'fabric_composition');
   ```

## 架构优势

### 迁移前（混合模式）
```
商品版型属性
├── 动态属性（EAV）: silhouette, fabric, neckline, sleeve, length...
└── 固定列: fabric_composition ❌ 唯一例外
```

### 迁移后（统一 EAV）
```
商品版型属性
└── 动态属性（EAV）: 全部统一，包括 fabric_composition ✅
```

### 优势：
1. ✅ **架构统一**：所有版型属性都在 EAV 系统，消除特殊情况
2. ✅ **扩展性**：后台可随时新增/编辑属性，无需改表结构
3. ✅ **多语言**：自动支持三语（EN/ES/FR）
4. ✅ **可配置**：可在属性集中控制每个品类是否显示此属性

## 注意事项

⚠️ **重要**：目前有一个无关的编译错误需要修复：
- `AdminCacheService.java:50` - Paginated 构造函数参数不匹配
- 这个错误与 fabric_composition 迁移无关，需要单独处理

