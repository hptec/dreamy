//! trading 底座实体:address / wishlist_item / browse_history(第一批交易侧表)。
//! DDL 对齐 identity 库真实结构(huihao 生成,已核实 SHOW CREATE TABLE)。

pub mod address {
    use sea_orm::entity::prelude::*;

    #[derive(Clone, Debug, PartialEq, DeriveEntityModel)]
    #[sea_orm(table_name = "address")]
    pub struct Model {
        #[sea_orm(primary_key)]
        pub id: u64,
        pub customer_id: i64,
        /// 收件人
        pub receiver: String,
        pub phone: Option<String>,
        /// 街道地址
        pub line: String,
        pub city: String,
        pub state: Option<String>,
        pub zip: String,
        /// 运费分区映射输入
        pub country: String,
        /// ISO-3166-1 alpha-2
        pub country_code: Option<String>,
        /// ISO-3166-2 后缀(仅 US/CA/AU 规范化)
        pub region_code: Option<String>,
        /// 恒至多一个默认(TX-TRD-008)
        pub is_default: bool,
        pub created_at: DateTime,
        pub updated_at: DateTime,
    }

    #[derive(Copy, Clone, Debug, EnumIter, DeriveRelation)]
    pub enum Relation {}

    impl ActiveModelBehavior for ActiveModel {}
}

pub mod wishlist_item {
    use sea_orm::entity::prelude::*;

    #[derive(Clone, Debug, PartialEq, DeriveEntityModel)]
    #[sea_orm(table_name = "wishlist_item")]
    pub struct Model {
        #[sea_orm(primary_key)]
        pub id: u64,
        pub customer_id: i64,
        pub product_id: i64,
        pub created_at: DateTime,
        pub updated_at: DateTime,
    }

    #[derive(Copy, Clone, Debug, EnumIter, DeriveRelation)]
    pub enum Relation {}

    impl ActiveModelBehavior for ActiveModel {}
}

pub mod browse_history {
    use sea_orm::entity::prelude::*;

    #[derive(Clone, Debug, PartialEq, DeriveEntityModel)]
    #[sea_orm(table_name = "browse_history")]
    pub struct Model {
        #[sea_orm(primary_key)]
        pub id: u64,
        pub customer_id: i64,
        pub product_id: i64,
        /// upsert 刷新;每用户滚动保留 50 条
        pub viewed_at: DateTime,
        pub created_at: DateTime,
        pub updated_at: DateTime,
    }

    #[derive(Copy, Clone, Debug, EnumIter, DeriveRelation)]
    pub enum Relation {}

    impl ActiveModelBehavior for ActiveModel {}
}
