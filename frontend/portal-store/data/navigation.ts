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

const C = '/competitor-refs'

export const mainNav: NavItem[] = [
  {
    label: 'Wedding Dresses',
    href: '/wedding-dresses',
    columns: [
      {
        title: 'Shop by Silhouette',
        links: [
          { label: 'A-Line', href: '/wedding-dresses?silhouette=A-Line' },
          { label: 'Mermaid', href: '/wedding-dresses?silhouette=Mermaid' },
          { label: 'Ball Gown', href: '/wedding-dresses?silhouette=Ball+Gown' },
          { label: 'Sheath', href: '/wedding-dresses?silhouette=Sheath' },
          { label: 'Short', href: '/wedding-dresses?subCategory=Short' }
        ]
      },
      {
        title: 'Shop by Fabric',
        links: [
          { label: 'Tulle', href: '/wedding-dresses?fabric=Tulle' },
          { label: 'Lace', href: '/wedding-dresses?fabric=Lace' },
          { label: 'Chiffon', href: '/wedding-dresses?fabric=Chiffon' },
          { label: 'Satin', href: '/wedding-dresses?fabric=Satin' }
        ]
      }
    ],
    featured: { label: 'New Arrivals', href: '/wedding-dresses?sort=newest', image: `${C}/kissprom/wedding-aline-tulle-01.jpg` }
  },
  {
    label: 'Special Occasion',
    href: '/special-occasion',
    columns: [
      {
        title: 'Shop by Role',
        links: [
          { label: 'Bridesmaid', href: '/special-occasion?occasion=Bridesmaid' },
          { label: 'Mother of the Bride', href: '/special-occasion?occasion=MOB' },
          { label: 'Wedding Guest', href: '/special-occasion?occasion=Guest' },
          { label: 'Prom', href: '/special-occasion?occasion=Prom' },
          { label: 'Cocktail', href: '/special-occasion?occasion=Cocktail' }
        ]
      },
      {
        title: 'Shop by Color',
        links: [
          { label: 'Sage', href: '/special-occasion?color=Sage' },
          { label: 'Dusty Blue', href: '/special-occasion?color=Dusty+Blue' },
          { label: 'Blush', href: '/special-occasion?color=Blush' },
          { label: 'Champagne', href: '/special-occasion?color=Champagne' }
        ]
      }
    ],
    featured: { label: 'Bridesmaid Edit', href: '/special-occasion?occasion=Bridesmaid', image: `${C}/birdygrey/bridesmaid-pink-bella-01.jpg` }
  },
  {
    label: 'Accessories',
    href: '/accessories',
    columns: [
      {
        title: 'Categories',
        links: [
          { label: 'Veils', href: '/accessories?subCategory=Veils' },
          { label: 'Shoes', href: '/accessories?subCategory=Shoes' },
          { label: 'Jewelry', href: '/accessories?subCategory=Jewelry' },
          { label: 'Headpieces', href: '/accessories?subCategory=Headpieces' }
        ]
      }
    ],
    featured: { label: 'Complete the Look', href: '/accessories', image: `${C}/birdygrey/accessory-jewelry-01.jpg` }
  },
  {
    label: 'Outdoor Weddings',
    href: '/outdoor-weddings',
    columns: [
      {
        title: 'Shop by Theme',
        links: [
          { label: 'Beach', href: '/outdoor-weddings?theme=Beach' },
          { label: 'Garden', href: '/outdoor-weddings?theme=Garden' },
          { label: 'Boho', href: '/outdoor-weddings?theme=Boho' },
          { label: 'Forest', href: '/outdoor-weddings?theme=Forest' },
          { label: 'Vineyard', href: '/outdoor-weddings?theme=Vineyard' }
        ]
      }
    ],
    featured: { label: 'Real Outdoor Weddings', href: '/real-weddings', image: `${C}/davidsbridal/wedding-dress-04.jpg` }
  },
  {
    label: 'Inspiration',
    href: '/inspiration',
    columns: [
      {
        title: 'Explore',
        links: [
          { label: 'Lookbook', href: '/inspiration' },
          { label: 'Real Weddings', href: '/real-weddings' },
          { label: 'Wedding Blog', href: '/blog' },
          { label: 'Planning Guides', href: '/wedding-guides' }
        ]
      }
    ]
  }
]

export const footerNav: MegaColumn[] = [
  {
    title: 'Shop',
    links: [
      { label: 'Wedding Dresses', href: '/wedding-dresses' },
      { label: 'Special Occasion', href: '/special-occasion' },
      { label: 'Accessories', href: '/accessories' },
      { label: 'Outdoor Weddings', href: '/outdoor-weddings' }
    ]
  },
  {
    title: 'Help',
    links: [
      { label: 'Size Guide', href: '/faq#size' },
      { label: 'Shipping & Delivery', href: '/faq#shipping' },
      { label: 'FAQ', href: '/faq' },
      { label: 'Contact Us', href: '/contact' }
    ]
  },
  {
    title: 'Company',
    links: [
      { label: 'About Dreamy', href: '/about' },
      { label: 'Wedding Blog', href: '/blog' },
      { label: 'Real Weddings', href: '/real-weddings' },
      { label: 'Planning Guides', href: '/wedding-guides' }
    ]
  },
  {
    title: 'Account',
    links: [
      { label: 'Sign In', href: '/account/login' },
      { label: 'My Orders', href: '/account/orders' },
      { label: 'Wishlist', href: '/account/wishlist' },
      { label: 'Track Order', href: '/account/orders' }
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
