// 伴娘系列(10 款,对标 Birdy Grey/Azazie 价格带 $129-189,免费定制尺码)
// 晚装与配饰(7+3 款)
// 文案跟图纪律:每款图库 = 全身主图 → -detail 细节裁切 → -skirt 裙摆裁切(如有);单款单色,不混图
import { dress, IMG } from './data-products-helpers.mjs'

export const bridesmaidDresses = [
  dress({
    name: 'Alex Bridesmaid Dress in Black', slug: 'alex-bridesmaid-dress-black',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 189, compareAt: 229, isBest: true,
    color: 'Black', fabricMain: 'Crepe',
    description: 'The little black dress of bridesmaid dresses: a square neckline, tie straps, clean crepe, and a slit that says "reception-ready." Your friends will wear this again — that\'s the whole point.',
    designerNote: 'Tested through four weddings and one karaoke night. Crepe with two-way stretch means it fits right out of the box and stays put through the toasts.',
    sellingPoints: ['Two-way stretch crepe', 'Square neck with tie straps and open back', 'Side slit for easy walking', 'Re-wearable — genuinely'],
    leadTimeDays: 28, rushAvailable: true, customSizeAvailable: true,
    attributes: { silhouette: ['Sheath'], neckline: ['Square'], fabric: ['Crepe'], back_style: ['Open Back'], length: ['Floor'], style_tag: ['Modern', 'Minimalist'] },
    images: [
      { url: IMG('birdygrey/pdp-alex-black-model-01.jpg'), kind: 1, sort: 0 },
      { url: IMG('birdygrey/pdp-alex-black-model-03.jpg'), kind: 1, sort: 1 },
      { url: IMG('birdygrey/pdp-alex-black-model-02.jpg'), kind: 1, sort: 2 },
      { url: IMG('birdygrey/bridesmaid-black-alex-07-detail.jpg'), kind: 1, sort: 3 }
    ],
    collections: ['Black & Espresso']
  }),
  dress({
    name: 'Mia Bridesmaid Dress in Espresso', slug: 'mia-bridesmaid-dress-espresso',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 139,
    color: 'Espresso', fabricMain: 'Crepe',
    description: 'A deep espresso brown that flatters every skin tone, in the same beloved crepe as our Alex dress. Strapless cowl neckline in front, a leg slit for the dance floor.',
    designerNote: 'Espresso replaced black at three of our photoshoot weddings this year — it photographs warmer and pairs with greenery like a dream.',
    sellingPoints: ['Strapless cowl neckline', 'Side slit for easy walking', 'Two-way stretch crepe', 'Pairs beautifully with sage + terracotta palettes'],
    leadTimeDays: 28, rushAvailable: true,
    attributes: { silhouette: ['Sheath'], neckline: ['Cowl'], sleeve: ['Sleeveless'], fabric: ['Crepe'], back_style: ['Zipper'], length: ['Floor'], style_tag: ['Classic', 'Modern'] },
    images: [
      { url: IMG('birdygrey/bridesmaid-espresso-mia-05.jpg'), kind: 1, sort: 0 },
      { url: IMG('birdygrey/bridesmaid-espresso-mia-05-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Black & Espresso']
  }),
  dress({
    name: 'Bella Bridesmaid Dress in Blush', slug: 'bella-bridesmaid-dress-blush',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 139, compareAt: 169, recommend: true,
    color: 'Blush', fabricMain: 'Chiffon',
    description: 'Soft blush chiffon on thin straps with a ruched sweetheart bodice and a cascading ruffle that falls from the waist — the romantic\'s bridesmaid dress. The ruching is just forgiving enough after the pasta course.',
    designerNote: 'We added a second chiffon layer at the skirt so blush reads pink, not pale, in photos — and cut the waist ruffle on the bias so it cascades instead of sticking out.',
    sellingPoints: ['Thin straps with ruched sweetheart bodice', 'Cascading waist ruffle', 'Double-layer blush chiffon', 'Ruched waist — dinner friendly'],
    leadTimeDays: 28, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Sweetheart'], sleeve: ['Sleeveless'], fabric: ['Chiffon'], embellishment: ['Ruffles'], length: ['Floor'], style_tag: ['Romantic'] },
    images: [
      { url: IMG('birdygrey/bridesmaid-pink-bella-01.jpg'), kind: 1, sort: 0 },
      { url: IMG('birdygrey/bridesmaid-pink-bella-01-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blush & Dusty Rose']
  }),
  dress({
    name: 'Bryten Off-the-Shoulder Bridesmaid Dress with Tie Sleeves', slug: 'bryten-convertible-bridesmaid-dress',
    categoryPath: ['Bridesmaids', 'Short & Convertible'],
    price: 189, compareAt: 229, isBest: true,
    color: 'Blush', fabricMain: 'Chiffon',
    description: 'An off-the-shoulder ruched column with removable tie sleeves — wear the sleeves tied soft for the ceremony, slip them off for the dance floor. Every bridesmaid styles it her way, and your photos still match.',
    designerNote: 'The tie sleeves attach at hidden loops inside the off-shoulder band, so the neckline stays clean either way. We include a printed styling card — it has saved at least one bridal party from a group-chat war.',
    sellingPoints: ['Off-the-shoulder neckline with removable tie sleeves', 'Two looks: sleeves on or off', 'Ruched column silhouette', 'Printed styling card included'],
    leadTimeDays: 35, rushAvailable: true,
    attributes: { silhouette: ['Sheath'], neckline: ['Off-Shoulder'], sleeve: ['Short Sleeve'], fabric: ['Chiffon'], length: ['Floor'], style_tag: ['Modern', 'Romantic'] },
    images: [
      { url: IMG('birdygrey/bridesmaid-pink-connie-03.jpg'), kind: 1, sort: 0 },
      { url: IMG('birdygrey/bridesmaid-pink-connie-03-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blush & Dusty Rose']
  }),
  dress({
    name: 'Emmy One-Shoulder Bridesmaid Dress', slug: 'emmy-one-shoulder-bridesmaid-dress',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 159, compareAt: 189, isNew: true,
    color: 'Blush', fabricMain: 'Crepe',
    description: 'An asymmetric one-shoulder crepe column with a long draped sash that falls from the shoulder — clean lines for modern palettes. The side slit is measured to hit mid-thigh: elegant standing, easy dancing.',
    designerNote: 'The shoulder strap is structured with a hidden stay so it never slides — we promise. The sash is a single length of crepe, so it drapes in the same soft line every wear.',
    sellingPoints: ['Asymmetric one-shoulder with hidden stay', 'Long draped shoulder sash', 'Mid-thigh side slit', 'Matte stretch crepe — no cling, no shine'],
    leadTimeDays: 28,
    attributes: { silhouette: ['Sheath'], neckline: ['One-Shoulder'], fabric: ['Crepe'], length: ['Floor'], style_tag: ['Modern'] },
    images: [
      { url: IMG('birdygrey/bridesmaid-pink-bryten-02.jpg'), kind: 1, sort: 0 },
      { url: IMG('birdygrey/bridesmaid-pink-bryten-02-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blush & Dusty Rose']
  }),
  dress({
    name: 'Sage Bridesmaid Dress with Slit', slug: 'sage-bridesmaid-dress-slit',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 139, compareAt: 165, recommend: true,
    color: 'Sage', fabricMain: 'Chiffon',
    description: 'Garden-party sage green in airy chiffon, with thin straps, a scoop neckline, real pockets, and a thigh slit. The color that made "greenery wedding" a thing, done properly.',
    designerNote: 'Our sage is dyed to sit between grey and green — it works against foliage instead of disappearing into it.',
    sellingPoints: ['True garden sage green', 'Scoop neck with thin straps', 'Pockets — real, deep ones', 'Thigh slit with lining included'],
    leadTimeDays: 28, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Scoop'], sleeve: ['Sleeveless'], fabric: ['Chiffon'], back_style: ['Zipper'], length: ['Floor'], style_tag: ['Boho', 'Romantic'], occasion: ['Garden'] },
    images: [
      { url: IMG('davidsbridal/bridesmaid-sage-01.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/bridesmaid-sage-01-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Sage & Olive', 'Garden Romance']
  }),
  dress({
    name: 'Steel Blue Cold-Shoulder Bridesmaid Dress', slug: 'steel-blue-bridesmaid-dress',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 139,
    color: 'Steel Blue', fabricMain: 'Chiffon',
    description: 'Cool steel blue with cold-shoulder flutter sleeves and a leg slit — the color for coastal and winter palettes alike. Chiffon keeps it light even in the deeper shade.',
    designerNote: 'Designed for a seaside wedding in Maine; re-requested for a mountain wedding in Colorado. That versatile.',
    sellingPoints: ['Cool steel blue, season-flexible', 'Cold-shoulder flutter sleeves', 'Side slit, airy lined chiffon', 'Pairs with silver + dusty blue palettes'],
    leadTimeDays: 28,
    attributes: { silhouette: ['A-Line'], neckline: ['Cold-Shoulder'], sleeve: ['Short Sleeve'], fabric: ['Chiffon'], back_style: ['Zipper'], length: ['Floor'], style_tag: ['Classic'], occasion: ['Beach'] },
    images: [
      { url: IMG('davidsbridal/bridesmaid-steelblue-02.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/bridesmaid-steelblue-02-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blue Hues']
  }),
  dress({
    name: 'Coral Bridesmaid Dress', slug: 'coral-bridesmaid-dress',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 139, isNew: true,
    color: 'Coral', fabricMain: 'Satin',
    description: 'Sun-warmed coral satin, made for beach ceremonies and golden light. A soft cowl neckline on thin straps and a leg slit that catches the sunset.',
    designerNote: 'Coral is treacherous — one shade wrong and it fights the bride\'s ivory. Ours is softened with a drop of pink precisely to sit beside white.',
    sellingPoints: ['Beach-approved warm coral', 'Cowl neckline with thin straps', 'Side slit with secured lining', 'Satin that photographs gold at sunset'],
    leadTimeDays: 28, rushAvailable: true,
    attributes: { silhouette: ['Sheath'], neckline: ['Cowl'], sleeve: ['Sleeveless'], fabric: ['Satin'], length: ['Floor'], style_tag: ['Romantic'], occasion: ['Beach'], season: ['Summer'] },
    images: [
      { url: IMG('davidsbridal/bridesmaid-coral-03.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/bridesmaid-coral-03-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Coastal Bride']
  }),
  dress({
    name: 'Dusty Blue Cowl-Neck Satin Bridesmaid Dress', slug: 'dusty-blue-bridesmaid-dress-bow',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 139,
    color: 'Dusty Blue', fabricMain: 'Satin',
    description: 'Dusty blue satin with a soft cowl neckline and a leg slit — the simple silhouette your photographer keeps framing. Cooler than navy, softer than sky.',
    designerNote: 'The cowl is weighted with a hidden chain so it drapes in the same soft fold every time — no fussing, no safety pins.',
    sellingPoints: ['Weighted cowl neckline, drapes every time', 'Dusty blue — cooler than navy, softer than sky', 'Side slit with secured lining', 'Soft-sheen satin, fully lined'],
    leadTimeDays: 28,
    attributes: { silhouette: ['Sheath'], neckline: ['Cowl'], sleeve: ['Sleeveless'], fabric: ['Satin'], back_style: ['Zipper'], length: ['Floor'], style_tag: ['Romantic', 'Classic'] },
    images: [
      { url: IMG('davidsbridal/bridesmaid-dustyblue-04.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/bridesmaid-dustyblue-04-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blue Hues']
  }),
  dress({
    name: 'Danny Cowl-Neck Bridesmaid Dress in Blush', slug: 'danny-cowl-neck-bridesmaid-dress-blush',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 149, compareAt: 179,
    color: 'Blush', fabricMain: 'Crepe',
    description: 'A sleeveless cowl neck, a softly ruched waist, and a floor-length crepe column — the Danny is the bridesmaid dress that looks like it cost three times more. Blush that reads warm in daylight and rosy under string lights.',
    designerNote: 'The ruching sits exactly at the natural waist and is anchored at the side seam, so it shapes without shifting. The cowl is cut on the bias — it falls the same way every wear.',
    sellingPoints: ['Sleeveless bias-cut cowl neckline', 'Ruched waist — flattering on every body', 'Floor-length matte crepe column', 'Re-wearable well beyond the wedding'],
    leadTimeDays: 28, rushAvailable: true,
    attributes: { silhouette: ['Sheath'], neckline: ['Cowl'], sleeve: ['Sleeveless'], fabric: ['Crepe'], back_style: ['Zipper'], length: ['Floor'], style_tag: ['Modern', 'Romantic'] },
    images: [
      { url: IMG('birdygrey/bridesmaid-pink-danny-08.jpg'), kind: 1, sort: 0 },
      { url: IMG('birdygrey/bridesmaid-pink-danny-08-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blush & Dusty Rose']
  })
]

export const occasionDresses = [
  dress({
    name: 'Cassia One-Shoulder Prom Dress', slug: 'cassia-one-shoulder-prom-dress',
    categoryPath: ['Occasion & Party', 'Prom & Evening'],
    price: 189, compareAt: 229, isBest: true, recommend: true,
    color: 'Sage', fabricMain: 'Tulle',
    description: 'A one-shoulder sage tulle gown with a lace-appliqué bodice and a high leg slit for prom season — or any night that deserves a train. The asymmetric neckline frames the collarbone; the soft tulle skirt does the rest.',
    designerNote: 'Named after the first customer who wore it to prom and then again, twice, to two weddings. It\'s that dress.',
    sellingPoints: ['Asymmetric one-shoulder neckline', 'Lace appliqué bodice over sage tulle', 'Thigh-high slit', 'Floor length with mini train'],
    leadTimeDays: 35, rushAvailable: true, customSizeAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['One-Shoulder'], fabric: ['Tulle'], embellishment: ['Appliqué', 'Lace'], length: ['Floor'], style_tag: ['Romantic'], season: ['Spring'] },
    images: [
      { url: IMG('kissprom/prom-sage-oneshoulder-01.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/prom-sage-oneshoulder-01-detail.jpg'), kind: 1, sort: 1 },
      { url: IMG('kissprom/prom-sage-oneshoulder-01-skirt.jpg'), kind: 1, sort: 2 }
    ],
    collections: ['Sage & Olive']
  }),
  dress({
    name: 'Lux Champagne Lace A-Line Gown', slug: 'lux-champagne-lace-prom-dress',
    categoryPath: ['Occasion & Party', 'Prom & Evening'],
    price: 219, compareAt: 269, recommend: true,
    color: 'Champagne', fabricMain: 'Lace',
    description: 'Champagne lace over a smoothing base, with a straight neckline on thin straps and a soft A-line skirt. Old-Hollywood energy with a modern fit — the dress that gets two compliments before you\'ve checked your coat.',
    designerNote: 'The lace is placed so the pattern mirrors itself across the center front — symmetry you feel even if you never notice it.',
    sellingPoints: ['Mirrored champagne lace placement', 'Straight neckline on thin straps, inner support', 'Soft A-line skirt, fully lined', 'Zero cling, all-night comfort'],
    leadTimeDays: 35,
    attributes: { silhouette: ['A-Line'], neckline: ['Square'], sleeve: ['Strap'], fabric: ['Lace'], back_style: ['Zipper'], embellishment: ['Lace'], length: ['Floor'], style_tag: ['Glam', 'Vintage'] },
    images: [
      { url: IMG('kissprom/prom-champagne-lace-05.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/prom-champagne-lace-05-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Ivory & Champagne']
  }),
  dress({
    name: 'Emerald Lace A-Line Evening Gown', slug: 'emerald-lace-evening-gown',
    categoryPath: ['Occasion & Party', 'Prom & Evening'],
    price: 209, compareAt: 249,
    color: 'Emerald', fabricMain: 'Lace',
    description: 'Deep emerald lace on thin straps with a straight neckline and a soft A-line skirt — the winter-formal answer to "I want to look like a jewel." Fully lined, softly fitted at the bodice, quietly knockout.',
    designerNote: 'A test batch survived a Chicago December (with the matching wrap). Approved.',
    sellingPoints: ['Straight-neck lace bodice on thin straps, inner support', 'Deep emerald, fully lined', 'Soft A-line skirt', 'Winter-formal approved'],
    leadTimeDays: 35, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Square'], sleeve: ['Strap'], fabric: ['Lace'], embellishment: ['Lace'], length: ['Floor'], style_tag: ['Classic', 'Glam'], season: ['Winter'] },
    images: [
      { url: IMG('kissprom/prom-darkgreen-lace-06.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/prom-darkgreen-lace-06-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Boho Wildflower']
  }),
  dress({
    name: 'Tiered Lace Prom Dress in Scarlet', slug: 'tiered-off-shoulder-prom-dress',
    categoryPath: ['Occasion & Party', 'Prom & Evening'],
    price: 229, compareAt: 279, isNew: true,
    color: 'Scarlet', fabricMain: 'Lace',
    description: 'Cascading tiers of scarlet lace from a sweetheart bodice on thin straps, with a leg slit cut into the layers — a dress that takes up exactly the right amount of space on the dance floor. Twirl-tested at 120 BPM.',
    designerNote: 'Graduated lace tiers, each 1.5 cm wider than the last, and the slit opens through all of them. The math of a good twirl.',
    sellingPoints: ['Cascading tiered lace skirt', 'Sweetheart neckline with thin straps', 'Leg slit through the tiers', 'Twirl-tested, photographer approved'],
    leadTimeDays: 42, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Sweetheart'], sleeve: ['Sleeveless'], fabric: ['Lace'], embellishment: ['Ruffles', 'Lace'], length: ['Floor'], style_tag: ['Romantic', 'Glam'] },
    images: [
      { url: IMG('kissprom/prom-offshoulder-tiered-07.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/prom-offshoulder-tiered-07-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: []
  }),
  dress({
    name: 'Fleur Lavender Floral Jacquard Ballgown', slug: 'fleur-floral-sweetheart-prom-dress',
    categoryPath: ['Occasion & Party', 'Prom & Evening'],
    price: 249, compareAt: 299, recommend: true,
    color: 'Lavender', fabricMain: 'Jacquard',
    description: 'A strapless ballgown in blooms — lavender florals woven into ivory jacquard, dense at the bodice, drifting toward the hem. Romantic without trying, structured enough to own the room.',
    designerNote: 'The floral is woven into the jacquard, not printed, so it catches light at the petals. We match the pattern across the center seam by hand.',
    sellingPoints: ['Woven lavender floral jacquard on ivory', 'Strapless bodice with built-in support', 'Full ballgown skirt with petticoat', 'Pattern hand-matched at every seam'],
    leadTimeDays: 42,
    attributes: { silhouette: ['Ballgown'], neckline: ['Strapless'], sleeve: ['Sleeveless'], fabric: ['Jacquard'], back_style: ['Zipper'], length: ['Floor'], style_tag: ['Romantic', 'Classic'] },
    images: [
      { url: IMG('kissprom/prom-floral-sweetheart-08.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/prom-floral-sweetheart-08-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Garden Romance']
  }),
  dress({
    name: 'Blush One-Shoulder Tulle Prom Dress', slug: 'blush-one-shoulder-homecoming-dress',
    categoryPath: ['Occasion & Party', 'Prom & Evening'],
    price: 149, compareAt: 179,
    color: 'Blush', fabricMain: 'Tulle',
    description: 'A floor-length blush tulle gown with a one-shoulder neckline, 3D floral appliqué trailing the bodice, and a high leg slit. Soft, confident, and comfortable in a room full of sequins.',
    designerNote: 'Each floral appliqué is hand-tacked so the petals lift off the tulle — they read three-dimensional in photos, not flat.',
    sellingPoints: ['Structured one-shoulder neckline', '3D floral appliqué on soft tulle', 'Floor length with high leg slit', 'Fully lined with tulle overlay'],
    leadTimeDays: 28, rushAvailable: true, customSizeAvailable: false,
    attributes: { silhouette: ['A-Line'], neckline: ['One-Shoulder'], fabric: ['Tulle'], embellishment: ['Appliqué'], length: ['Floor'], style_tag: ['Romantic'] },
    images: [
      { url: IMG('kissprom/prom-blush-oneshoulder-03.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/prom-blush-oneshoulder-03-detail.jpg'), kind: 1, sort: 1 },
      { url: IMG('kissprom/prom-blush-oneshoulder-03-skirt.jpg'), kind: 1, sort: 2 }
    ],
    collections: ['Blush & Dusty Rose']
  }),
  dress({
    name: 'Lavender One-Shoulder Prom Dress', slug: 'lavender-one-shoulder-prom-dress',
    categoryPath: ['Occasion & Party', 'Prom & Evening'],
    price: 179,
    color: 'Lavender', fabricMain: 'Tulle',
    description: 'Soft lavender tulle with a one-shoulder neckline, lace appliqué across the bodice, and a high leg slit — pastel done with a spine. The appliqué is placed to trail from the shoulder in the same line every time.',
    designerNote: 'The shoulder is interfaced feather-light, so it holds its shape without ever standing away from the body.',
    sellingPoints: ['Sculpted one-shoulder neckline', 'Lace appliqué on soft lavender tulle', 'High slit with secured lining', 'Floor length, fully lined'],
    leadTimeDays: 35, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['One-Shoulder'], fabric: ['Tulle'], embellishment: ['Appliqué', 'Lace'], length: ['Floor'], style_tag: ['Modern', 'Romantic'] },
    images: [
      { url: IMG('kissprom/prom-lavender-oneshoulder-04.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/prom-lavender-oneshoulder-04-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Modern Minimal']
  })
]

export const accessories = [
  dress({
    name: 'Bridal Jewelry Set — Pearl & Leaf', slug: 'bridal-jewelry-set-pearl-leaf',
    categoryPath: ['Accessories', 'Jewelry & Headpieces'],
    price: 89, compareAt: 109, isNew: true,
    color: 'Pearl White', fabricMain: 'Alloy',
    description: 'Freshwater-look pearls and hand-set leaf charms on gold-plated brass — earrings and a necklace that finish a neckline without competing with it. Hypoallergenic posts.',
    designerNote: 'Designed to sit flat under lace necklines; tested against three bodice styles before we approved the chain drop.',
    sellingPoints: ['Gold-plated brass, hypoallergenic posts', 'Earrings + necklace set', 'Sits flat under high necklines', 'Arrives in a keepsake box'],
    leadTimeDays: 14, rushAvailable: true, customSizeAvailable: false, installment: false,
    sizeChart: null,
    skus: [{ skuCode: 'JEWEL-PEARL-01', color: 'Pearl White', size: 'One Size', stock: 40 }],
    attributes: { embellishment: ['Pearls'], style_tag: ['Classic', 'Romantic'] },
    images: [
      { url: IMG('birdygrey/accessory-jewelry-01-crop.jpg'), kind: 1, sort: 0 }
    ],
    collections: ['Garden Romance']
  }),
  dress({
    name: 'Getting Ready Silk-Touch Pajama Set', slug: 'getting-ready-silktouch-pajama-set',
    categoryPath: ['Accessories', 'Getting Ready'],
    price: 69, recommend: true,
    color: 'Ivory', fabricMain: 'Satin',
    description: 'The morning-of uniform: a piped ivory satin set with button top and shorts, monogram-ready. Photo-ready from hair-and-makeup to the first look.',
    designerNote: 'Cut generously so it slips over done hair without a fight. Add embroidery locally for a personal touch.',
    sellingPoints: ['Photo-ready piped satin', 'Button top + shorts', 'Hair-and-makeup friendly fit', 'Monogram-friendly left chest panel'],
    leadTimeDays: 14, rushAvailable: true, customSizeAvailable: false,
    sizeChart: [
      { us: 'XS', uk: '6', au: '6', bust: 32, waist: 24, hips: 35, hollowToFloor: null },
      { us: 'S', uk: '8', au: '8', bust: 34, waist: 26, hips: 37, hollowToFloor: null },
      { us: 'M', uk: '10', au: '10', bust: 36, waist: 28, hips: 39, hollowToFloor: null },
      { us: 'L', uk: '12', au: '12', bust: 38, waist: 30, hips: 41, hollowToFloor: null },
      { us: 'XL', uk: '14', au: '14', bust: 40, waist: 32, hips: 43, hollowToFloor: null }
    ],
    skus: [
      { skuCode: 'PJS-IVORY-XS', color: 'Ivory', size: 'XS', stock: 25 },
      { skuCode: 'PJS-IVORY-S', color: 'Ivory', size: 'S', stock: 25 },
      { skuCode: 'PJS-IVORY-M', color: 'Ivory', size: 'M', stock: 25 },
      { skuCode: 'PJS-IVORY-L', color: 'Ivory', size: 'L', stock: 25 },
      { skuCode: 'PJS-IVORY-XL', color: 'Ivory', size: 'XL', stock: 25 }
    ],
    attributes: { style_tag: ['Romantic'] },
    images: [
      { url: IMG('birdygrey/accessory-pjs-02-crop.jpg'), kind: 1, sort: 0 }
    ],
    collections: ['Ivory & Champagne']
  }),
  dress({
    name: 'Petal Flower Girl Dress in Sage', slug: 'petal-flower-girl-dress',
    categoryPath: ['Accessories', 'Flower Girl'],
    price: 79, compareAt: 95,
    color: 'Sage', fabricMain: 'Tulle',
    description: 'A pint-sized tulle party: a soft bodice with a satin sash tied in a bow and a sage tulle skirt with actual twirl. Sizes 2T–10, because the smallest member of the party deserves the biggest spin.',
    designerNote: 'Cotton-lined bodice for sensitive skin, and the sash bow is sewn down at the back so little hands can\'t undo it mid-ceremony.',
    sellingPoints: ['Cotton-lined bodice for sensitive skin', 'Secured satin sash bow — toddler-proof', 'Sage tulle skirt, sizes 2T to 10', 'Machine washable (!)'],
    leadTimeDays: 21, rushAvailable: true, customSizeAvailable: false,
    sizeChart: [
      { us: '2T', uk: '2T', au: '2T', bust: 21, waist: 20, hips: 22, hollowToFloor: null },
      { us: '4', uk: '4', au: '4', bust: 23, waist: 21.5, hips: 24, hollowToFloor: null },
      { us: '6', uk: '6', au: '6', bust: 24.5, waist: 23, hips: 26, hollowToFloor: null },
      { us: '8', uk: '8', au: '8', bust: 26, waist: 24, hips: 28, hollowToFloor: null },
      { us: '10', uk: '10', au: '10', bust: 28, waist: 25.5, hips: 30, hollowToFloor: null }
    ],
    skus: [
      { skuCode: 'FG-PETAL-2T', color: 'Sage', size: '2T', stock: 15 },
      { skuCode: 'FG-PETAL-4', color: 'Sage', size: '4', stock: 15 },
      { skuCode: 'FG-PETAL-6', color: 'Sage', size: '6', stock: 15 },
      { skuCode: 'FG-PETAL-8', color: 'Sage', size: '8', stock: 15 },
      { skuCode: 'FG-PETAL-10', color: 'Sage', size: '10', stock: 15 }
    ],
    attributes: { silhouette: ['A-Line'], fabric: ['Tulle'], embellishment: ['Ruffles'], style_tag: ['Romantic'] },
    images: [
      { url: IMG('birdygrey/lifestyle-flowergirl-08.jpg'), kind: 1, sort: 0 },
      { url: IMG('birdygrey/lifestyle-flowergirl-08-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Sage & Olive']
  })
]
