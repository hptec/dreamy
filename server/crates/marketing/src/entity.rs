//! marketing 底座域 SeaORM 实体(newsletter_subscriber / contact_message)。
//! SeaORM 约定:每个实体独立 module,主结构名必须为 Model。
//! IntEnum 口径:source 1=页脚 2=弹窗 3=退出挽留 4=首页区块;status 1=已订阅 2=已退订。

pub mod newsletter_subscriber {
    use sea_orm::entity::prelude::*;

    #[derive(Debug, Clone, PartialEq, Eq, EnumIter, DeriveActiveEnum)]
    #[sea_orm(rs_type = "i8", db_type = "TinyInteger")]
    pub enum NewsletterSource {
        #[sea_orm(num_value = 1)]
        Footer,
        #[sea_orm(num_value = 2)]
        Modal,
        #[sea_orm(num_value = 3)]
        ExitIntent,
        #[sea_orm(num_value = 4)]
        HomeBlock,
    }

    impl NewsletterSource {
        pub fn from_key(v: i64) -> Option<Self> {
            match v {
                1 => Some(Self::Footer),
                2 => Some(Self::Modal),
                3 => Some(Self::ExitIntent),
                4 => Some(Self::HomeBlock),
                _ => None,
            }
        }
    }

    #[derive(Debug, Clone, PartialEq, Eq, EnumIter, DeriveActiveEnum)]
    #[sea_orm(rs_type = "i8", db_type = "TinyInteger")]
    pub enum SubscriberStatus {
        #[sea_orm(num_value = 1)]
        Subscribed,
        #[sea_orm(num_value = 2)]
        Unsubscribed,
    }

    #[derive(Clone, Debug, PartialEq, DeriveEntityModel)]
    #[sea_orm(table_name = "newsletter_subscriber")]
    pub struct Model {
        #[sea_orm(primary_key)]
        pub id: u64,
        /// 小写归一,唯一(幂等判重首写胜出)
        pub email: String,
        pub source: NewsletterSource,
        /// en|es|fr
        pub locale: String,
        pub status: SubscriberStatus,
        /// 订阅时间(退订 token 代际锚点)
        pub subscribed_at: DateTime,
        /// 退订时间(重复退订保留首次时间)
        pub unsubscribed_at: Option<DateTime>,
        pub created_at: DateTime,
        pub updated_at: DateTime,
    }

    #[derive(Copy, Clone, Debug, EnumIter, DeriveRelation)]
    pub enum Relation {}

    impl ActiveModelBehavior for ActiveModel {}
}

pub mod contact_message {
    use sea_orm::entity::prelude::*;

    /// contact_message(联系表单;管理端本期不做查看页)
    #[derive(Clone, Debug, PartialEq, DeriveEntityModel)]
    #[sea_orm(table_name = "contact_message")]
    pub struct Model {
        #[sea_orm(primary_key)]
        pub id: u64,
        pub name: String,
        pub email: String,
        pub subject: Option<String>,
        pub message: String,
        pub submitted_at: DateTime,
        pub created_at: DateTime,
        pub updated_at: DateTime,
    }

    #[derive(Copy, Clone, Debug, EnumIter, DeriveRelation)]
    pub enum Relation {}

    impl ActiveModelBehavior for ActiveModel {}
}
