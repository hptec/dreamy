-- 为 ES/FR Banner 翻译行增加可选语言专属图片；为空时消费端回退 banner.image_url。
ALTER TABLE banner_translation
  ADD COLUMN IF NOT EXISTS image_url VARCHAR(512) NULL COMMENT '语言专属 Banner 图';
