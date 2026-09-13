import type { Currency } from './types'

export interface NavLink {
  label: string
  href: string
}

export interface MegaColumn {
  title: string
  links: NavLink[]
}

export interface NavItem {
  label: string
  href: string
  columns?: MegaColumn[]
  featured?: { label: string; href: string; image: string }
}

/**
 * 静态导航兜底（site_builder 导航接口为空/失败时使用）。
 *
 * 约束：每条链接必须落到目录里真实存在、且有商品的筛选口径 ——
 * 分类走 /products?cat=<后台分类名>，款式走 a_<attr>=<value>，色系走 collection=<色板集合 id>。
 * 禁止写目录里不存在的伪分类（如 cat=Shoes / cat=A-Line），否则 CollectionPage 会落到空页。
 * 主数据源是 scripts/seed/data-site-nav.mjs（写进 site_builder 的 mega_menu_json），此处与其保持同构。
 */
export const mainNav: NavItem[] = [
  {
    label: 'Wedding Dresses',
    href: '/products?cat=Wedding+Dresses',
    columns: [
      {
        title: 'Shop by Setting',
        links: [
          { label: 'Beach & Destination', href: '/products?cat=Beach+%26+Destination' },
          { label: 'Garden & Boho', href: '/products?cat=Garden+%26+Boho' },
          { label: 'Classic Elegance', href: '/products?cat=Classic+Elegance' }
        ]
      },
      {
        title: 'Shop by Silhouette',
        links: [
          { label: 'A-Line', href: '/products?cat=Wedding+Dresses&a_silhouette=A-Line' },
          { label: 'Sheath', href: '/products?cat=Wedding+Dresses&a_silhouette=Sheath' },
          { label: 'Mermaid', href: '/products?cat=Wedding+Dresses&a_silhouette=Mermaid' },
          { label: 'Ballgown', href: '/products?cat=Wedding+Dresses&a_silhouette=Ballgown' },
          { label: 'Short & Tea-Length', href: '/products?cat=Wedding+Dresses&a_length=Tea-Length%7CHigh-Low' }
        ]
      },
      {
        title: 'Shop by Detail',
        links: [
          { label: 'Long Sleeve', href: '/products?cat=Wedding+Dresses&a_sleeve=Long+Sleeve' },
          { label: 'Lace', href: '/products?cat=Wedding+Dresses&a_embellishment=Lace' },
          { label: 'Beaded', href: '/products?cat=Wedding+Dresses&a_embellishment=Beading' }
        ]
      }
    ],
    featured: { label: 'New Arrivals', href: '/products?cat=Wedding+Dresses&sort=newest', image: '/photography/plp-wedding-dresses.jpg' }
  },
  {
    label: 'Bridesmaids',
    href: '/products?cat=Bridesmaids',
    columns: [
      {
        title: 'Shop by Style',
        links: [
          { label: 'Long Bridesmaid Dresses', href: '/products?cat=Long+Bridesmaid+Dresses' },
          { label: 'Short & Convertible', href: '/products?cat=Short+%26+Convertible' },
          { label: 'One-Shoulder', href: '/products?cat=Bridesmaids&a_neckline=One-Shoulder' },
          { label: 'Cowl Neck', href: '/products?cat=Bridesmaids&a_neckline=Cowl' }
        ]
      },
      {
        title: 'Shop by Color',
        links: [
          { label: 'Blush & Dusty Rose', href: '/products?cat=Bridesmaids&collection=6' },
          { label: 'Sage & Olive', href: '/products?cat=Bridesmaids&collection=7' },
          { label: 'Blue Hues', href: '/products?cat=Bridesmaids&collection=8' },
          { label: 'Black & Espresso', href: '/products?cat=Bridesmaids&collection=9' }
        ]
      }
    ],
    featured: { label: 'Bridesmaid Edit', href: '/products?cat=Bridesmaids', image: '/photography/featured-bridesmaids.jpg' }
  },
  {
    label: 'Occasion & Party',
    href: '/products?cat=Occasion+%26+Party',
    columns: [
      {
        title: 'Shop by Occasion',
        links: [
          { label: 'Prom & Evening', href: '/products?cat=Prom+%26+Evening' },
          { label: 'Ballgowns', href: '/products?cat=Occasion+%26+Party&a_silhouette=Ballgown' },
          { label: 'One-Shoulder', href: '/products?cat=Occasion+%26+Party&a_neckline=One-Shoulder' }
        ]
      },
      {
        title: 'Shop by Fabric',
        links: [
          { label: 'Lace', href: '/products?cat=Occasion+%26+Party&a_fabric=Lace' },
          { label: 'Tulle', href: '/products?cat=Occasion+%26+Party&a_fabric=Tulle' }
        ]
      }
    ],
    featured: { label: 'Party Season', href: '/products?cat=Occasion+%26+Party&sort=newest', image: '/photography/plp-occasion.jpg' }
  },
  {
    label: 'Accessories',
    href: '/products?cat=Accessories',
    columns: [
      {
        title: 'Complete the Look',
        links: [
          { label: 'Jewelry & Headpieces', href: '/products?cat=Jewelry+%26+Headpieces' },
          { label: 'Getting Ready', href: '/products?cat=Getting+Ready' },
          { label: 'Flower Girl', href: '/products?cat=Flower+Girl' }
        ]
      }
    ],
    featured: { label: 'Complete the Look', href: '/products?cat=Accessories', image: '/photography/plp-accessories.jpg' }
  },
  {
    label: 'Real Weddings',
    href: '/real-weddings'
  },
  {
    label: 'The Journal',
    href: '/blog'
  }
]

export const footerNav: MegaColumn[] = [
  {
    title: 'Shop',
    links: [
      { label: 'Wedding Dresses', href: '/products?cat=Wedding+Dresses' },
      { label: 'Bridesmaids', href: '/products?cat=Bridesmaids' },
      { label: 'Occasion & Party', href: '/products?cat=Occasion+%26+Party' },
      { label: 'Accessories', href: '/products?cat=Accessories' },
      { label: 'Outdoor Weddings', href: '/outdoor-weddings' }
    ]
  },
  {
    title: 'Help',
    links: [
      { label: 'Size Guide', href: '/faq#size' },
      { label: 'Shipping & Delivery', href: '/faq#shipping' },
      { label: 'Track Order', href: '/track-order' },
      { label: 'FAQ', href: '/faq' },
      { label: 'Contact Us', href: '/contact' }
    ]
  },
  {
    title: 'Company',
    links: [
      { label: 'About Dreamy', href: '/about' },
      { label: 'The Journal', href: '/blog' },
      { label: 'Real Weddings', href: '/real-weddings' },
      { label: 'Planning Guides', href: '/wedding-guides' }
    ]
  },
  {
    title: 'Account',
    links: [
      { label: 'Sign In', href: '/account/login' },
      { label: 'My Orders', href: '/account/orders' },
      { label: 'Wishlist', href: '/account/wishlist' }
    ]
  }
]

export const currencies: { code: Currency; label: string }[] = [
  { code: 'USD', label: 'USD $' },
  { code: 'EUR', label: 'EUR €' },
  { code: 'CAD', label: 'CAD C$' },
  { code: 'AUD', label: 'AUD A$' },
  { code: 'GBP', label: 'GBP £' }
]

export const languages = [
  { code: 'EN', label: 'English' },
  { code: 'ES', label: 'Español' },
  { code: 'FR', label: 'Français' }
]

export const announcements = [
  'Complimentary worldwide shipping on orders over $200',
  'Pay in 4 interest-free installments with Klarna & Afterpay',
  'Order fabric swatches — try your colors before you commit'
]
