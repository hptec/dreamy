// 婚纱系列(12 款):A-Line/Mermaid/Ballgown/Sheath/Beach,户外场景叙事
import { dress, IMG } from './data-products-helpers.mjs'

export const weddingDresses = [
  dress({
    name: 'Aria Lace A-Line Wedding Dress', slug: 'aria-lace-aline-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Garden & Boho'],
    price: 449, compareAt: 549, isNew: true, isBest: true, recommend: true,
    color: 'Ivory', fabricMain: 'Lace',
    description: 'Scalloped floral lace traces a soft sweetheart neckline before flowing into an effortless A-line skirt — cut for the bride who wants to look like herself, only more so. The airy tulle underskirt keeps every step weightless as you move between ceremony and reception under open sky.',
    designerNote: 'We developed this lace exclusively with a family-run mill. The scallop edges are hand-placed so the pattern reads continuous around the bodice — no visible seams, no shortcuts.',
    sellingPoints: ['Exclusive scalloped floral lace', 'Detachable satin waist sash included', 'Fully boned bodice with built-in bra', 'Floor-length A-line skirt with sweep train'],
    leadTimeDays: 63, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Sweetheart'], fabric: ['Lace'], back_style: ['Zipper'], train: ['Sweep'], occasion: ['Garden', 'Vineyard'], style_tag: ['Romantic', 'Boho'], embellishment: ['Lace'] },
    images: [
      { url: IMG('kissprom/wedding-aline-lace-02.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/lifestyle-banner-bridal-02.jpg'), kind: 2, sort: 0 }
    ],
    collections: ['Garden Romance', 'Ivory & Champagne']
  }),
  dress({
    name: 'Wren Tulle A-Line Wedding Dress', slug: 'wren-tulle-aline-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Classic Elegance'],
    price: 399, compareAt: 499, isBest: true, recommend: true,
    color: 'Ivory', fabricMain: 'Tulle',
    description: 'Layers upon layers of whisper-soft tulle fall from a structured satin bodice, catching the light with every turn. A modern square neckline keeps the silhouette clean; the skirt does all the talking. Our most requested style for courthouse ceremonies that turn into garden parties.',
    designerNote: 'Eleven individually cut tulle layers give the skirt its cloud-like movement without the weight — we tested until the dress danced on its own.',
    sellingPoints: ['Eleven-layer airy tulle skirt', 'Structured satin bodice, square neckline', 'Weighs under 2 lbs in most sizes', 'Pockets. Real, deep, dress-with-pockets.'],
    leadTimeDays: 56, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Square'], fabric: ['Tulle'], back_style: ['Button'], train: ['Sweep'], occasion: ['Courthouse', 'Garden'], style_tag: ['Modern', 'Minimalist'] },
    images: [
      { url: IMG('kissprom/wedding-aline-tulle-01.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/lifestyle-banner-bridal-02.jpg'), kind: 2, sort: 1 }
    ],
    collections: ['Modern Minimal', 'Ivory & Champagne']
  }),
  dress({
    name: 'Ingrid Long Sleeve A-Line Wedding Dress', slug: 'ingrid-longsleeve-aline-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Garden & Boho'],
    price: 529, compareAt: 629,
    color: 'Ivory', fabricMain: 'Lace',
    description: 'Illusion long sleeves in heritage-inspired lace make this the dress for autumn vows and candlelit forests. A gently scooped neckline and low open back balance the coverage; the A-line skirt in soft crepe moves like water.',
    designerNote: 'The sleeves are cut from a single stretch-lace panel so they skim — never cling. Designed for brides who want coverage without losing an inch of romance.',
    sellingPoints: ['Illusion stretch-lace long sleeves', 'Low scoop back with covered buttons', 'Soft crepe A-line skirt', 'Perfect for fall and winter ceremonies'],
    leadTimeDays: 70,
    attributes: { silhouette: ['A-Line'], neckline: ['V-Neck'], sleeve: ['Long Sleeve'], fabric: ['Lace'], back_style: ['Open Back'], train: ['Sweep'], occasion: ['Forest', 'Garden'], style_tag: ['Romantic', 'Vintage'], season: ['Fall'] },
    images: [
      { url: IMG('kissprom/wedding-aline-longsleeve-06.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/lifestyle-banner-bridal-02.jpg'), kind: 2, sort: 2 }
    ],
    collections: ['Garden Romance']
  }),
  dress({
    name: 'Cove Short Beach Wedding Dress', slug: 'cove-short-beach-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Beach & Destination'],
    price: 299, compareAt: 359, isNew: true, isBest: true,
    color: 'Ivory', fabricMain: 'Chiffon',
    description: 'Barefoot ceremony, salt in the air, a dress that packs into a carry-on. This short chiffon number hits mid-calf with the softest flutter sleeves — it dries in minutes if the tide comes in, and it never met a breeze it didn\'t like.',
    designerNote: 'We flew this prototype to three beaches before signing off. Chiffon was the only fabric that stayed graceful in ocean wind and shook the sand right off.',
    sellingPoints: ['Packs into a carry-on, no steaming needed', 'Quick-dry wrinkle-resistant chiffon', 'Flutter sleeves with open neckline', 'Mid-calf hem — sand-friendly'],
    leadTimeDays: 42, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Off-Shoulder'], sleeve: ['Short Sleeve'], fabric: ['Chiffon'], length: ['Tea-Length'], train: ['None'], occasion: ['Beach', 'Elopement'], style_tag: ['Boho', 'Minimalist'], season: ['Summer'] },
    images: [
      { url: IMG('kissprom/wedding-beach-short-05.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/lifestyle-banner-bridal-02.jpg'), kind: 2, sort: 3 }
    ],
    collections: ['Coastal Bride', 'Ivory & Champagne']
  }),
  dress({
    name: 'Marisol Chiffon Mermaid Wedding Dress', slug: 'marisol-chiffon-mermaid-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Classic Elegance'],
    price: 489, compareAt: 589,
    color: 'Ivory', fabricMain: 'Chiffon',
    description: 'A mermaid silhouette in featherlight chiffon — the paradox that works. The bodice traces every curve before the skirt flares into a soft, swishable train. Made for golden-hour portraits on the cliffs.',
    designerNote: 'Traditional mermaids use heavy satin that locks your knees together. We rebuilt the shape in layered chiffon so you can actually walk — and dance.',
    sellingPoints: ['Curve-skimming bodice with boning', 'Flared chiffon train with real movement', 'Surplice neckline with inner corset', 'Dance-floor tested silhouette'],
    leadTimeDays: 63,
    attributes: { silhouette: ['Mermaid'], neckline: ['V-Neck'], fabric: ['Chiffon'], back_style: ['Zipper'], train: ['Chapel'], occasion: ['Vineyard', 'Garden'], style_tag: ['Modern', 'Romantic'] },
    images: [
      { url: IMG('kissprom/wedding-mermaid-chiffon-03.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/lifestyle-banner-bridal-02.jpg'), kind: 2, sort: 4 }
    ],
    collections: ['Garden Romance', 'Ivory & Champagne']
  }),
  dress({
    name: 'Odette Lace Mermaid Wedding Dress', slug: 'odette-lace-mermaid-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Classic Elegance'],
    price: 649, compareAt: 789, recommend: true,
    color: 'Ivory', fabricMain: 'Lace',
    description: 'Vintage-inspired floral lace over a corseted mermaid base, finished with a deep sweetheart neckline. This is the dress for the bride who wants drama without shouting — every bead is placed by hand, every line deliberate.',
    designerNote: 'The corsetry takes 40 hours per gown. We kept the exterior soft and the interior architectural: you get the curve, the lace does the work.',
    sellingPoints: ['Hand-placed beaded floral lace', 'Interior corset with 14 bones', 'Deep sweetheart neckline', 'Chapel train with lace hem border'],
    leadTimeDays: 84,
    attributes: { silhouette: ['Mermaid'], neckline: ['Sweetheart'], fabric: ['Lace'], back_style: ['Lace-Up'], train: ['Chapel'], embellishment: ['Beading', 'Lace', 'Pearls'], occasion: ['Garden'], style_tag: ['Classic', 'Glam'] },
    images: [
      { url: IMG('kissprom/wedding-mermaid-lace-04.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/lifestyle-banner-bridal-02.jpg'), kind: 2, sort: 5 }
    ],
    collections: ['Garden Romance']
  }),
  dress({
    name: 'Bea Satin Ballgown Wedding Dress', slug: 'bea-satin-ballgown-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Classic Elegance'],
    price: 899, compareAt: 1099, isBest: true,
    color: 'Ivory', fabricMain: 'Satin',
    description: 'Full-skirted Mikado satin with a cathedral train — the once-in-a-lifetime dress, engineered to be worn all day. The bodice is a proper corset (lace-up back, modesty panel included) and the skirt holds its bell shape from first look to last dance.',
    designerNote: 'Mikado is unforgiving to cut and irresistible once cut correctly. We built a horsehair hem so the ballgown keeps its architecture — no collapsing at hour ten.',
    sellingPoints: ['Cathedral train in structured Mikado satin', 'Lace-up corset back with modesty panel', 'Horsehair-braid hem holds the bell shape', 'Detachable train option available'],
    leadTimeDays: 84, rushAvailable: false,
    attributes: { silhouette: ['Ballgown'], neckline: ['Strapless'], fabric: ['Mikado'], back_style: ['Lace-Up'], train: ['Cathedral'], support: ['Built-in Bra'], occasion: ['Garden'], style_tag: ['Classic', 'Glam'] },
    images: [
      { url: IMG('davidsbridal/wedding-dress-06.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/lifestyle-banner-bridal-02.jpg'), kind: 2, sort: 6 }
    ],
    collections: ['Garden Romance', 'Ivory & Champagne']
  }),
  dress({
    name: 'Dahlia Fit & Flare Wedding Dress', slug: 'dahlia-fit-flare-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Garden & Boho'],
    price: 549, compareAt: 649,
    color: 'Ivory', fabricMain: 'Crepe',
    description: 'Fluid crepe wrapped into a fit-and-flare that reads expensive from every angle. The high neckline and covered buttons down the spine give it a quiet 1970s spirit — barefoot in the meadow, champagne in hand.',
    designerNote: 'Cut on the bias so the crepe follows your body instead of fighting it. The flare begins exactly at the knee — we measured, twice.',
    sellingPoints: ['Bias-cut fluid crepe', 'High neckline, covered back buttons', 'Fit & flare with knee-length flare point', 'Unlined for warm-weather comfort'],
    leadTimeDays: 63, rushAvailable: true,
    attributes: { silhouette: ['Fit & Flare'], neckline: ['Halter'], fabric: ['Crepe'], back_style: ['Button'], train: ['Sweep'], occasion: ['Garden', 'Elopement'], style_tag: ['Boho', 'Vintage'], season: ['Summer'] },
    images: [
      { url: IMG('davidsbridal/wedding-dress-03.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/lifestyle-banner-bridal-02.jpg'), kind: 2, sort: 7 }
    ],
    collections: ['Boho Wildflower', 'Modern Minimal']
  }),
  dress({
    name: 'Elowen A-Line Wedding Dress with Cold Shoulder', slug: 'elowen-aline-cold-shoulder-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Garden & Boho'],
    price: 479, compareAt: 559, isNew: true,
    color: 'Ivory', fabricMain: 'Tulle',
    description: 'A cold-shoulder neckline in soft tulle and glitter mesh — bare shoulders without going strapless. The A-line skirt catches light in a way that photographs like candlelight, even at noon.',
    designerNote: 'The glitter mesh is woven, not printed — it will not shed on your dress, your groom, or your grandmother\'s antique sofa.',
    sellingPoints: ['Cold-shoulder sleeves with glitter mesh', 'Woven (non-shedding) sparkle tulle', 'Bodice with internal boning', 'Sweep train, bustle-ready'],
    leadTimeDays: 56, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Off-Shoulder'], sleeve: ['Short Sleeve'], fabric: ['Tulle'], embellishment: ['Sequins'], back_style: ['Zipper'], train: ['Sweep'], occasion: ['Garden'], style_tag: ['Romantic', 'Glam'] },
    images: [
      { url: IMG('davidsbridal/wedding-dress-04.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/pdp-coldshoulder-lavender-01.jpg'), kind: 2, sort: 0 }
    ],
    collections: ['Garden Romance']
  }),
  dress({
    name: 'Sylvie Sheath Wedding Dress', slug: 'sylvie-sheath-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Beach & Destination'],
    price: 429, compareAt: 499, recommend: true,
    color: 'Ivory', fabricMain: 'Crepe',
    description: 'One clean line from shoulder to hem. The Sylvie is a crepe sheath with a plunge neckline and an open square back — the minimalist\'s answer to "what do I wear to city hall and dinner after."',
    designerNote: 'True minimalism hides its effort: three hidden panels shape the waist without a single visible seam.',
    sellingPoints: ['Seamless crepe sheath silhouette', 'Plunge V neckline, open back', 'Hidden waist-shaping panels', 'Effortless city-hall-to-dinner dress'],
    leadTimeDays: 49, rushAvailable: true,
    attributes: { silhouette: ['Sheath'], neckline: ['Deep-V'], fabric: ['Crepe'], back_style: ['Open Back'], train: ['None'], occasion: ['Courthouse', 'Elopement', 'Beach'], style_tag: ['Minimalist', 'Modern'] },
    images: [
      { url: IMG('davidsbridal/wedding-dress-05.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/lifestyle-banner-bridal-02.jpg'), kind: 2, sort: 8 }
    ],
    collections: ['Modern Minimal', 'Coastal Bride']
  }),
  dress({
    name: 'Noelle Mermaid Wedding Dress', slug: 'noelle-mermaid-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Classic Elegance'],
    price: 599, compareAt: 699,
    color: 'Ivory', fabricMain: 'Satin',
    description: 'Satin mermaid with a sculpted bodice and a flirtatious flared train. The off-shoulder neckline frames the collarbone; the fit does everything else. Made for the reception grand entrance.',
    designerNote: 'We added a hidden back pleat so the flare opens when you walk — it reads dramatic in photos and stays walkable in real life.',
    sellingPoints: ['Sculpted satin bodice with boning', 'Off-shoulder neckline', 'Hidden walking pleat for mobility', 'Chapel train with bustle loops'],
    leadTimeDays: 70,
    attributes: { silhouette: ['Mermaid'], neckline: ['Off-Shoulder'], fabric: ['Satin'], back_style: ['Zipper'], train: ['Chapel'], support: ['Built-in Bra'], occasion: ['Garden'], style_tag: ['Classic', 'Glam'] },
    images: [
      { url: IMG('davidsbridal/wedding-dress-08.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/lifestyle-banner-bridal-02.jpg'), kind: 2, sort: 9 }
    ],
    collections: ['Ivory & Champagne']
  }),
  dress({
    name: 'Juno Two-Piece Wedding Dress Set', slug: 'juno-two-piece-wedding-dress-set',
    categoryPath: ['Wedding Dresses', 'Beach & Destination'],
    price: 749, compareAt: 899, isNew: true,
    color: 'Ivory', fabricMain: 'Lace',
    description: 'A beaded lace crop top with a flowing high-waist skirt — wear them together for the ceremony, swap the top for the after-party. Two pieces, three outfits, one very sensible bride.',
    designerNote: 'The top is hand-beaded across 60 hours. The skirt waistband is wide and soft — no digging, no adjusting, just dancing.',
    sellingPoints: ['Hand-beaded lace crop top', 'High-waist flowing tulle skirt', 'Separates: restyle for the after-party', 'Both pieces lined in silk-touch crepe'],
    leadTimeDays: 77,
    attributes: { silhouette: ['A-Line'], neckline: ['Strapless'], fabric: ['Lace'], embellishment: ['Beading', 'Pearls'], train: ['Detachable'], occasion: ['Beach', 'Garden'], style_tag: ['Modern', 'Boho'] },
    images: [
      { url: IMG('davidsbridal/wedding-dress-set-07.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/lifestyle-banner-bridal-02.jpg'), kind: 2, sort: 10 }
    ],
    collections: ['Coastal Bride', 'Boho Wildflower']
  })
]
