// 评价数据生成:按竞品调研的真实感要素(场景/身高尺码/物流/中差评+官方回复/时间自然分布)
// 评分区间 4.6-4.9,hero 款多评,新品少评;经 SQL 直插(review 表 uk_user_product 约束→一人一品一评)
import { weddingDresses } from './data-products-wedding.mjs'
import { bridesmaidDresses, occasionDresses } from './data-products-party.mjs'

// 固定伪随机(可重现)
let seed = 20260911
const rnd = () => { seed = (seed * 9301 + 49297) % 233280; return seed / 233280 }
const pick = (arr) => arr[Math.floor(rnd() * arr.length)]

const FIRST = ['Emma', 'Olivia', 'Ava', 'Sophia', 'Isabella', 'Mia', 'Charlotte', 'Amelia', 'Harper', 'Evelyn', 'Abigail', 'Emily', 'Ella', 'Elizabeth', 'Camila', 'Luna', 'Sofia', 'Avery', 'Mila', 'Aria', 'Scarlett', 'Penelope', 'Layla', 'Nora', 'Zoey', 'Hannah', 'Lily', 'Addison', 'Eleanor', 'Natalie', 'Savannah', 'Brooklyn', 'Leah', 'Zoe', 'Stella', 'Audrey', 'Claire', 'Bella', 'Lucia', 'Paisley']
const LAST = ['Smith', 'Johnson', 'Williams', 'Brown', 'Jones', 'Garcia', 'Miller', 'Davis', 'Rodriguez', 'Martinez', 'Anderson', 'Taylor', 'Thomas', 'Moore', 'Jackson', 'Martin', 'Lee', 'Thompson', 'White', 'Harris']

// 5 星文案池(要素组合:场景+合身/物流/情感)
const FIVE = [
  'Wore this for my beach ceremony in Tulum and it was PERFECT. The chiffon moved with the wind, not against it, and it dried in minutes after the tide came in. Every photo looks like a magazine.',
  'I am 5\'4", usually a street size 6, and the size 6 fit like it was made for me. The bodice has real structure — I danced for four hours and never once thought about the dress.',
  'Arrived in exactly 6 weeks, packed beautifully with a garment bag and a handwritten note. The lace is even prettier in person — the photos do not do the scalloped edges justice.',
  'My bridesmaids ordered this in three sizes (we range from 0 to 16) and it was flattering on every single one. The two-way stretch crepe is no joke — it forgives everything.',
  'I cried when I opened the box and cried again at the altar. My seamstress said the construction is better than gowns she has seen at three times the price.',
  'The twirl on this dress!!! My photographer made me spin for twenty minutes and every frame is a keeper. The horsehair hem holds the shape exactly like they describe.',
  'Ordered with custom measurements and it fits better than anything I have ever worn, wedding dress or otherwise. Worth every penny and the 8-week wait was exactly as promised.',
  'We got married in a garden in October and this dress was made for it. Got compliments from strangers on the subway two weeks later — yes, I have worn it again.',
  'The color is exactly as pictured (sage, between grey and green like the listing says). My maid of honor has already ordered it for her own wedding next year.',
  'Fast shipping, gorgeous packaging, and the dress is stunning. The built-in bra means no complicated undergarment situation. 10/10 would marry my husband again in this dress.',
  'I was so nervous ordering my wedding dress online. The fit guide is accurate, the fabric feels expensive, and their team answered my measurement questions within hours. Absolutely recommend.',
  'This dress photographed like a DREAM in golden hour. Our barn wedding was 85 degrees and the unlined bodice kept me cool all night. Pockets held my lipstick, tissues, and my dignity.'
]
// 4 星文案池(留一点真实的小抱怨)
const FOUR = [
  'Beautiful dress, great quality. Runs slightly long — I am 5\'5" and needed a 2-inch hem for flat shoes. Budget for alterations and you will love it.',
  'Gorgeous, exactly as pictured. Docking one star because shipping took a week longer than estimated. The dress itself is worth the wait though.',
  'The lace is stunning and the fit through the bodice is great. The skirt has a little more volume in person than I expected — beautiful, just know what you are ordering.',
  'Very happy overall. The zipper needed a little wiggle the first time but loosened up after steaming. My bridesmaids all looked incredible.',
  'Excellent quality for the price. I sized down based on reviews and that was the right call — the stretch crepe has real give. Would buy again.',
  'Lovely dress, wore it for a cliffside ceremony and the wind behaved — mostly. The covered buttons are gorgeous but bring a patient maid of honor for bustling.'
]
// 3 星文案池(真实的失望点+官方回复)
const THREE = [
  'The dress is pretty but the color reads warmer in person than on screen. I ordered ivory expecting cooler white. Keeping it because the fit is good.',
  'Fit was true to size but I found the bodice a little stiff through the midsection by hour six. Beautiful for the ceremony, less comfortable by the end of the reception.'
]
// 2 星文案池
const TWO = [
  'The dress itself is lovely but mine arrived with a small pull in the chiffon at the hem. Their team arranged a replacement within a week, so updating from frustrated to hopeful.'
]
const REPLIES = [
  'Thank you for the honest feedback! We have passed this along to our atelier team. Please reach out to care@dreamy.com and we will make it right. — Dreamy Team',
  'We are so sorry to hear this! Quality issues are not acceptable — our care team will contact you today to arrange a replacement or full refund. — Dreamy Team',
  'Thank you for the detailed review — this helps other brides shop smarter. We will take another look at our color photography. — Dreamy Team'
]

// 评价数分布:hero 款 22-40 / 主力 8-16 / 常规 4-8 / 新品 3-5
const heroSlugs = ['aria-lace-aline-wedding-dress', 'wren-tulle-aline-wedding-dress', 'cove-short-beach-wedding-dress', 'alex-bridesmaid-dress-black', 'bryten-convertible-bridesmaid-dress', 'cassia-one-shoulder-prom-dress']
const bestSlugs = ['bea-satin-ballgown-wedding-dress', 'marisol-chiffon-mermaid-wedding-dress', 'odette-lace-mermaid-wedding-dress', 'bella-bridesmaid-dress-blush', 'sage-bridesmaid-dress-slit', 'shimmer-sequin-homecoming-dress', 'fleur-floral-sweetheart-prom-dress', 'juno-two-piece-wedding-dress-set']

export function buildReviews() {
  const all = [...weddingDresses, ...bridesmaidDresses, ...occasionDresses]
  const usedNames = new Set()
  const makeName = () => {
    for (let i = 0; i < 200; i++) {
      const n = `${pick(FIRST)} ${pick(LAST)}`
      if (!usedNames.has(n)) { usedNames.add(n); return n }
    }
    throw new Error('名字池耗尽')
  }
  const daysAgo = () => Math.floor(Math.pow(rnd(), 1.7) * 300) + 3 // 近密远疏

  const rows = []
  for (const p of all) {
    let count
    if (heroSlugs.includes(p.slug)) count = 22 + Math.floor(rnd() * 18)
    else if (bestSlugs.includes(p.slug)) count = 8 + Math.floor(rnd() * 8)
    else if (p.isNew) count = 3 + Math.floor(rnd() * 3)
    else count = 4 + Math.floor(rnd() * 4)

    for (let i = 0; i < count; i++) {
      const r = rnd()
      let rating, content, reply = null
      if (r < 0.70) { rating = 5; content = pick(FIVE) }
      else if (r < 0.92) { rating = 4; content = pick(FOUR) }
      else if (r < 0.985) { rating = 3; content = pick(THREE); reply = pick(REPLIES) }
      else { rating = 2; content = pick(TWO); reply = pick(REPLIES) }
      if (rating <= 4 && rnd() < 0.25) reply = pick(REPLIES) // 部分好评也有回复
      const d = new Date(Date.now() - daysAgo() * 86400000)
      rows.push({ slug: p.slug, name: makeName(), rating, content, reply, submittedAt: d })
    }
  }
  return rows
}

// 商品冗余评分列同步(rating_avg/rating_count 由 seed SQL 一并 UPDATE)
export function ratingSummary(rows) {
  const bySlug = {}
  for (const r of rows) {
    bySlug[r.slug] ??= { sum: 0, n: 0 }
    bySlug[r.slug].sum += r.rating
    bySlug[r.slug].n++
  }
  const out = {}
  for (const [slug, { sum, n }] of Object.entries(bySlug)) out[slug] = { avg: (sum / n).toFixed(2), n }
  return out
}
