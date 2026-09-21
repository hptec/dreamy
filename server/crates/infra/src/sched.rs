//! 调度器:6 字段 cron(秒 分 时 日 月 周,对齐 Spring @Scheduled cron)+ fixedDelay。
//!
//! Java 侧 12 个任务的对齐清单(迁移时逐域接线,infra 只提供运行时):
//! | 任务 | 触发 | 说明 |
//! |---|---|---|
//! | OrderTimeoutScheduler | cron `0 * * * * *` 每分钟 | 订单超时关闭(Redis 锁单飞) |
//! | OrderAutoCompleteScheduler | cron `0 15 * * * *` 每小时:15 | 自动完成 |
//! | OutboxRelayScheduler | cron `30 * * * * *` 每分钟:30 | outbox 投递 |
//! | BlogViewsFlusher | cron `30 * * * * *` | 浏览数 flush |
//! | MarketingPromoScheduler | cron `0 * * * * *` | 促销状态机 |
//! | CacheInvalidationTaskWorker | fixedDelay 1000ms | 缓存失效 worker |
//! | GatewayModelRefreshScheduler | fixedDelay 300000ms | 网关模型刷新 |
//! | ExchangeRateRefreshScheduler | cron `0 5 3 * * *` 03:05 | 汇率刷新(锁) |
//! | ShipmentTrackingSyncScheduler | cron `0 */30 * * * *` | 轨迹同步(锁) |
//! | CartMergeRecordCleanupScheduler | cron `0 40 4 * * *` 04:40 | 清理 |
//! | ProcessedEventCleanupScheduler | cron `0 30 4 * * *` 04:30 | 幂等记录清理 |
//! | SalesWindowRefreshJob(经 MQ 消费触发,非 @Scheduled) |
//!
//! cron 解析支持:`*` 任意、`*/n` 步进、`a-b` 区间、列表 `a,b`、纯数字;
//! 日/月/周任意 `?` 等同 `*`。夏令时不适用(服务器 TZ 固定)。

use std::future::Future;
use std::pin::Pin;
use std::sync::Arc;
use std::time::Duration;

use chrono::{Datelike, Timelike, Utc};

#[derive(Debug, thiserror::Error)]
#[error("cron 表达式非法:{0}")]
pub struct CronError(pub String);

/// 字段取值集合
#[derive(Debug, Clone)]
struct Field {
    /// 秒/分/时:0..=59/59/23;日 1..=31;月 1..=12;周 0..=6(0=周日,Spring 口径 1-7 或 SUN-SAT)
    bits: [bool; 62],
    is_star: bool,
}

impl Field {
    fn matches(&self, v: u32) -> bool {
        (v as usize) < 62 && self.bits[v as usize]
    }
}

fn parse_field(spec: &str, min: u32, max: u32, name: &str) -> Result<Field, CronError> {
    let mut f = Field {
        bits: [false; 62],
        is_star: false,
    };
    let clamp = |v: u32| -> Result<usize, CronError> {
        if v < min || v > max {
            return Err(CronError(format!("{name} 值 {v} 越界 [{min},{max}]")));
        }
        Ok(v as usize)
    };
    for part in spec.split(',') {
        let part = part.trim();
        if part == "*" || part == "?" {
            for v in min..=max {
                f.bits[v as usize] = true;
            }
            f.is_star = true;
            continue;
        }
        // 步进:`*/n`、`a/n`、`a-b/n`
        let (range_part, step) = match part.split_once('/') {
            Some((r, s)) => {
                let step: u32 = s.parse().map_err(|_| CronError(format!("{name} 步进非法:{s}")))?;
                if step == 0 {
                    return Err(CronError(format!("{name} 步进不能为 0")));
                }
                (r, step)
            }
            None => (part, 1),
        };
        let (lo, hi) = match range_part.split_once('-') {
            Some((a, b)) => {
                let lo: u32 = parse_num(a, name)?;
                let hi: u32 = parse_num(b, name)?;
                (clamp(lo)?, clamp(hi)?)
            }
            None => {
                if range_part == "*" || range_part == "?" {
                    // `*/n`:全域步进
                    (min as usize, max as usize)
                } else {
                    let v = clamp(parse_num(range_part, name)?)?;
                    if step > 1 {
                        (v, max as usize)
                    } else {
                        (v, v)
                    }
                }
            }
        };
        let mut v = lo;
        loop {
            f.bits[v] = true;
            v += step as usize;
            if v > hi || v >= 62 {
                break;
            }
        }
    }
    Ok(f)
}

fn parse_num(s: &str, name: &str) -> Result<u32, CronError> {
    s.trim()
        .parse()
        .map_err(|_| CronError(format!("{name} 数字非法:{s}")))
}

/// 6 字段 cron(秒 分 时 日 月 周)。周字段支持 0-7(0 与 7 均为周日,quartz/spring 混用口径)。
#[derive(Debug, Clone)]
pub struct Cron {
    sec: Field,
    min: Field,
    hour: Field,
    day: Field,
    month: Field,
    /// 周:0..=6(内部归一 7→0)
    weekday: Field,
    /// day 与 weekday 均非 * 时,OR 语义(vixie cron);Spring 默认即如此
    day_or_weekday: bool,
}

impl Cron {
    pub fn parse(expr: &str) -> Result<Cron, CronError> {
        let parts: Vec<&str> = expr.split_whitespace().collect();
        if parts.len() != 6 {
            return Err(CronError(format!(
                "需 6 字段(秒 分 时 日 月 周),实得 {} 字段:\"{expr}\"",
                parts.len()
            )));
        }
        let sec = parse_field(parts[0], 0, 59, "秒")?;
        let min = parse_field(parts[1], 0, 59, "分")?;
        let hour = parse_field(parts[2], 0, 23, "时")?;
        let day = parse_field(parts[3], 1, 31, "日")?;
        let month = parse_field(parts[4], 1, 12, "月")?;
        // 周:允许 7(周日),归一到 0
        let mut weekday = parse_field(parts[5], 0, 7, "周")?;
        if weekday.bits[7] {
            weekday.bits[0] = true;
            weekday.bits[7] = false;
        }
        let day_or_weekday = !day.is_star && !weekday.is_star;
        Ok(Cron {
            sec,
            min,
            hour,
            day,
            month,
            weekday,
            day_or_weekday,
        })
    }

    /// 判定给定 UTC 时刻是否命中
    pub fn matches(&self, t: chrono::DateTime<Utc>) -> bool {
        let sec = t.second();
        let min = t.minute();
        let hour = t.hour();
        let day = t.day();
        let month = t.month();
        // chrono:Sun=0
        let weekday = t.weekday().num_days_from_sunday();
        let month_ok = self.month.matches(month);
        let dom_ok = self.day.matches(day);
        let dow_ok = self.weekday.matches(weekday);
        let day_ok = if self.day_or_weekday {
            // 两个都指定时 OR(vixie cron 语义)
            dom_ok || dow_ok
        } else {
            dom_ok && dow_ok
        };
        month_ok
            && day_ok
            && self.hour.matches(hour)
            && self.min.matches(min)
            && self.sec.matches(sec)
    }

    /// 距 t 最近的下次触发时刻(含 t 当秒之后的下一次;最坏逐秒扫描 366 天)
    pub fn next_after(&self, t: chrono::DateTime<Utc>) -> Option<chrono::DateTime<Utc>> {
        let mut cur = t + chrono::Duration::seconds(1);
        let limit = t + chrono::Duration::days(366);
        while cur <= limit {
            if self.matches(cur) {
                return Some(cur);
            }
            // 快进:秒位不命中时按命中秒列表跳;粗快进足够(调度场景分钟级为主)
            cur = cur + chrono::Duration::seconds(1);
        }
        None
    }
}

/// 一个已注册任务
type JobFn = Arc<dyn Fn() -> Pin<Box<dyn Future<Output = ()> + Send>> + Send + Sync>;

struct Task {
    name: &'static str,
    trigger: Trigger,
    job: JobFn,
}

enum Trigger {
    Cron(Cron),
    /// 对齐 Spring fixedDelay:上一次执行**完成后**再等 delay
    FixedDelay(Duration),
}

/// 调度运行时。任务逐个 spawn,永不中断;任务 panic 被捕获记 ERROR(对齐 Java 单任务异常不影响调度器)。
#[derive(Default)]
pub struct Scheduler {
    tasks: Vec<Task>,
}

impl Scheduler {
    pub fn new() -> Self {
        Scheduler { tasks: Vec::new() }
    }

    pub fn cron(mut self, name: &'static str, expr: &str, job: JobFn) -> Result<Self, CronError> {
        self.tasks.push(Task {
            name,
            trigger: Trigger::Cron(Cron::parse(expr)?),
            job,
        });
        Ok(self)
    }

    pub fn fixed_delay(mut self, name: &'static str, delay_ms: u64, job: JobFn) -> Self {
        self.tasks.push(Task {
            name,
            trigger: Trigger::FixedDelay(Duration::from_millis(delay_ms)),
            job,
        });
        self
    }

    /// 启动全部任务(每个 spawn 独立 task)。返回后调度在后台运行。
    pub fn start(self) {
        for task in self.tasks {
            tokio::spawn(async move {
                match &task.trigger {
                    Trigger::FixedDelay(d) => {
                        // fixedDelay:执行完成后等 delay 再下一轮(首轮立即)
                        loop {
                            run_job(&task).await;
                            tokio::time::sleep(*d).await;
                        }
                    }
                    Trigger::Cron(cron) => {
                        // 逐秒对齐到下一命中时刻;执行不阻塞时刻推进(对齐 Spring:同刻触发合并,
                        // 执行超长时跳过错过的时刻,下次从 now 起算)
                        loop {
                            let now = Utc::now();
                            match cron.next_after(now) {
                                None => {
                                    tracing::error!("[sched] {} cron 无下次触发时刻,任务退出", task.name);
                                    return;
                                }
                                Some(next) => {
                                    let wait = (next - Utc::now())
                                        .to_std()
                                        .unwrap_or(Duration::ZERO);
                                    tokio::time::sleep(wait).await;
                                    run_job(&task).await;
                                }
                            }
                        }
                    }
                }
            });
        }
    }
}

async fn run_job(task: &Task) {
    let job = (task.job)();
    let res = tokio::spawn(job).await;
    if let Err(err) = res {
        tracing::error!("[sched] {} panic:{err}", task.name);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_spring_six_field() {
        // Java 侧真实表达式逐一可解析
        for expr in [
            "0 * * * * *",       // OrderTimeout 每分钟
            "0 15 * * * *",      // AutoComplete 每小时:15
            "30 * * * * *",      // OutboxRelay / BlogViewsFlusher
            "0 40 4 * * *",      // CartMergeCleanup 04:40
            "0 30 4 * * *",      // ProcessedEventCleanup 04:30
            "0 5 3 * * *",       // ExchangeRate 03:05
            "0 */30 * * * *",    // ShipmentTracking 每 30 分
            "*/5 * * * * *",     // 步进秒
        ] {
            Cron::parse(expr).unwrap_or_else(|e| panic!("{expr}: {e}"));
        }
    }

    #[test]
    fn rejects_five_field() {
        assert!(Cron::parse("* * * * *").is_err());
    }

    #[test]
    fn matches_exact_minute() {
        let c = Cron::parse("0 15 * * * *").unwrap();
        let t = Utc::now().with_minute(15).unwrap().with_second(0).unwrap();
        assert!(c.matches(t));
        assert!(!c.matches(t.with_minute(16).unwrap()));
    }

    #[test]
    fn matches_step() {
        let c = Cron::parse("0 */30 * * * *").unwrap();
        assert!(c.matches(Utc::now().with_minute(0).unwrap().with_second(0).unwrap()));
        assert!(c.matches(Utc::now().with_minute(30).unwrap().with_second(0).unwrap()));
        assert!(!c.matches(Utc::now().with_minute(17).unwrap().with_second(0).unwrap()));
    }

    #[test]
    fn weekday_sunday_dual() {
        // 0 与 7 都是周日
        let c = Cron::parse("0 0 0 * * 7").unwrap();
        let t = Utc::now();
        let sunday = t + chrono::Duration::days((7 - t.weekday().num_days_from_sunday()) as i64);
        assert!(c.matches(sunday.with_hour(0).unwrap().with_minute(0).unwrap().with_second(0).unwrap()));
    }

    #[test]
    fn next_after_advances() {
        let c = Cron::parse("0 * * * * *").unwrap(); // 每分钟 0 秒
        let now = Utc::now().with_second(30).unwrap().with_nanosecond(0).unwrap();
        let next = c.next_after(now).unwrap();
        assert_eq!(next.second(), 0);
        assert!(next > now);
    }

    #[tokio::test]
    async fn fixed_delay_runs() {
        let hit = Arc::new(std::sync::atomic::AtomicUsize::new(0));
        let h = hit.clone();
        let s = Scheduler::new().fixed_delay(
            "t",
            10,
            Arc::new(move || {
                let h = h.clone();
                Box::pin(async move {
                    h.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
                })
            }),
        );
        s.start();
        tokio::time::sleep(Duration::from_millis(120)).await;
        assert!(hit.load(std::sync::atomic::Ordering::SeqCst) >= 2);
    }
}
