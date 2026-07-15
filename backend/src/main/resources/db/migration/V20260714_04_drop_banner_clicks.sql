-- 移除未接入埋点写入链路的 Banner 点击统计字段。
ALTER TABLE banner DROP COLUMN clicks;
