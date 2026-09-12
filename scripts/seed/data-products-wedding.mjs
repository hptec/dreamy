// 婚纱系列(12 款):A-Line/Mermaid/Ballgown/Sheath/Beach,户外场景叙事
// 文案跟图纪律:每款图库 = 全身主图 → -detail 细节裁切 → -skirt 裙摆裁切(如有);文案描述图中真实可见的款式
import { dress, IMG } from './data-products-helpers.mjs'

export const weddingDresses = [
  dress({
    name: 'Elowen Off-the-Shoulder Lace Sleeve Sheath', slug: 'elowen-aline-cold-shoulder-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Garden & Boho'],
    price: 479, compareAt: 559, recommend: false,
    color: 'Ivory', fabricMain: 'Crepe',
    description: 'Illusion off-the-shoulder lace long sleeves over a draped crepe sheath — bare shoulders without going strapless, coverage without losing the line. The crepe is gathered softly across the hip so the silhouette photographs like candlelight, even at noon.',
    designerNote: 'The lace sleeves are set on illusion mesh so they sit just below the shoulder and stay there. The draping is anchored at one hidden seam — it moves, it never shifts.',
    sellingPoints: ['Illusion off-the-shoulder lace long sleeves', 'Draped crepe sheath with hip gathering', 'Bodice with internal boning', 'Sweep train, bustle-ready'],
    leadTimeDays: 56, rushAvailable: true,
    attributes: { silhouette: ['Sheath'], neckline: ['Off-Shoulder'], sleeve: ['Long Sleeve'], fabric: ['Crepe'], embellishment: ['Lace'], back_style: ['Zipper'], train: ['Sweep'], occasion: ['Garden'], style_tag: ['Romantic', 'Modern'] },
    images: [
      { url: IMG('davidsbridal/wedding-dress-04.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/wedding-dress-04-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Garden Romance']
  }),
  dress({
    name: 'Juno Beaded Deep-V Sheath', slug: 'juno-two-piece-wedding-dress-set',
    categoryPath: ['Wedding Dresses', 'Beach & Destination'],
    price: 749, compareAt: 899, recommend: false,
    color: 'Ivory', fabricMain: 'Tulle',
    description: 'Allover beaded embroidery on a deep-V sheath — light from every angle, from the first toast to the last song. The beading is dense at the bodice and softens toward the sweep train so the dress glows rather than glares.',
    designerNote: 'The embroidery is hand-beaded across 60 hours. We set it on a stretch base with a wide, soft waist — no digging, no adjusting, just dancing.',
    sellingPoints: ['Hand-beaded allover embroidery', 'Deep-V neckline with thin straps', 'Fitted sheath with sweep train', 'Lined in silk-touch crepe'],
    leadTimeDays: 77,
    attributes: { silhouette: ['Sheath'], neckline: ['Deep-V'], sleeve: ['Sleeveless'], fabric: ['Tulle'], embellishment: ['Beading', 'Embroidery'], back_style: ['Zipper'], train: ['Sweep'], occasion: ['Beach', 'Garden'], style_tag: ['Glam', 'Modern'] },
    images: [
      { url: IMG('davidsbridal/wedding-dress-set-07.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/wedding-dress-set-07-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Coastal Bride', 'Boho Wildflower']
  }),
  dress({
    name: 'Bea Satin Halter Ballgown', slug: 'bea-satin-ballgown-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Classic Elegance'],
    price: 899, compareAt: 1099, isBest: true,
    color: 'Ivory', fabricMain: 'Satin',
    description: 'A halter deep-V neckline over a clean, full satin ballgown skirt — the once-in-a-lifetime dress, engineered to be worn all day. No lace, no beading: just architecture and shine. The skirt holds its bell shape from first look to last dance.',
    designerNote: 'Satin is unforgiving to cut and irresistible once cut correctly. We built a horsehair hem so the ballgown keeps its architecture — no collapsing at hour ten.',
    sellingPoints: ['Halter neckline with deep V', 'Clean unembellished satin ballgown skirt', 'Horsehair-braid hem holds the bell shape', 'Structured bodice with built-in bra'],
    leadTimeDays: 84, rushAvailable: false,
    attributes: { silhouette: ['Ballgown'], neckline: ['Halter'], sleeve: ['Sleeveless'], fabric: ['Satin'], back_style: ['Zipper'], train: ['Chapel'], support: ['Built-in Bra'], occasion: ['Garden'], style_tag: ['Classic', 'Modern'] },
    images: [
      { url: IMG('davidsbridal/wedding-dress-06.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/wedding-dress-06-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Garden Romance', 'Ivory & Champagne']
  }),
  dress({
    name: 'Dahlia Long Sleeve Beaded Lace Sheath', slug: 'dahlia-fit-flare-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Garden & Boho'],
    price: 549, compareAt: 649,
    color: 'Ivory', fabricMain: 'Lace',
    description: 'Beaded lace long sleeves, a soft V-neckline, and a sheath that reads expensive from every angle. The lace is worked over a fitted base so the beading catches candlelight while the line stays clean — barefoot in the meadow, champagne in hand.',
    designerNote: 'The sleeves are cut from a single stretch-lace panel so they follow your arm instead of fighting it. The beading is densest at the bodice and fades toward the hem — we placed it, twice.',
    sellingPoints: ['Beaded lace long sleeves', 'Soft V-neckline', 'Fitted sheath with sweep train', 'Lined in soft crepe for warm-weather comfort'],
    leadTimeDays: 63, rushAvailable: true,
    attributes: { silhouette: ['Sheath'], neckline: ['V-Neck'], sleeve: ['Long Sleeve'], fabric: ['Lace'], embellishment: ['Beading', 'Lace'], back_style: ['Button'], train: ['Sweep'], occasion: ['Garden', 'Elopement'], style_tag: ['Romantic', 'Vintage'], season: ['Summer'] },
    images: [
      { url: IMG('davidsbridal/wedding-dress-03.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/wedding-dress-03-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Boho Wildflower', 'Modern Minimal']
  }),
  dress({
    name: 'Sylvie Beaded Cap-Sleeve Sheath', slug: 'sylvie-sheath-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Beach & Destination'],
    price: 429, compareAt: 499, recommend: true,
    color: 'Ivory', fabricMain: 'Tulle',
    description: 'One clean line from shoulder to hem, covered in light. The Sylvie is an allover-beaded sheath with cap sleeves and a soft V-neckline — the minimalist\'s answer to "what do I wear to city hall and dinner after," if the minimalist likes to sparkle.',
    designerNote: 'True minimalism hides its effort: the beading is worked on a stretch tulle base so the dress skims and moves — no stiffness, no weight you can feel.',
    sellingPoints: ['Allover beading on soft stretch tulle', 'Cap sleeves with V-neckline', 'Fitted sheath, fully lined', 'Effortless city-hall-to-dinner dress'],
    leadTimeDays: 49, rushAvailable: true,
    attributes: { silhouette: ['Sheath'], neckline: ['V-Neck'], sleeve: ['Cap Sleeve'], fabric: ['Tulle'], embellishment: ['Beading'], back_style: ['Zipper'], train: ['Sweep'], occasion: ['Courthouse', 'Elopement', 'Beach'], style_tag: ['Glam', 'Modern'] },
    images: [
      { url: IMG('davidsbridal/wedding-dress-02.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/wedding-dress-02-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Modern Minimal', 'Coastal Bride']
  }),
  dress({
    name: 'Noelle Lace Ballgown with Illusion Neckline', slug: 'noelle-mermaid-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Classic Elegance'],
    price: 599, compareAt: 699,
    color: 'Ivory', fabricMain: 'Lace',
    description: 'A full lace ballgown with an illusion bateau neckline and sheer lace sleeves — the collarbone framed, the skirt sweeping into a chapel train. Made for the cathedral aisle and the reception grand entrance alike.',
    designerNote: 'We set the lace on illusion mesh at the neckline so it reads as if it floats on skin. The skirt carries a soft petticoat — full without the weight.',
    sellingPoints: ['Illusion bateau neckline', 'Sheer lace sleeves', 'Full lace ballgown skirt with petticoat', 'Chapel train with bustle loops'],
    leadTimeDays: 70,
    attributes: { silhouette: ['Ballgown'], neckline: ['Illusion'], sleeve: ['Long Sleeve'], fabric: ['Lace'], embellishment: ['Lace'], back_style: ['Button'], train: ['Chapel'], support: ['Built-in Bra'], occasion: ['Garden'], style_tag: ['Classic', 'Romantic'] },
    images: [
      { url: IMG('davidsbridal/wedding-dress-08.jpg'), kind: 1, sort: 0 },
      { url: IMG('davidsbridal/wedding-dress-08-detail.jpg'), kind: 1, sort: 1 },
      { url: IMG('davidsbridal/wedding-dress-08-skirt.jpg'), kind: 1, sort: 2 }
    ],
    collections: ['Ivory & Champagne']
  }),
  dress({
    name: 'Cove High-Low Beach Wedding Dress', slug: 'cove-short-beach-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Beach & Destination'],
    price: 299, compareAt: 359, isNew: true, isBest: true,
    color: 'Ivory', fabricMain: 'Organza',
    description: 'Barefoot ceremony, salt in the air, a dress that packs into a carry-on. A strapless corset bodice sits over a bubble-hem organza skirt cut high in front and sweeping long behind — sand-friendly at the toes, dramatic in the photos, and it never met a breeze it didn\'t like.',
    designerNote: 'We flew this prototype to three beaches before signing off. Crisp organza was the only fabric that held the bubble hem in ocean wind and shook the sand right off.',
    sellingPoints: ['Packs into a carry-on, no steaming needed', 'Strapless corset bodice with boning', 'Bubble-hem organza high-low skirt', 'Short in front — sand-friendly'],
    leadTimeDays: 42, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Strapless'], sleeve: ['Sleeveless'], fabric: ['Organza'], length: ['High-Low'], back_style: ['Lace-Up'], train: ['Sweep'], support: ['Built-in Bra'], occasion: ['Beach', 'Elopement'], style_tag: ['Modern', 'Romantic'], season: ['Summer'] },
    images: [
      { url: IMG('kissprom/wedding-beach-short-05.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/wedding-beach-short-05-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Coastal Bride', 'Ivory & Champagne']
  }),
  dress({
    name: 'Wren Off-the-Shoulder Tulle Tea-Length Dress', slug: 'wren-tulle-aline-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Classic Elegance'],
    price: 399, compareAt: 499, isBest: true, recommend: true,
    color: 'Ivory', fabricMain: 'Tulle',
    description: 'Whisper-soft tulle falls from an off-the-shoulder bodice to a tea-length hem that shows off the shoes — and the dancing. The bare-shoulder neckline keeps the silhouette romantic; the skirt does all the talking. Our most requested style for courthouse ceremonies that turn into garden parties.',
    designerNote: 'The off-shoulder band is elasticated and lined so it stays exactly where we put it — bare shoulders, zero tugging, all night.',
    sellingPoints: ['Off-the-shoulder tulle neckline', 'Tea-length A-line skirt — shoe-friendly', 'Weighs under 2 lbs in most sizes', 'Layered soft tulle with full lining'],
    leadTimeDays: 56, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Off-Shoulder'], sleeve: ['Short Sleeve'], fabric: ['Tulle'], length: ['Tea-Length'], back_style: ['Zipper'], train: ['None'], occasion: ['Courthouse', 'Garden'], style_tag: ['Romantic', 'Modern'] },
    images: [
      { url: IMG('kissprom/wedding-aline-tulle-01.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/wedding-aline-tulle-01-detail.jpg'), kind: 1, sort: 1 }
    ],
    collections: ['Modern Minimal', 'Ivory & Champagne']
  }),
  dress({
    name: 'Ingrid Long Sleeve A-Line Wedding Dress', slug: 'ingrid-longsleeve-aline-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Garden & Boho'],
    price: 529, compareAt: 629,
    color: 'Ivory', fabricMain: 'Chiffon',
    description: 'Sheer long sleeves scattered with floral lace appliqué make this the dress for autumn vows and candlelit forests. A soft V-neckline balances the coverage; the chiffon A-line skirt opens at a front slit and moves like water.',
    designerNote: 'The sleeves are cut from a single sheer panel so they skim — never cling — and each appliqué is hand-placed to trail from shoulder to wrist. Coverage without losing an inch of romance.',
    sellingPoints: ['Sheer long sleeves with floral lace appliqué', 'Soft V-neckline', 'Chiffon A-line skirt with front slit', 'Perfect for fall and winter ceremonies'],
    leadTimeDays: 70,
    attributes: { silhouette: ['A-Line'], neckline: ['V-Neck'], sleeve: ['Long Sleeve'], fabric: ['Chiffon'], embellishment: ['Appliqué', 'Lace'], back_style: ['Zipper'], train: ['Sweep'], occasion: ['Forest', 'Garden'], style_tag: ['Romantic', 'Vintage'], season: ['Fall'] },
    images: [
      { url: IMG('kissprom/wedding-aline-longsleeve-06.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/wedding-aline-longsleeve-06-detail.jpg'), kind: 1, sort: 1 },
      { url: IMG('kissprom/wedding-aline-longsleeve-06-skirt.jpg'), kind: 1, sort: 2 }
    ],
    collections: ['Garden Romance']
  }),
  dress({
    name: 'Odette Lace Mermaid Wedding Dress', slug: 'odette-lace-mermaid-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Classic Elegance'],
    price: 649, compareAt: 789, recommend: true,
    color: 'Ivory', fabricMain: 'Lace',
    description: 'Vintage-inspired floral lace over a corseted mermaid base, finished with a sweetheart neckline on thin straps. This is the dress for the bride who wants drama without shouting — every bead is placed by hand, every line deliberate.',
    designerNote: 'The corsetry takes 40 hours per gown. We kept the exterior soft and the interior architectural: you get the curve, the lace does the work.',
    sellingPoints: ['Hand-placed beaded floral lace', 'Interior corset with 14 bones', 'Sweetheart neckline with thin straps', 'Chapel train with lace hem border'],
    leadTimeDays: 84,
    attributes: { silhouette: ['Mermaid'], neckline: ['Sweetheart'], sleeve: ['Sleeveless'], fabric: ['Lace'], back_style: ['Lace-Up'], train: ['Chapel'], embellishment: ['Beading', 'Lace', 'Pearls'], occasion: ['Garden'], style_tag: ['Classic', 'Glam'] },
    images: [
      { url: IMG('kissprom/wedding-mermaid-lace-04.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/wedding-mermaid-lace-04-detail.jpg'), kind: 1, sort: 1 },
      { url: IMG('kissprom/wedding-mermaid-lace-04-skirt.jpg'), kind: 1, sort: 2 }
    ],
    collections: ['Garden Romance']
  }),
  dress({
    name: 'Marisol Crepe Mermaid with Sheer Puff Sleeves', slug: 'marisol-chiffon-mermaid-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Classic Elegance'],
    price: 489, compareAt: 589,
    color: 'Ivory', fabricMain: 'Crepe',
    description: 'A clean crepe mermaid with off-the-shoulder sheer puff sleeves — modern lines, romantic shoulders. The bodice traces every curve before the skirt flares into a chapel train edged in lace. Made for golden-hour portraits on the cliffs.',
    designerNote: 'Traditional mermaids use heavy satin that locks your knees together. We rebuilt the shape in stretch crepe so you can actually walk — and gave it sheer puff sleeves so it still feels like a wedding.',
    sellingPoints: ['Off-the-shoulder sheer puff sleeves', 'Curve-skimming stretch crepe with boning', 'Chapel train with lace-edged hem', 'Dance-floor tested silhouette'],
    leadTimeDays: 63,
    attributes: { silhouette: ['Mermaid'], neckline: ['Off-Shoulder'], sleeve: ['Long Sleeve'], fabric: ['Crepe'], embellishment: ['Lace'], back_style: ['Zipper'], train: ['Chapel'], occasion: ['Vineyard', 'Garden'], style_tag: ['Modern', 'Romantic'] },
    images: [
      { url: IMG('kissprom/wedding-mermaid-chiffon-03.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/wedding-mermaid-chiffon-03-detail.jpg'), kind: 1, sort: 1 },
      { url: IMG('kissprom/wedding-mermaid-chiffon-03-skirt.jpg'), kind: 1, sort: 2 }
    ],
    collections: ['Garden Romance', 'Ivory & Champagne']
  }),
  dress({
    name: 'Aria Lace A-Line Wedding Dress', slug: 'aria-lace-aline-wedding-dress',
    categoryPath: ['Wedding Dresses', 'Garden & Boho'],
    price: 449, compareAt: 549, isNew: true, isBest: true, recommend: true,
    color: 'Ivory', fabricMain: 'Lace',
    description: 'Floral lace traces a plunging deep-V neckline held by the thinnest straps, then flows into an effortless A-line skirt with a high leg slit — cut for the bride who wants to look like herself, only more so. The airy lining keeps every step weightless as you move between ceremony and reception under open sky.',
    designerNote: 'We developed this lace exclusively with a family-run mill. The floral motifs are hand-placed so the pattern reads continuous around the plunge — no visible seams, no shortcuts.',
    sellingPoints: ['Exclusive floral lace with plunging deep-V', 'Thin adjustable spaghetti straps', 'High front leg slit for easy movement', 'Floor-length A-line skirt with sweep train'],
    leadTimeDays: 63, rushAvailable: true,
    attributes: { silhouette: ['A-Line'], neckline: ['Deep-V'], sleeve: ['Sleeveless'], fabric: ['Lace'], back_style: ['Zipper'], train: ['Sweep'], occasion: ['Garden', 'Vineyard'], style_tag: ['Romantic', 'Boho'], embellishment: ['Lace'] },
    images: [
      { url: IMG('kissprom/wedding-aline-lace-02.jpg'), kind: 1, sort: 0 },
      { url: IMG('kissprom/wedding-aline-lace-02-detail.jpg'), kind: 1, sort: 1 },
      { url: IMG('kissprom/wedding-aline-lace-02-skirt.jpg'), kind: 1, sort: 2 }
    ],
    collections: ['Garden Romance', 'Ivory & Champagne']
  })
]
