// 内容数据:blog(8 篇)/real weddings(6 个)/lookbook(3)/banner(4)/coupon(3)/公告
// 选题与故事框架来自 2026-09 竞品调研(Style Me Pretty 实证框架 + Birdy Grey SEO 路线)
import { IMG } from './data-products-helpers.mjs'

const P = (parts) => parts.join('\n\n')

export const blogPosts = [
  {
    title: 'What to Wear to a Beach Wedding: The Complete Guide', slug: 'what-to-wear-beach-wedding-guide',
    category: 'Wedding Guest Guides', author: 'The Dreamy Atelier', status: 1,
    cover: '/photography/blog-beach-attire.jpg'),
    excerpt: 'Sand, salt, and sunset light — everything you need to choose a dress (and shoes) that will survive the tide line beautifully.',
    content: P(['Sand changes everything. The dress that photographs like a dream in a ballroom can turn into a wrestling match on a beach — so before you fall in love with a silhouette, fall in love with the right fabric.',
      '**For brides:** think chiffon, tulle, and lightweight crepe. These fabrics move with the ocean breeze instead of fighting it, and they shake sand right out. Our Cove dress was tested on three beaches before we signed off on it — the mid-calf hem stays above the tide line, and it packs into a carry-on without a single wrinkle. If you have your heart set on a train, choose a detachable one; the ceremony photos will thank you, and the reception-you will too.',
      '**For guests:** avoid anything floor-length and heavily lined — hemlines act like sand scoops. Midi and tea-length dresses are the sweet spot. Flat or block-heel sandals are a must; stilettos sink six inches into soft sand, and there is no elegant way to retrieve them. Bring a wrap for after sunset: coastal air turns cool fast, even in August.',
      '**Color guidance:** soft neutrals, dusty blues, and warm corals photograph beautifully against sand and water. Skip the exact shade of the bride\'s ivory — and pure white, always.',
      '**Practical checklist:** garments steamed before travel (hotel irons are unreliable), a small tide-table check for ceremony timing, and dress weights sewn into hems if you are wearing anything midi. The wind is part of the venue; dress like you planned it that way.'])
  },
  {
    title: 'Garden Wedding Attire: Fabrics That Breathe (and Photograph Beautifully)', slug: 'garden-wedding-fabrics-guide',
    category: 'Wedding Guest Guides', author: 'The Dreamy Atelier', status: 1,
    cover: '/photography/blog-garden-fabrics.jpg'),
    excerpt: 'A garden in July is a photographer\'s dream and a polyester dress\'s nightmare. Here is how to pick fabrics that keep their cool.',
    content: P(['Garden weddings give you golden light, living backdrops, and — if you dress wrong — the slow realization that your dress has become a greenhouse. Fabric choice matters more here than at any other venue type.',
      '**The fabrics that work:** chiffon and tulle float away from the body and let air move; cotton-blend crepe breathes while holding a structured shape; matte jersey wicks better than any of them but photographs with a slight sheen under direct sun — know which you are getting. Our Bella bridesmaid dress uses double-layer chiffon precisely because single-layer reads pale and translucent against greenery.',
      '**The fabric to be careful with:** heavy satin and mikado. They photograph luxuriously in October and turn into saunas in July. If you are the bride and your heart is set on satin for a summer garden ceremony, look for an unlined or partial-lined bodice and plan portrait photos for golden hour, not noon.',
      '**Color against greenery:** sage, terracotta, blush, and espresso all sit beautifully against leaves. Pure white can go bluish in dappled shade — ivory photographs warmer. If your bridal party is standing in front of hedges, ask for a swatch photo in actual shade; the difference between store lighting and garden shade is bigger than most people expect.',
      'One last garden truth: grass eats heels. Block heels, wedges, or flats for everyone — the bride included. Our florist friends tell us the number-one garden-wedding injury is a stiletto ankle, and the number-one regret is a dry-clean-only dress that met the lawn.'])
  },
  {
    title: 'Cliffside & Coastal Ceremony Dresses That Won\'t Fight the Wind', slug: 'cliffside-coastal-dresses-wind',
    category: 'Buying Guides', author: 'The Dreamy Atelier', status: 1,
    cover: '/photography/hero-02.jpg'),
    excerpt: 'The view is dramatic; the wind is more dramatic. How to choose a silhouette that holds its shape three hundred feet above the Pacific.',
    content: P(['Every cliffside couple has the same Pinterest board: the bride, the drop, the infinite ocean. And every cliffside photographer has the same private knowledge: the wind has opinions about your dress.',
      'The good news is that wind, handled correctly, is a collaborator. The dresses that look incredible on cliffs are the ones engineered to catch air in a controlled way — heavier bodices with lighter skirts, so the dress moves without ever moving you.',
      '**What works:** fit-and-flare and mermaid silhouettes with structured bodices (the bodice anchors you, the hem performs), crepe and ponte fabrics that are too dense to whip, and two-piece sets that keep the midriff weighted. Our Dahlia dress was designed for exactly this — the bias-cut crepe catches wind in slow, photogenic waves rather than sudden billows.',
      '**What to avoid:** enormous tulle skirts (they become sails), cathedral trains (they become flags), and anything strapless without serious interior corsetry. If you must have volume, look for detachable elements you can remove before the ceremony-adjacent portraits.',
      '**Practical details that matter:** covered buttons rather than zippers at the back (zippers catch wind noise in video — yes, really), a hem weighted at the side seams, and a bustle that can be tied in under thirty seconds by a maid of honor who is also holding your bouquet. Rehearse it. The wind will not wait.'])
  },
  {
    title: 'Barn & Meadow Weddings: Textures That Love Golden Hour', slug: 'barn-meadow-wedding-textures',
    category: 'Buying Guides', author: 'The Dreamy Atelier', status: 1,
    cover: '/photography/lookbook-golden.jpg'),
    excerpt: 'Weathered wood and wild grasses want lace, crepe, and a little bit of sparkle. Here is how to dress for the most forgiving light of your life.',
    content: P(['Golden hour in a meadow is the most forgiving light that exists — it forgives a lot, but it also reveals everything. Texture becomes the whole story: smooth fabrics look flat, and dimensional fabrics come alive.',
      '**Lace is the meadow\'s native language.** Scalloped edges, re-embroidered florals, and tiered ruffles all catch the low sun and create depth that flat satin simply cannot. If you are the bride, look for lace with some openwork — at golden hour, the light through the pattern is the detail everyone remembers.',
      '**Crepe and matte satin photograph warm** against weathered barn wood. The slight texture of crepe in particular keeps the dress from looking like a plastic bag under flash once the party moves inside. Our Marisol mermaid was photographed in a Wisconsin barn at 6:47 p.m. and the sequin-free crepe did more with that light than any beaded gown could have.',
      '**Footwear, the eternal barn question:** the honest answer is a block heel you love plus a pair of boots for later. The floor of a 1912 barn is not going to be level, and the meadow certainly is not. Many of our barn brides change into embroidered cowboy boots for the reception and report that it was the best decision of the day, closely followed by marrying their person.',
      '**A note on sparkle:** a little goes far. One beaded element — a bodice, a belt, earrings — reads as candlelight. Full sequins under a barn\'s string lights can read as a road sign. Choose the one detail you want people to notice, and let golden hour do the rest.'])
  },
  {
    title: 'How to Choose a Wedding Dress for Sand, Grass, or Stone Aisles', slug: 'choose-dress-sand-grass-stone-aisles',
    category: 'Buying Guides', author: 'The Dreamy Atelier', status: 1,
    cover: '/photography/lookbook-garden.jpg'),
    excerpt: 'Your aisle surface decides your hemline, your shoes, and honestly some of your silhouette. A field guide, surface by surface.',
    content: P(['We spend months on silhouettes and lace patterns, and then the aisle gets a vote. Here is what each surface does to a dress — and how to choose one that ends the night still looking like it was chosen, not survived.',
      '**Sand.** The enemy of hems and heels. Tea-length and midi dresses are the obvious answer, but a floor-length chiffon also survives sand well — it is light enough to shake clean. What does not survive: heavy satin trains (they scoop), anything with a horsehair hem (it acts like a broom), and stilettos of any height. Wear flats or barefoot sandals and lean into it; barefoot-in-lace is a whole genre of beautiful photos.',
      '**Grass.** The hem consideration is smaller — grass stains wash from most fabrics but not from raw silk — but the shoe consideration is bigger. Heels sink, as every outdoor caterer will tell you while carrying plywood to put under the cake table. Block heels or wedges under 3 inches, always. A-line and ballgown hems all work; just ask about the hem\'s structure if you are buying satin, because a soft satin hem will pick up the lawn\'s dew pattern by the end of cocktail hour.',
      '**Stone and gravel.** The most forgiving surface for shoes and the most dangerous for delicate fabrics. Gravel snags tulle and shreds horsehair braid. If your venue is a stone courtyard or gravel path, choose crepe, ponte, or a tightly-woven mikado, and keep the tulle to an overskirt you can lift while walking. Heels are fine on stone — this is the one outdoor surface where stilettos behave — but mind the gaps between flagstones.',
      'The meta-advice: ask your venue for photos of the aisle in the season you are marrying, and match the dress to the surface like you would match a wine to a dish. The dress should look like it grew there.'])
  },
  {
    title: 'The 8–12 Week Timeline: When to Order Your Made-to-Order Gown', slug: 'made-to-order-timeline-guide',
    category: 'Planning', author: 'The Dreamy Atelier', status: 1,
    cover: '/photography/blog-atelier-timeline.jpg'),
    excerpt: 'Made-to-order means your dress is cut for you, not pulled from a shelf — which means the calendar is part of the design. Here is the honest timeline.',
    content: P(['"Why does my dress take eight weeks?" is the second-most-common question we hear, right after "will it fit?" — and the answers are related.',
      'A made-to-order gown is cut after you order it. The pattern is adjusted to your measurements, the fabric is cut by hand, the boning is shaped, and the lace — if there is lace — is placed by a person, not a machine. This is why made-to-order fits better than anything off a rack, and also why it cannot be rushed without charge: the calendar is not bureaucratic, it is craft.',
      '**Our honest timeline, week by week.** Week 0: you order; your measurements are reviewed by the atelier. Weeks 1–2: pattern adjustment and fabric cutting. Weeks 3–6: construction — bodice first, always, because everything hangs from it. Weeks 7–8: finishing, quality check, and a two-day rest (fabric relaxes; rushing the last two days shows). Then shipping, which adds 4–7 days. **Total: 8–12 weeks for most gowns, door to door.**',
      '**When to order:** your date minus 16 weeks is the sweet spot. That gives the atelier its full window plus a month of buffer for alterations near you. Bridesmaid dresses work on a similar but shorter cycle: 4–6 weeks made-to-order.',
      '**If you are inside 8 weeks:** rush production compresses the calendar to 3–4 weeks by re-sequencing the queue (and prioritizing your gown\'s cutting). It costs from $79 and — this matters — it does not skip steps; the dress gets the same construction, on a tighter schedule.',
      '**What we refuse to do:** promise a 2-week gown. Anyone who does is either shipping you a stock dress in a box or hoping you will not notice the difference. Your wedding date deserves better arithmetic than hope.'])
  },
  {
    title: 'What Is "Casual Outdoor Elegance"? A Dress Code, Explained', slug: 'casual-outdoor-elegance-dress-code',
    category: 'Wedding Guest Guides', author: 'The Dreamy Atelier', status: 1,
    cover: '/photography/blog-dress-code.jpg'),
    excerpt: 'It is on the invitation. It sounds like a contradiction. It is actually the easiest dress code to nail — here is the decode.',
    content: P(['"Casual outdoor elegance" strikes fear into guests because it sounds like two dress codes stapled together. In practice it is the kindest one: it means "look deliberate, not formal — and dress for grass."',
      '**The formula, if you want one:** one elevated element + one relaxed element. A midi dress in a dressy fabric (chiffon, matte satin) with flat sandals. Tailored trousers with silk camisole and woven wedges. A jumpsuit in a draped fabric. The elegance comes from fit and fabric; the casual comes from footwear and a general absence of structure like stiff boning or cathedral trains.',
      '**What it rules out:** black tie silhouettes (ballgowns will drag and look sad), business-formal suits (too indoor), and — please — jeans, even dark ones. The couple gave you a middle lane on purpose.',
      '**Color and pattern:** this dress code loves color. Terracotta, sage, sky blue, and floral prints all read as "understood the assignment." Save the little black dress for a city wedding; against a meadow, LBD reads like a typo.',
      '**For the mothers of the couple:** the same logic applies with a touch more polish — a midi or tea-length in lace or crepe, low block heels, and a wrap. You will be in more photos than anyone but the couple; the ground will be as uneven as it is for everyone else.',
      '**The one-sentence version:** dress like the outdoors is the venue and someone you love is getting married — because both are true.'])
  },
  {
    title: 'Real Weddings, Our First Chapter: Meet the Couples Behind the Dresses', slug: 'real-weddings-first-chapter',
    category: 'Real Weddings', author: 'The Dreamy Atelier', status: 1,
    cover: '/photography/blog-real-weddings.jpg'),
    excerpt: 'Six couples, six landscapes, one small atelier. A short note on why we photograph real weddings — and what you can learn from theirs.',
    content: P(['We started this atelier for a simple reason: outdoor weddings deserve dresses engineered for the outdoors, and real couples deserve to see real evidence.',
      'So this season, we followed six couples across six landscapes — a barefoot ceremony in Tulum, a sailcloth-tent garden in Charleston, a golden-hour barn in Wisconsin, a two-person cliffside elopement in Big Sur, a moss-green forest wedding in Oregon, and a desert-garden sunset in Santa Fe. Six real weddings, six real gowns from our line, zero staged photo shoots.',
      '**What we learned, in short:** the dress matters, but the match between dress and place matters more. The chiffon that survived the Tulum tide-line. The crepe mermaid that moved with Big Sur\'s wind instead of fighting it. The ballgown that owned a Wisconsin barn for exactly as long as it needed to, then bustedled into something you can actually polka in.',
      'Each story below follows the same shape: the couple\'s vision in their own words, the venue\'s honest constraints, and how the dress answered both. If you are planning your own outdoor wedding, steal freely — every decision in these stories is a decision you will make too.',
      'This is the first of many chapters. If you wore one of our dresses into a landscape we should see, write to us — the next six stories should be yours.'])
  }
]

// Real Weddings(6 个场景,对应 admin lookbook 封面与商品挂载)
export const realWeddings = [
  {
    couple: 'Maya & James', location: 'Tulum, Mexico', theme: 'Barefoot Beach', weddingDate: '2026-04-18', status: 1,
    title: 'A Barefoot Ceremony at the Tide Line',
    cover: '/photography/wedding-beach.jpg'),
    productSlugs: ['cove-short-beach-wedding-dress', 'coral-bridesmaid-dress'],
    story: 'Maya knew two things from the start: the ceremony would be at 4 p.m. when the light went gold, and she would be barefoot. The Cove — mid-calf, quick-dry chiffon — handled the tide line, the breeze, and the walk to dinner without a single steamer emergency. Her bridesmaids wore the Coral in a palette that matched the sunset almost suspiciously well. The reception was forty people, one long table, and the sea doing the decor.'
  },
  {
    couple: 'Elena & Theodore', location: 'Charleston, South Carolina', theme: 'Coastal Garden', weddingDate: '2026-05-09', status: 1,
    title: 'A Sailcloth Tent and a Garden in Full Bloom',
    cover: '/photography/wedding-garden.jpg'),
    productSlugs: ['aria-lace-aline-wedding-dress', 'sage-bridesmaid-dress-slit', 'bella-bridesmaid-dress-blush'],
    story: 'The vision was "a garden party that happens to include a wedding." A sailcloth tent, hanging garden roses, and a long family-style dinner under live oaks. Elena\'s Aria — scalloped lace, sweep train — moved through the garden like it belonged to it, and bustedled cleanly for dancing. The bridal party split sage and blush, because why choose. The detail everyone still talks about: the couple\'s dog, ring bearer, wearing a floral collar that matched the bridesmaids.'
  },
  {
    couple: 'Ruth & Henry', location: 'Driftless, Wisconsin', theme: 'Barn & Meadow', weddingDate: '2025-09-27', status: 1,
    title: 'Golden Hour in a 1912 Barn',
    cover: '/photography/hero-03.jpg'),
    productSlugs: ['marisol-chiffon-mermaid-wedding-dress', 'alex-bridesmaid-dress-black'],
    story: 'A family barn, a September sunset, and a bride who changed into embroidered cowboy boots before the first dance. Ruth\'s Marisol mermaid in flowing chiffon gave her the drama she wanted for portraits and the mobility she needed to polka — the hidden pleat did its job. Her bridesmaids wore the Alex in black, which photographed warm against weathered wood, and every one of them has worn it since. The meadow ceremony faced west on purpose.'
  },
  {
    couple: 'Priya & Alex', location: 'Big Sur, California', theme: 'Cliffside Elopement', weddingDate: '2026-03-14', status: 1,
    title: 'Two People, One Cliff, Wind as a Witness',
    cover: '/photography/lookbook-coastal.jpg'),
    productSlugs: ['dahlia-fit-flare-wedding-dress', 'sylvie-sheath-wedding-dress'],
    story: 'An elopement stripped to what mattered: vows, a photographer, and three hundred feet of Pacific below. Priya wore the Dahlia fit-and-flare — bias crepe that catches wind in slow waves — with covered buttons the videographer later thanked us for (no zipper noise). Their officiant was a friend on FaceTime; their witnesses were two hikers who happened by and stayed. Dinner was at a taqueria an hour south, still in the dress.'
  },
  {
    couple: 'June & Marcus', location: 'Willamette Valley, Oregon', theme: 'Forest & Moss', weddingDate: '2025-10-11', status: 1,
    title: 'A Moss Cathedral and Lantern Light',
    cover: '/photography/wedding-forest.jpg'),
    productSlugs: ['ingrid-longsleeve-aline-wedding-dress', 'sage-bridesmaid-dress-slit'],
    story: 'The aisle was a path of moss between Douglas firs, lit by lanterns, and the October air was exactly cool enough for long sleeves. June\'s Ingrid — illusion stretch-lace sleeves, low scoop back — was the rare dress that solved the temperature problem and the romance problem at once. Bridesmaids in sage disappeared into the forest in the best way: photos look like the party grew there. The reception was a heated tent with a wood stove and a folk trio.'
  },
  {
    couple: 'Sofia & Daniel', location: 'Santa Fe, New Mexico', theme: 'Desert Garden', weddingDate: '2026-05-30', status: 1,
    title: 'Sunset Over Adobe Walls',
    cover: '/photography/wedding-desert.jpg'),
    productSlugs: ['juno-two-piece-wedding-dress-set', 'mia-bridesmaid-dress-espresso'],
    story: 'A desert garden, adobe walls glowing at sunset, and a bride in two pieces: the Juno\'s beaded lace top and high-waist skirt for the ceremony, the top swapped for a silk cami at dinner. "Three outfits, one very sensible bride," she told us. Bridesmaids wore the Mia in espresso against terracotta and cactus bloom. The wind at 6,400 feet behaved itself for exactly as long as the portraits took — which is all any of us can ask.'
  }
]

// Lookbook(3 本,对应 admin 预置封面主题)
export const lookbooks = [
  { title: 'Coastal Romance', theme: 'Beach & Destination', status: 1, description: 'Salt air, bare feet, and chiffon that dries before the champagne. Our coastal edit: dresses engineered for tide lines and golden light.',
    cover: '/photography/lookbook-coastal.jpg'),
    productSlugs: ['cove-short-beach-wedding-dress', 'sylvie-sheath-wedding-dress', 'juno-two-piece-wedding-dress-set', 'coral-bridesmaid-dress', 'steel-blue-bridesmaid-dress'] },
  { title: 'The Garden Edit', theme: 'Garden & Boho', status: 1, description: 'Lace against greenery, lanterns under oaks, palettes grown from the landscape. Everything we love about saying vows in a garden.',
    cover: '/photography/lookbook-garden.jpg'),
    productSlugs: ['aria-lace-aline-wedding-dress', 'wren-tulle-aline-wedding-dress', 'dahlia-fit-flare-wedding-dress', 'bella-bridesmaid-dress-blush', 'sage-bridesmaid-dress-slit', 'petal-flower-girl-dress'] },
  { title: 'Golden Hour', theme: 'Barn & Meadow', status: 1, description: 'Weathered wood, wild grasses, and the most forgiving light of your life. Textures that come alive when the sun gets low.',
    cover: '/photography/lookbook-golden.jpg'),
    productSlugs: ['marisol-chiffon-mermaid-wedding-dress', 'odette-lace-mermaid-wedding-dress', 'bea-satin-ballgown-wedding-dress', 'alex-bridesmaid-dress-black', 'mia-bridesmaid-dress-espresso'] }
]

// Banner(position: HERO=1? 按枚举——MarketingSeed 用 HERO/FEATURED/TOPBAR;status 1=published)
export const banners = [
  { name: 'Hero — Coastal Season', imageUrl: '/photography/hero-01.jpg', position: 'HERO', status: 1, sort: 1,
    title: 'Dresses Made for the Outdoors', subtitle: 'Engineered for tide lines, gardens, and golden hour — not ballrooms.', ctaText: 'Shop Wedding Dresses', ctaLink: '/wedding-dresses' },
  { name: 'Hero — Garden Season', imageUrl: '/photography/hero-02.jpg', position: 'HERO', status: 1, sort: 2,
    title: 'The Garden Edit', subtitle: 'Lace, tulle, and palettes grown from the landscape.', ctaText: 'Explore the Edit', ctaLink: '/inspiration' },
  { name: 'Featured — Made-to-Order', imageUrl: '/photography/featured-atelier.jpg', position: 'FEATURED', status: 1, sort: 1,
    title: 'Cut for You, Not for a Shelf', subtitle: 'Every gown made to your measurements in 8–12 weeks. Rush available.', ctaText: 'How It Works', ctaLink: '/blog/made-to-order-timeline-guide' },
  { name: 'Featured — Bridesmaid Program', imageUrl: '/photography/wedding-garden.jpg', position: 'FEATURED', status: 1, sort: 2,
    title: 'One Dress, Six Ways', subtitle: 'Convertible bridesmaid styles your friends will actually wear again.', ctaText: 'Shop Bridesmaids', ctaLink: '/products?cat=Bridesmaids' }
]

// 优惠券(value 为展示文案,格式对齐 V-MKT 校验:type DISCOUNT="N% OFF"/FIXED="$N OFF"/FREE_SHIPPING="Free Shipping")
export const coupons = [
  { code: 'WELCOME10', name: 'Welcome — 10% Off First Order', type: 1, value: '10% OFF', minAmount: 150, totalLimit: 5000, status: 1, description: '10% off your first order over $150 — new atelier friends only.' },
  { code: 'FREESHIP199', name: 'Free US Shipping over $199', type: 3, value: 'Free Shipping', minAmount: 199, totalLimit: 20000, status: 1, description: 'Complimentary U.S. shipping on every order, every day — automatically applied at checkout.' },
  { code: 'BRIDALPARTY15', name: 'Bridal Party — 15% off 4+ Dresses', type: 1, value: '15% OFF', minAmount: 600, totalLimit: 3000, status: 1, description: 'Order four or more bridesmaid dresses together and save 15% on the group.' }
]

// 顶部公告
export const announcements = [
  { content: 'Made-to-order gowns in 8–12 weeks · Rush production available · Free U.S. shipping over $199', priority: 1, status: 1 }
]
