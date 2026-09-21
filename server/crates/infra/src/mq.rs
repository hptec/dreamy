//! 领域事件总线:信封、拓扑描述、发布器、订阅者注册(stub 同步直调 / real RabbitMQ)。
//!
//! 对齐 Java `infra/mq`(BE-DIM-4 / error-strategy 降级矩阵):
//! - 信封 `{event_id, type, occurred_at, payload}`,JSON snake_case,event_id UUID;
//! - 拓扑:topic exchange `dreamy.events`(durable)+ 4 业务队列(durable,DLX=dreamy.dlx)
//!   + 重试队列 `dreamy.retry.{queue}`(per-message TTL 阶梯,到期 default exchange 回投)+ DLX fanout → `dreamy.dlq`;
//! - 队列:q.mail←order.*/showroom.*/refund.*;q.showroom←order.paid;
//!   q.catalog.sales←order.paid(阶梯 1s/4s/16s);q.catalog.rating←review.moderated(同);
//! - 发布:publish 失败不抛(告警日志补偿,本地事务不回滚);publish_or_throw 抛出由 outbox 标记重投;
//! - 消费:manual ack,nack 重试按 x-dreamy-retry 计数 ×3 → basicReject(requeue=false) → DLQ。

use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;

use serde::{Deserialize, Serialize};
use serde_json::Value;
use tokio::sync::RwLock;

/// 领域事件信封(data-flow.md:消费侧按 event_id 幂等)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DomainEvent {
    #[serde(rename = "event_id")]
    pub event_id: String,
    #[serde(rename = "type")]
    pub event_type: String,
    #[serde(rename = "occurred_at")]
    pub occurred_at: String,
    #[serde(default)]
    pub payload: Value,
}

impl DomainEvent {
    pub fn new(event_type: &str, payload: Value) -> Self {
        DomainEvent {
            event_id: uuid::Uuid::new_v4().to_string(),
            event_type: event_type.to_string(),
            occurred_at: chrono::Utc::now().to_rfc3339(),
            payload,
        }
    }
}

/// 队列绑定规格(对齐 Java MqProperties.QueueSpec)
#[derive(Debug, Clone)]
pub struct QueueSpec {
    pub name: &'static str,
    pub binding_keys: &'static [&'static str],
    /// 本队列重试 TTL 阶梯(毫秒);None 取 default
    pub retry_ttl_ms: Option<&'static [u64]>,
    pub prefetch: u16,
}

/// 事件拓扑(对齐 Java MqProperties + RabbitMqTopologyConfig;配置驱动,新增队列零代码改动)
#[derive(Debug, Clone)]
pub struct MqTopology {
    pub exchange: String,
    pub dead_letter_exchange: String,
    pub dead_letter_queue: String,
    pub retry_queue_prefix: String,
    pub max_retries: u32,
    pub default_retry_ttl_ms: Vec<u64>,
    pub queues: Vec<QueueSpec>,
}

impl Default for MqTopology {
    fn default() -> Self {
        MqTopology {
            exchange: "dreamy.events".into(),
            dead_letter_exchange: "dreamy.dlx".into(),
            dead_letter_queue: "dreamy.dlq".into(),
            retry_queue_prefix: "dreamy.retry.".into(),
            max_retries: 3,
            default_retry_ttl_ms: vec![5_000, 30_000, 180_000],
            queues: vec![
                QueueSpec {
                    name: "q.mail",
                    binding_keys: &["order.*", "showroom.*", "refund.*"],
                    retry_ttl_ms: None,
                    prefetch: 8,
                },
                QueueSpec {
                    name: "q.showroom",
                    binding_keys: &["order.paid"],
                    retry_ttl_ms: None,
                    prefetch: 8,
                },
                QueueSpec {
                    name: "q.catalog.sales",
                    binding_keys: &["order.paid"],
                    retry_ttl_ms: Some(&[1_000, 4_000, 16_000]),
                    prefetch: 8,
                },
                QueueSpec {
                    name: "q.catalog.rating",
                    binding_keys: &["review.moderated"],
                    retry_ttl_ms: Some(&[1_000, 4_000, 16_000]),
                    prefetch: 8,
                },
            ],
        }
    }
}

impl MqTopology {
    pub fn retry_queue_name(&self, queue: &str) -> String {
        format!("{}{}", self.retry_queue_prefix, queue)
    }

    /// 取队列重试阶梯;attempt 超出阶梯长度时取末档(对齐 Java retryTtlMs)
    pub fn retry_ttl_ms(&self, queue: &str, attempt: u32) -> u64 {
        let ladder = self
            .queues
            .iter()
            .find(|q| q.name == queue)
            .and_then(|q| q.retry_ttl_ms)
            .map(|s| s.to_vec())
            .filter(|v| !v.is_empty())
            .unwrap_or_else(|| self.default_retry_ttl_ms.clone());
        let idx = (attempt as usize).min(ladder.len() - 1);
        ladder[idx]
    }

    pub fn queue(&self, name: &str) -> Option<&QueueSpec> {
        self.queues.iter().find(|q| q.name == name)
    }
}

/// 进程内订阅者(stub 模式同步直调;real 模式由 AMQP 消费循环回调同一 trait)
pub type SubscriberFn =
    Arc<dyn Fn(&DomainEvent) -> futures::future::BoxFuture<'static, Result<(), String>> + Send + Sync>;

/// 事件总线:stub(进程内直调,按 binding-key 匹配投递)/ real(RabbitMQ)。
/// dev 缺省 stub 不连 broker(对齐 Java bootRun 缺省)。
pub struct EventBus {
    pub mode: MqMode,
    pub topology: MqTopology,
    /// queue_name -> subscribers(stub 模式按队列绑定键匹配事件)
    subscribers: RwLock<HashMap<String, Vec<SubscriberFn>>>,
    amqp: RwLock<Option<Arc<lapin::Connection>>>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MqMode {
    Stub,
    Real,
}

impl EventBus {
    pub fn new(mode: MqMode, topology: MqTopology) -> Self {
        EventBus {
            mode,
            topology,
            subscribers: RwLock::new(HashMap::new()),
            amqp: RwLock::new(None),
        }
    }

    pub fn from_env() -> Self {
        let mode = match std::env::var("MQ_MODE").as_deref() {
            Ok("real") => MqMode::Real,
            _ => MqMode::Stub,
        };
        Self::new(mode, MqTopology::default())
    }

    /// 注册订阅者(queue 名对应 Java DomainEventSubscriber.queue())
    pub async fn subscribe(&self, queue: &str, f: SubscriberFn) {
        self.subscribers
            .write()
            .await
            .entry(queue.to_string())
            .or_default()
            .push(f);
    }

    /// topic binding 匹配(order.* 匹配 order.paid;对齐 AMQP topic 语义)
    fn binding_matches(pattern: &str, routing_key: &str) -> bool {
        let mut p = pattern.split('.');
        let mut r = routing_key.split('.');
        loop {
            match (p.next(), r.next()) {
                (Some("#"), _) => return true,
                (Some("*"), Some(_)) => {}
                (Some("*"), None) => return false,
                (Some(a), Some(b)) if a == b => {}
                (Some(_), Some(_)) => return false,
                (Some(_), None) | (None, Some(_)) => return false,
                (None, None) => return true,
            }
        }
    }

    /// 发布(事件驱动入口)。stub:同步直调匹配队列的进程内订阅者;
    /// real:发 persistent JSON 到 topic exchange。
    ///
    /// 失败不抛(对齐 Java RabbitDomainEventPublisher.publish):记 ERROR 告警,
    /// 本地事务不回滚,缓存新鲜度退化为 TTL 级/触发补偿。
    pub async fn publish(&self, routing_key: &str, payload: Value) -> String {
        let event = DomainEvent::new(routing_key, payload);
        match self.mode {
            MqMode::Stub => {
                let _ = self.deliver_stub(routing_key, &event, true).await;
            }
            MqMode::Real => {
                if let Err(err) = self.publish_amqp(routing_key, &event).await {
                    tracing::error!(
                        "[MQ] publish failed key={} event_id={} (local tx NOT rolled back):{err}",
                        routing_key,
                        event.event_id
                    );
                }
            }
        }
        event.event_id
    }

    /// outbox 投递入口:失败返回 Err,由 outbox relay 标记 retry/DEAD 后重投
    /// (对齐 Java publishOrThrow)
    pub async fn publish_or_throw(
        &self,
        routing_key: &str,
        payload: Value,
        preset_event_id: Option<String>,
    ) -> Result<String, String> {
        let mut event = DomainEvent::new(routing_key, payload);
        if let Some(id) = preset_event_id.filter(|s| !s.trim().is_empty()) {
            event.event_id = id;
        }
        match self.mode {
            MqMode::Stub => self.deliver_stub(routing_key, &event, false).await?,
            MqMode::Real => self
                .publish_amqp(routing_key, &event)
                .await
                .map_err(|e| format!("rabbit publish failed key={routing_key}: {e}"))?,
        }
        Ok(event.event_id)
    }

    /// stub 直调:遍历队列,按 binding 匹配; swallow 参数控制失败是否吞掉
    async fn deliver_stub(
        &self,
        routing_key: &str,
        event: &DomainEvent,
        swallow: bool,
    ) -> Result<(), String> {
        let subs = self.subscribers.read().await;
        let mut matched = 0usize;
        for (queue, fns) in subs.iter() {
            let Some(spec) = self.topology.queue(queue) else {
                continue;
            };
            if !spec
                .binding_keys
                .iter()
                .any(|k| Self::binding_matches(k, routing_key))
            {
                continue;
            }
            for f in fns {
                matched += 1;
                let fut = f(event);
                match fut.await {
                    Ok(()) => {}
                    Err(err) if swallow => {
                        tracing::error!("[MQ-stub] subscriber fail queue={queue} event_id={} :{err}", event.event_id)
                    }
                    Err(err) => return Err(err),
                }
            }
        }
        if matched == 0 {
            tracing::debug!("[MQ-stub] no subscriber for key={routing_key} ( tolerated)");
        }
        Ok(())
    }

    async fn publish_amqp(&self, routing_key: &str, event: &DomainEvent) -> Result<(), String> {
        let conn = self.ensure_amqp().await?;
        let channel = conn
            .create_channel()
            .await
            .map_err(|e| format!("open channel: {e}"))?;
        // 确保拓扑存在(幂等 declare;对齐 Java Declarables)
        self.declare_topology(&channel).await?;
        let body = serde_json::to_vec(event).map_err(|e| e.to_string())?;
        let props = lapin::options::BasicPublishOptions::default();
        let amqp_props = lapin::BasicProperties::default()
            .with_message_id(event.event_id.clone().into())
            .with_content_type("application/json".into())
            .with_delivery_mode(2); // persistent
        channel
            .basic_publish(
                self.topology.exchange.as_str(),
                routing_key,
                lapin::options::BasicPublishOptions::default(),
                body.as_slice(),
                amqp_props,
            )
            .await
            .map_err(|e| format!("publish: {e}"))?
            .await
            .map_err(|e| format!("publish confirm: {e}"))?;
        tracing::info!("[MQ] publish key={routing_key} event_id={}", event.event_id);
        let _ = props;
        Ok(())
    }

    async fn ensure_amqp(&self) -> Result<Arc<lapin::Connection>, String> {
        {
            let guard = self.amqp.read().await;
            if let Some(conn) = guard.as_ref() {
                if conn.status().connected() {
                    return Ok(conn.clone()); // Arc<Connection>
                }
            }
        }
        let url = std::env::var("RABBITMQ_URL")
            .unwrap_or_else(|_| "amqp://guest:guest@localhost:5672/%2f".into());
        let conn = lapin::Connection::connect(&url, lapin::ConnectionProperties::default())
            .await
            .map_err(|e| format!("connect {url}: {e}"))?;
        let conn = Arc::new(conn);
        *self.amqp.write().await = Some(conn.clone());
        Ok(conn)
    }

    async fn declare_topology(&self, channel: &lapin::Channel) -> Result<(), String> {
        use lapin::options::*;
        use lapin::types::FieldTable;
        let t = &self.topology;
        channel
            .exchange_declare(
                t.exchange.as_str(),
                lapin::ExchangeKind::Topic,
                ExchangeDeclareOptions {
                    durable: true,
                    ..Default::default()
                },
                FieldTable::default(),
            )
            .await
            .map_err(|e| e.to_string())?;
        channel
            .exchange_declare(
                t.dead_letter_exchange.as_str(),
                lapin::ExchangeKind::Fanout,
                ExchangeDeclareOptions {
                    durable: true,
                    ..Default::default()
                },
                FieldTable::default(),
            )
            .await
            .map_err(|e| e.to_string())?;
        channel
            .queue_declare(
                t.dead_letter_queue.as_str(),
                QueueDeclareOptions {
                    durable: true,
                    ..Default::default()
                },
                FieldTable::default(),
            )
            .await
            .map_err(|e| e.to_string())?;
        channel
            .queue_bind(
                t.dead_letter_queue.as_str(),
                t.dead_letter_exchange.as_str(),
                "",
                QueueBindOptions::default(),
                FieldTable::default(),
            )
            .await
            .map_err(|e| e.to_string())?;
        for q in &t.queues {
            let mut args = FieldTable::default();
            args.insert(
                "x-dead-letter-exchange".into(),
                lapin::types::AMQPValue::LongString(t.dead_letter_exchange.as_str().into()),
            );
            channel
                .queue_declare(
                    q.name,
                    QueueDeclareOptions {
                        durable: true,
                        ..Default::default()
                    },
                    args.clone(),
                )
                .await
                .map_err(|e| e.to_string())?;
            for key in q.binding_keys {
                channel
                    .queue_bind(
                        q.name,
                        t.exchange.as_str(),
                        key,
                        QueueBindOptions::default(),
                        FieldTable::default(),
                    )
                    .await
                    .map_err(|e| e.to_string())?;
            }
            // 重试队列:per-message TTL,到期经 default exchange 回投主队列
            let mut retry_args = FieldTable::default();
            retry_args.insert(
                "x-dead-letter-exchange".into(),
                lapin::types::AMQPValue::LongString("".into()),
            );
            retry_args.insert(
                "x-dead-letter-routing-key".into(),
                lapin::types::AMQPValue::LongString(q.name.into()),
            );
            channel
                .queue_declare(
                    t.retry_queue_name(q.name).as_str(),
                    QueueDeclareOptions {
                        durable: true,
                        ..Default::default()
                    },
                    retry_args,
                )
                .await
                .map_err(|e| e.to_string())?;
        }
        Ok(())
    }

    /// real 模式消费循环:每队列一个 task,manual ack + 重试阶梯 + DLQ。
    /// 启动即阻塞(由调用方 spawn);stub 模式直接返回。
    pub async fn run_consumers(&self) {
        if self.mode != MqMode::Real {
            return;
        }
        let conn = match self.ensure_amqp().await {
            Ok(c) => c,
            Err(err) => {
                tracing::error!("[MQ] consumer connect failed:{err}");
                return;
            }
        };
        let channel = match conn.create_channel().await {
            Ok(c) => c,
            Err(err) => {
                tracing::error!("[MQ] consumer channel failed:{err}");
                return;
            }
        };
        if let Err(err) = self.declare_topology(&channel).await {
            tracing::error!("[MQ] topology declare failed:{err}");
            return;
        }
        for q in &self.topology.queues {
            let ch = channel.clone();
            let topology = self.topology.clone();
            let queue_name = q.name.to_string();
            let prefetch = q.prefetch;
            let subscribers = self.subscribers.read().await.get(q.name).cloned();
            let Some(subs) = subscribers else {
                continue;
            };
            tokio::spawn(async move {
                use futures::StreamExt;
                use lapin::options::*;
                use lapin::types::FieldTable;
                let _ = ch
                    .basic_qos(prefetch, BasicQosOptions::default())
                    .await;
                let consumer_res = ch
                    .basic_consume(
                        queue_name.as_str(),
                        format!("rust-{}", queue_name).as_str(),
                        BasicConsumeOptions::default(),
                        FieldTable::default(),
                    )
                    .await;
                let mut consumer = match consumer_res {
                    Ok(c) => c,
                    Err(err) => {
                        tracing::error!("[MQ] consume {queue_name} failed:{err}");
                        return;
                    }
                };
                while let Some(delivery) = consumer.next().await {
                    let Ok(delivery) = delivery else { continue };
                    let event: Option<DomainEvent> =
                        serde_json::from_slice(&delivery.data).ok();
                    let Some(event) = event else {
                        // 不可解析信封:直接死信(不重试)
                        let _ = delivery
                            .nack(lapin::options::BasicNackOptions {
                                requeue: false,
                                ..Default::default()
                            })
                            .await;
                        continue;
                    };
                    let attempt = delivery
                        .properties
                        .headers()
                        .as_ref()
                        .and_then(|h| h.inner().get("x-dreamy-retry"))
                        .and_then(|v| match v {
                            lapin::types::AMQPValue::LongLongInt(i) => Some(*i),
                            lapin::types::AMQPValue::ShortShortInt(i) => Some(*i as i64),
                            lapin::types::AMQPValue::ShortInt(i) => Some(*i as i64),
                            lapin::types::AMQPValue::LongInt(i) => Some(*i as i64),
                            _ => None,
                        })
                        .unwrap_or(0i64) as u32;
                    let mut result = Ok(());
                    for f in &subs {
                        if let Err(err) = f(&event).await {
                            result = Err(err);
                            break;
                        }
                    }
                    match result {
                        Ok(()) => {
                            let _ = delivery.ack(lapin::options::BasicAckOptions::default()).await;
                        }
                        Err(err) if attempt < topology.max_retries => {
                            // 改投 retry 队列(per-message TTL),ack 原消息
                            let mut retry_event = event.clone();
                            let _ = &mut retry_event;
                            let body = serde_json::to_vec(&event).unwrap_or_default();
                            let mut props = lapin::BasicProperties::default()
                                .with_message_id(event.event_id.clone().into())
                                .with_content_type("application/json".into())
                                .with_delivery_mode(2);
                            let mut headers = lapin::types::FieldTable::default();
                            headers.insert(
                                "x-dreamy-retry".into(),
                                ((attempt + 1) as i64).into(),
                            );
                            props = props.with_headers(headers);
                            let ttl = topology.retry_ttl_ms(&queue_name, attempt);
                            let _ = ch
                                .basic_publish(
                                    "",
                                    topology.retry_queue_name(&queue_name).as_str(),
                                    BasicPublishOptions::default(),
                                    body.as_slice(),
                                    props.with_expiration((ttl as u64).to_string().into()),
                                )
                                .await;
                            let _ = delivery.ack(lapin::options::BasicAckOptions::default()).await;
                            tracing::warn!(
                                "[MQ] retry {}/{} queue={} event_id={} :{}",
                                attempt + 1,
                                topology.max_retries,
                                queue_name,
                                event.event_id,
                                err
                            );
                        }
                        Err(err) => {
                            // 超限:reject → DLX → DLQ(告警 + 人工重放)
                            let _ = delivery
                                .nack(lapin::options::BasicNackOptions {
                                    requeue: false,
                                    ..Default::default()
                                })
                                .await;
                            tracing::error!(
                                "[MQ] DEAD queue={} event_id={} (max retries):{err}",
                                queue_name,
                                event.event_id
                            );
                        }
                    }
                }
            });
        }
        tracing::info!("[MQ] consumers started ({} queues)", self.topology.queues.len());
        let _ = Duration::ZERO;
    }
}

/// topic binding 匹配单测锚点(与 AMQP 语义一致)
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn topic_binding_matches() {
        assert!(EventBus::binding_matches("order.*", "order.paid"));
        assert!(EventBus::binding_matches("order.*", "order.refund.a") == false);
        assert!(EventBus::binding_matches("review.moderated", "review.moderated"));
        assert!(!EventBus::binding_matches("order.paid", "order.refund"));
        assert!(EventBus::binding_matches("#", "a.b.c"));
        assert!(EventBus::binding_matches("a.#", "a.b.c"));
    }

    #[test]
    fn retry_ladder_default_and_override() {
        let t = MqTopology::default();
        assert_eq!(t.retry_ttl_ms("q.mail", 0), 5_000);
        assert_eq!(t.retry_ttl_ms("q.mail", 2), 180_000);
        assert_eq!(t.retry_ttl_ms("q.mail", 7), 180_000, "超阶梯取末档");
        assert_eq!(t.retry_ttl_ms("q.catalog.sales", 1), 4_000, "catalog 专用阶梯");
    }

    #[tokio::test]
    async fn stub_pubsub_direct_call() {
        let bus = EventBus::new(MqMode::Stub, MqTopology::default());
        let hit = Arc::new(std::sync::atomic::AtomicUsize::new(0));
        let h2 = hit.clone();
        bus.subscribe("q.showroom", Arc::new(move |ev| {
            let h = h2.clone();
            let ty = ev.event_type.clone();
            Box::pin(async move {
                assert_eq!(ty, "order.paid");
                h.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
                Ok(())
            })
        }))
        .await;
        // 不匹配的 key 不投递
        bus.publish("review.moderated", serde_json::json!({})).await;
        bus.publish("order.paid", serde_json::json!({"order_id": 1})).await;
        assert_eq!(hit.load(std::sync::atomic::Ordering::SeqCst), 1);
    }
}
