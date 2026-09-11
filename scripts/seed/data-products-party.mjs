// 伴娘系列(9 款,对标 Birdy Grey/Azazie 价格带 $129-169,免费定制尺码)
// 晚装与配饰(8+3 款)
import { dress, IMG } from './data-products-helpers.mjs'

export const bridesmaidDresses = [
  dress({
    name: 'Alex Bridesmaid Dress in Black', slug: 'alex-bridesmaid-dress-black',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 189, compareAt: 229, isBest: true,
    color: 'Black', fabricMain: 'Crepe',
    description: 'The little black dress of bridesmaid dresses: a square neckline, clean crepe, and a slit that says "reception-ready." Your friends will wear this again — that\'s the whole point.',
    designerNote: 'Tested through four weddings and one karaoke night. Crepe with two-way stretch means it fits right out of the box and stays put through the toasts.',
    sellingPoints: ['Two-way stretch crepe', 'Square neck with open back', 'Side slit for easy walking', 'Re-wearable — genuinely'],
    leadTimeDays: 28, rushAvailable: true, customSizeAvailable: true,
    attributes: { silhouette: ['Sheath'], neckline: ['Square'], fabric: ['Crepe'], back_style: ['Open Back'], length: ['Floor'], style_tag: ['Modern', 'Minimalist'] },
    images: [
      { url: IMG('birdygrey/bridesmaid-black-alex-07.jpg'), kind: 1, sort: 0 },
      { url: IMG('birdygrey/pdp-alex-black-model-01.jpg'), kind: 1, sort: 1 },
      { url: IMG('birdygrey/pdp-alex-black-model-02.jpg'), kind: 1, sort: 2 }
    ],
    collections: ['Black & Espresso']
  }),
  dress({
    name: 'Mia Bridesmaid Dress in Espresso', slug: 'mia-bridesmaid-dress-espresso',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 139,
    color: 'Espresso', fabricMain: 'Crepe',
    description: 'A deep espresso brown that flatters every skin tone, in the same beloved crepe as our Alex dress. Cowl neckline in front, criss-cross straps in back.',
    designerNote: 'Espresso replaced black at three of our photoshoot weddings this year — it photographs warmer and pairs with greenery like a dream.',
    sellingPoints: ['Cowl neckline with criss-cross back', 'Deep espresso brown', 'Two-way stretch crepe', 'Pairs beautifully with sage + terracotta palettes'],
    leadTimeDays: 28, rushAvailable: true,
    attributes: { silhouette: ['Sheath'], neckline: ['Halter'], fabric: ['Crepe'], back_style: ['Open Back'], length: ['Floor'], style_tag: ['Classic', 'Modern'] },
    images: [
      { url: IMG('birdygrey/bridesmaid-espresso-mia-05.jpg'), kind: 1, sort: 0 },
      { url: IMG('birdygrey/bridesmaid-black-mia-06.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Black & Espresso']
  }),
  dress({
    name: 'Bella Bridesmaid Dress in Blush', slug: 'bella-bridesmaid-dress-blush',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 139, compareAt: 169, recommend: true,
    color: 'Blush', fabricMain: 'Chiffon',
    description: 'Soft blush chiffon with ruffled off-shoulder straps — the romantic\'s bridesmaid dress. The waist is ruched just enough to be forgiving after the pasta course.',
    designerNote: 'We added a second chiffon layer at the skirt so blush reads pink, not pale, in photos.',
    sellingPoints: ['Ruffled off-shoulder straps', 'Ruched waist — dinner friendly', 'Double-layer blush chiffon', 'Also in lemon for sunshine palettes'],
    leadTimeDays: 28, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Off-Shoulder'], fabric: ['Chiffon'], embellishment: ['Ruffles'], length: ['Floor'], style_tag: ['Romantic'] },
    images: [
      { url: IMG('birdygrey/bridesmaid-pink-bella-01.jpg'), kind: 1, sort: 0 },
      { url: IMG('birdygrey/bridesmaid-lemon-bella-10.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blush & Dusty Rose']
  }),
  dress({
    name: 'Bryten Convertible Bridesmaid Dress', slug: 'bryten-convertible-bridesmaid-dress',
    categoryPath: ['Bridesmaids', 'Short & Convertible'],
    price: 189, compareAt: 229, isBest: true,
    color: 'Dusty Rose', fabricMain: 'Chiffon',
    description: 'One dress, infinite necklines. The Bryten\'s long straps wrap into halter, one-shoulder, or cap-sleeve — every bridesmaid styles it her way, and your photos still match.',
    designerNote: 'We include a printed styling card with six wrap tutorials. It has saved at least one bridal party from a group-chat war.',
    sellingPoints: ['Six+ neckline wrap styles', 'Every bridesmaid can style her own', 'Printed styling card included', 'Matte jersey — no cling, no wrinkle'],
    leadTimeDays: 35, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Halter'], fabric: ['Chiffon'], length: ['Floor'], style_tag: ['Modern', 'Romantic'] },
    images: [
      { url: IMG('birdygrey/bridesmaid-pink-bryten-02.jpg'), kind: 1, sort: 0 },
      { url: IMG('birdygrey/bridesmaid-pink-connie-03.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blush & Dusty Rose']
  }),
  dress({
    name: 'Emmy One-Shoulder Bridesmaid Dress', slug: 'emmy-one-shoulder-bridesmaid-dress',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 159, compareAt: 189, isNew: true,
    color: 'Blush', fabricMain: 'Satin',
    description: 'A one-shoulder satin column with a softly draped bodice — clean lines for modern palettes. The side slit is measured to hit mid-thigh: elegant standing, easy dancing.',
    designerNote: 'The shoulder strap is structured with a hidden stay so it never slides — we promise.',
    sellingPoints: ['One-shoulder with hidden stay', 'Draped satin bodice', 'Mid-thigh side slit', 'Shine-level: soft, not disco'],
    leadTimeDays: 28,
    attributes: { silhouette: ['Sheath'], neckline: ['One-Shoulder'], fabric: ['Satin'], length: ['Floor'], style_tag: ['Modern'] },
    images: [
      { url: IMG('birdygrey/bridesmaid-pink-emmy-09.jpg'), kind: 1, sort: 0 },
      { url: IMG('birdygrey/bridesmaid-pink-danny-08.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blush & Dusty Rose']
  }),
  dress({
    name: 'Sage Bridesmaid Dress with Slit', slug: 'sage-bridesmaid-dress-slit',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 139, compareAt: 165, recommend: true,
    color: 'Sage', fabricMain: 'Chiffon',
    description: 'Garden-party sage green in airy chiffon, with a V-neckline and a thigh slit. The color that made "greenery wedding" a thing, done properly.',
    designerNote: 'Our sage is dyed to sit between grey and green — it works against foliage instead of disappearing into it.',
    sellingPoints: ['True garden sage green', 'V-neck with adjustable tie back', 'Thigh slit lining included', 'Colors match Sage Collection palette'],
    leadTimeDays: 28, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['V-Neck'], fabric: ['Chiffon'], back_style: ['Lace-Up'], length: ['Floor'], style_tag: ['Boho', 'Romantic'], occasion: ['Garden'] },
    images: [
      { url: IMG('davidsbridal/bridesmaid-sage-01.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/bridesmaid-olive-07.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Sage & Olive', 'Garden Romance']
  }),
  dress({
    name: 'Steel Blue Bridesmaid Dress', slug: 'steel-blue-bridesmaid-dress',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 139,
    color: 'Steel Blue', fabricMain: 'Chiffon',
    description: 'Cool steel blue with a strappy open back — the color for coastal and winter palettes alike. Chiffon keeps it light even in the deeper shade.',
    designerNote: 'Designed for a seaside wedding in Maine; re-requested for a mountain wedding in Colorado. That versatile.',
    sellingPoints: ['Cool steel blue, season-flexible', 'Strappy low open back', 'Airy lined chiffon', 'Pairs with silver + dusty blue palettes'],
    leadTimeDays: 28,
    attributes: { silhouette: ['A-Line'], neckline: ['V-Neck'], fabric: ['Chiffon'], back_style: ['Open Back'], length: ['Floor'], style_tag: ['Classic'], occasion: ['Beach'] },
    images: [
      { url: IMG('davidsbridal/bridesmaid-steelblue-02.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/bridesmaid-dustyblue-04.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blue Hues']
  }),
  dress({
    name: 'Coral Bridesmaid Dress', slug: 'coral-bridesmaid-dress',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 139, isNew: true,
    color: 'Coral', fabricMain: 'Chiffon',
    description: 'Sun-warmed coral, made for beach ceremonies and golden light. Sweetheart neckline with a softly gathered waist that forgives everything.',
    designerNote: 'Coral is treacherous — one shade wrong and it fights the bride\'s ivory. Ours is softened with a drop of pink precisely to sit beside white.',
    sellingPoints: ['Beach-approved warm coral', 'Sweetheart neckline with straps', 'Gathered forgiving waist', 'Photographs gold at sunset'],
    leadTimeDays: 28, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Sweetheart'], fabric: ['Chiffon'], length: ['Floor'], style_tag: ['Romantic'], occasion: ['Beach'], season: ['Summer'] },
    images: [
      { url: IMG('davidsbridal/bridesmaid-coral-03.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/bridesmaid-ballet-05.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Coastal Bride']
  }),
  dress({
    name: 'Dusty Blue Bridesmaid Dress with Bow', slug: 'dusty-blue-bridesmaid-dress-bow',
    categoryPath: ['Bridesmaids', 'Long Bridesmaid Dresses'],
    price: 139,
    color: 'Dusty Blue', fabricMain: 'Chiffon',
    description: 'Dusty blue chiffon with an unexpected back bow — the detail that turns a simple silhouette into the one your photographer keeps framing.',
    designerNote: 'The bow is sewn flat at the base so it stays architectural, not fussy. It ships pre-tied.',
    sellingPoints: ['Pre-tied back bow detail', 'Dusty blue — cooler than navy, softer than sky', 'Lined bodice, airy skirt', 'Zipper back hidden under the bow'],
    leadTimeDays: 28,
    attributes: { silhouette: ['A-Line'], neckline: ['Strapless'], fabric: ['Chiffon'], embellishment: ['Ruffles'], back_style: ['Zipper'], length: ['Floor'], style_tag: ['Romantic', 'Classic'] },
    images: [
      { url: IMG('davidsbridal/bridesmaid-petal-06.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/bridesmaid-dustyblue-04.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blue Hues']
  })
]

export const occasionDresses = [
  dress({
    name: 'Cassia One-Shoulder Prom Dress', slug: 'cassia-one-shoulder-prom-dress',
    categoryPath: ['Occasion & Party', 'Prom & Evening'],
    price: 189, compareAt: 229, isBest: true,
    color: 'Sage', fabricMain: 'Crepe',
    description: 'A one-shoulder crepe gown with a leg slit for prom season — or any night that deserves a train. The asymmetric neckline frames the collarbone; the stretch crepe forgives the dessert.',
    designerNote: 'Named after the first customer who wore it to prom and then again, twice, to two weddings. It\'s that dress.',
    sellingPoints: ['Asymmetric one-shoulder neckline', 'Stretch crepe with full lining', 'Thigh-high slit', 'Floor length with mini train'],
    leadTimeDays: 35, rushAvailable: true, customSizeAvailable: true,
    attributes: { silhouette: ['Sheath'], neckline: ['One-Shoulder'], fabric: ['Crepe'], length: ['Floor'], style_tag: ['Modern'], season: ['Spring'] },
    images: [
      { url: IMG('kissprom/prom-sage-oneshoulder-01.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/prom-skyblue-oneshoulder-02.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Sage & Olive']
  }),
  dress({
    name: 'Lux Champagne Lace Prom Dress', slug: 'lux-champagne-lace-prom-dress',
    categoryPath: ['Occasion & Party', 'Prom & Evening'],
    price: 219, compareAt: 269, recommend: true,
    color: 'Champagne', fabricMain: 'Lace',
    description: 'Champagne lace over a smoothing base, with a deep V and an open back. Old-Hollywood energy with a modern fit — the dress that gets two compliments before you\'ve checked your coat.',
    designerNote: 'The lace is placed so the pattern mirrors itself across the V — symmetry you feel even if you never notice it.',
    sellingPoints: ['Mirrored champagne lace placement', 'Deep V with inner support', 'Open back with tie detail', 'Full lining, zero cling'],
    leadTimeDays: 35,
    attributes: { silhouette: ['Mermaid'], neckline: ['Deep-V'], fabric: ['Lace'], back_style: ['Open Back'], embellishment: ['Lace'], length: ['Floor'], style_tag: ['Glam', 'Vintage'] },
    images: [
      { url: IMG('kissprom/prom-champagne-lace-05.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/prom-lavender-lace-09.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Ivory & Champagne']
  }),
  dress({
    name: 'Emerald Lace Evening Gown', slug: 'emerald-lace-evening-gown',
    categoryPath: ['Occasion & Party', 'Prom & Evening'],
    price: 209, compareAt: 249,
    color: 'Emerald', fabricMain: 'Lace',
    description: 'Deep emerald lace with a high neck and long sleeves — the winter-formal answer to "I don\'t want to freeze." Fully lined, softly fitted, quietly knockout.',
    designerNote: 'A test batch survived a Chicago December. Approved.',
    sellingPoints: ['High neck with long sleeves', 'Deep emerald, fully lined', 'Fitted with comfortable stretch', 'Winter-formal approved'],
    leadTimeDays: 35, rushAvailable: true,
    attributes: { silhouette: ['Sheath'], neckline: ['Halter'], sleeve: ['Long Sleeve'], fabric: ['Lace'], length: ['Floor'], style_tag: ['Classic', 'Glam'], season: ['Winter'] },
    images: [
      { url: IMG('kissprom/prom-darkgreen-lace-06.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/homecoming-darkgreen-short-02.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Boho Wildflower']
  }),
  dress({
    name: 'Tiered Off-Shoulder Prom Dress', slug: 'tiered-off-shoulder-prom-dress',
    categoryPath: ['Occasion & Party', 'Prom & Evening'],
    price: 229, compareAt: 279, isNew: true,
    color: 'Blush', fabricMain: 'Tulle',
    description: 'Cascading tiers of soft tulle from an off-shoulder bodice — a dress that takes up exactly the right amount of space on the dance floor. Twirl-tested at 120 BPM.',
    designerNote: 'Six graduated tiers, each 1.5 cm wider than the last. The math of a good twirl.',
    sellingPoints: ['Six-tier cascading tulle skirt', 'Off-shoulder neckline with stays', 'Built-in tulle petticoat', 'Twirl-tested, photographer approved'],
    leadTimeDays: 42, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Off-Shoulder'], fabric: ['Tulle'], embellishment: ['Ruffles'], length: ['Floor'], style_tag: ['Romantic', 'Glam'] },
    images: [
      { url: IMG('kissprom/prom-offshoulder-tiered-07.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/prom-blush-oneshoulder-03.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blush & Dusty Rose']
  }),
  dress({
    name: 'Fleur Floral Sweetheart Prom Dress', slug: 'fleur-floral-sweetheart-prom-dress',
    categoryPath: ['Occasion & Party', 'Prom & Evening'],
    price: 249, compareAt: 299, recommend: true,
    color: 'Floral', fabricMain: 'Tulle',
    description: 'A sweetheart gown in blooms — embroidered florals scatter across champagne tulle, dense at the bodice, drifting toward the hem. Romantic without trying.',
    designerNote: 'Each floral cluster is embroidered separately, then hand-placed during cutting so no two gowns bloom identically.',
    sellingPoints: ['Hand-placed embroidered florals', 'Sweetheart with built-in support', 'Champagne tulle base', 'No two gowns identical'],
    leadTimeDays: 42,
    attributes: { silhouette: ['A-Line'], neckline: ['Sweetheart'], fabric: ['Tulle'], embellishment: ['Embroidery', 'Appliqué'], length: ['Floor'], style_tag: ['Romantic', 'Boho'] },
    images: [
      { url: IMG('kissprom/prom-floral-sweetheart-08.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/prom-lavender-oneshoulder-04.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Garden Romance']
  }),
  dress({
    name: 'Shimmer Sequin Homecoming Dress', slug: 'shimmer-sequin-homecoming-dress',
    categoryPath: ['Occasion & Party', 'Homecoming'],
    price: 189, compareAt: 229, isBest: true,
    color: 'Rose Gold', fabricMain: 'Sequin',
    description: 'Short, sparkling, and seriously comfortable — rose-gold sequins on a stretch base that moves with you from pre-game photos to the last song.',
    designerNote: 'Sequins on knits usually itch. Ours are woven flat onto a two-way stretch mesh — zero scratch, full shimmer.',
    sellingPoints: ['No-itch woven sequins on stretch mesh', 'Rose gold under studio and dance lights', 'Mini length with full lining', 'Pockets — in a sequin dress, yes'],
    leadTimeDays: 28, rushAvailable: true, customSizeAvailable: false,
    attributes: { silhouette: ['A-Line'], neckline: ['Strapless'], fabric: ['Sequin'], embellishment: ['Sequins'], length: ['Short'], style_tag: ['Glam', 'Modern'] },
    images: [
      { url: IMG('kissprom/homecoming-pink-sequin-03.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/homecoming-pink-short-01.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blush & Dusty Rose']
  }),
  dress({
    name: 'Blush One-Shoulder Homecoming Dress', slug: 'blush-one-shoulder-homecoming-dress',
    categoryPath: ['Occasion & Party', 'Homecoming'],
    price: 149, compareAt: 179,
    color: 'Blush', fabricMain: 'Crepe',
    description: 'A blush crepe mini with a one-shoulder neckline — the grown-up homecoming dress. Clean, confident, comfortable in a room full of tulle.',
    designerNote: 'Sometimes the answer is a great crepe and one good shoulder line. This is that.',
    sellingPoints: ['Structured one-shoulder neckline', 'Stretch crepe, fully lined', 'Mini length with side slit', 'Re-wearable beyond the night'],
    leadTimeDays: 28, rushAvailable: true, customSizeAvailable: false,
    attributes: { silhouette: ['Sheath'], neckline: ['One-Shoulder'], fabric: ['Crepe'], length: ['Short'], style_tag: ['Modern', 'Minimalist'] },
    images: [
      { url: IMG('kissprom/prom-blush-oneshoulder-03.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/homecoming-pink-short-01.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Blush & Dusty Rose']
  }),
  dress({
    name: 'Lavender One-Shoulder Prom Dress', slug: 'lavender-one-shoulder-prom-dress',
    categoryPath: ['Occasion & Party', 'Prom & Evening'],
    price: 179,
    color: 'Lavender', fabricMain: 'Satin',
    description: 'Soft lavender satin with a one-shoulder drape and a slit — pastel done with a spine. The drape is sewn to fall in the same three folds every time.',
    designerNote: 'The shoulder drape is interfaced feather-light, so it holds its shape without ever standing away from the body.',
    sellingPoints: ['Sculpted one-shoulder drape', 'Soft lavender satin', 'Secured slit lining', 'Three-fold drape, every wear'],
    leadTimeDays: 35, rushAvailable: true,
    attributes: { silhouette: ['Mermaid'], neckline: ['One-Shoulder'], fabric: ['Satin'], length: ['Floor'], style_tag: ['Modern', 'Romantic'] },
    images: [
      { url: IMG('kissprom/prom-lavender-oneshoulder-04.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/pdp-coldshoulder-lavender-01.jpg'), kind: 1, sort: 1 }
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
    attributes: { fabric: ['Lace'], embellishment: ['Pearls'], style_tag: ['Classic', 'Romantic'] },
    images: [
      { url: IMG('birdygrey/accessory-jewelry-01.jpg'), kind: 1, sort: 0 }
    ],
    collections: ['Garden Romance']
  }),
  dress({
    name: 'Getting Ready Silk-Touch Pajama Set', slug: 'getting-ready-silktouch-pajama-set',
    categoryPath: ['Accessories', 'Wraps & Cover-Ups'],
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
      { url: IMG('birdygrey/accessory-pjs-02.jpg'), kind: 1, sort: 0 }
    ],
    collections: ['Ivory & Champagne']
  }),
  dress({
    name: 'Petal Flower Girl Dress', slug: 'petal-flower-girl-dress',
    categoryPath: ['Accessories', 'Flower Girl'],
    price: 79, compareAt: 95,
    color: 'Ivory', fabricMain: 'Tulle',
    description: 'A pint-sized tulle party: ivory bodice with a satin waist sash and a skirt with actual twirl. Sizes 2T–10, because the smallest member of the party deserves the biggest spin.',
    designerNote: 'Cotton-lined bodice for sensitive skin, and the sash is sewn down at the back so little hands can\'t undo it mid-ceremony.',
    sellingPoints: ['Cotton-lined bodice for sensitive skin', 'Secured satin sash — toddler-proof', 'Sizes 2T to 10', 'Machine washable (!)'],
    leadTimeDays: 21, rushAvailable: true, customSizeAvailable: false,
    sizeChart: [
      { us: '2T', uk: '2T', au: '2T', bust: 21, waist: 20, hips: 22, hollowToFloor: null },
      { us: '4', uk: '4', au: '4', bust: 23, waist: 21.5, hips: 24, hollowToFloor: null },
      { us: '6', uk: '6', au: '6', bust: 24.5, waist: 23, hips: 26, hollowToFloor: null },
      { us: '8', uk: '8', au: '8', bust: 26, waist: 24, hips: 28, hollowToFloor: null },
      { us: '10', uk: '10', au: '10', bust: 28, waist: 25.5, hips: 30, hollowToFloor: null }
    ],
    skus: [
      { skuCode: 'FG-PETAL-2T', color: 'Ivory', size: '2T', stock: 15 },
      { skuCode: 'FG-PETAL-4', color: 'Ivory', size: '4', stock: 15 },
      { skuCode: 'FG-PETAL-6', color: 'Ivory', size: '6', stock: 15 },
      { skuCode: 'FG-PETAL-8', color: 'Ivory', size: '8', stock: 15 },
      { skuCode: 'FG-PETAL-10', color: 'Ivory', size: '10', stock: 15 }
    ],
    attributes: { silhouette: ['A-Line'], fabric: ['Tulle'], embellishment: ['Ruffles'], style_tag: ['Romantic'] },
    images: [
      { url: IMG('birdygrey/lifestyle-flowergirl-08.jpg'), kind: 1, sort: 0 }
    ],
    collections: ['Garden Romance']
  })
]
