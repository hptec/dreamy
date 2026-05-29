// Shared navigation structure — consumed by AppHeader, MegaMenu, mobile menu, AppFooter.
import { PRODUCT_IMAGES } from './images.js'

export const MAIN_NAV = [
  { id: 'wedding', labelKey: 'nav.wedding', to: '/wedding-dresses', mega: true },
  { id: 'evening', labelKey: 'nav.evening', to: '/evening-dresses', mega: true },
  { id: 'bridesmaid', labelKey: 'nav.bridesmaid', to: '/bridesmaid-dresses', mega: true },
  { id: 'occasions', labelKey: 'nav.occasions', to: '/special-occasions', mega: true },
  { id: 'accessories', labelKey: 'nav.accessories', to: '/accessories', mega: false },
  { id: 'lookbook', labelKey: 'nav.lookbook', to: '/lookbook/eden-2026', mega: false },
]

export const MEGA = {
  wedding: {
    columns: [
      {
        titleKey: 'nav.shopBySilhouette',
        links: [
          { label: 'A-Line', to: '/wedding-dresses?silhouette=A-Line' },
          { label: 'Ball Gown', to: '/wedding-dresses?silhouette=Ball%20Gown' },
          { label: 'Mermaid', to: '/wedding-dresses?silhouette=Mermaid' },
          { label: 'Sheath', to: '/wedding-dresses?silhouette=Sheath' },
          { label: 'Two-Piece', to: '/wedding-dresses?silhouette=Two-Piece' },
        ],
      },
      {
        titleKey: 'nav.featured',
        links: [
          { label: 'New Arrivals', to: '/wedding-dresses?sort=newest' },
          { label: 'Best Sellers', to: '/wedding-dresses?sort=bestSelling' },
          { label: 'Little White Dresses', to: '/wedding-dresses' },
          { label: 'Plus Size', to: '/wedding-dresses' },
          { label: 'Ships Now', to: '/wedding-dresses?shipsNow=1' },
        ],
      },
      {
        titleKey: 'nav.shopByColor',
        links: [
          { label: 'Diamond White', to: '/wedding-dresses?color=diamond-white' },
          { label: 'Ivory', to: '/wedding-dresses?color=ivory' },
          { label: 'Champagne', to: '/wedding-dresses?color=champagne' },
          { label: 'Blush', to: '/wedding-dresses?color=blush' },
        ],
      },
    ],
    feature: { img: PRODUCT_IMAGES[5], label: 'The 2026 Bridal Edit', to: '/wedding-dresses?sort=newest' },
  },
  evening: {
    columns: [
      {
        titleKey: 'nav.shopBySilhouette',
        links: [
          { label: 'Mermaid', to: '/evening-dresses?silhouette=Mermaid' },
          { label: 'A-Line', to: '/evening-dresses?silhouette=A-Line' },
          { label: 'Sheath', to: '/evening-dresses?silhouette=Sheath' },
          { label: 'Two-Piece', to: '/evening-dresses?silhouette=Two-Piece' },
        ],
      },
      {
        titleKey: 'nav.featured',
        links: [
          { label: 'Cocktail Dresses', to: '/evening-dresses' },
          { label: 'Black Tie', to: '/evening-dresses' },
          { label: 'New Arrivals', to: '/evening-dresses?sort=newest' },
          { label: 'Ships Now', to: '/evening-dresses?shipsNow=1' },
        ],
      },
      {
        titleKey: 'nav.shopByColor',
        links: [
          { label: 'Black', to: '/evening-dresses?color=black' },
          { label: 'Cabernet', to: '/evening-dresses?color=cabernet' },
          { label: 'Emerald', to: '/evening-dresses?color=emerald' },
          { label: 'Navy', to: '/evening-dresses?color=navy' },
        ],
      },
    ],
    feature: { img: PRODUCT_IMAGES[67], label: 'Evening Spotlight', to: '/evening-dresses' },
  },
  bridesmaid: {
    columns: [
      {
        titleKey: 'nav.shopByColor',
        links: [
          { label: 'Dusty Blue', to: '/bridesmaid-dresses?color=dusty-blue' },
          { label: 'Dusty Rose', to: '/bridesmaid-dresses?color=dusty-rose' },
          { label: 'Sage', to: '/bridesmaid-dresses?color=dusty-sage' },
          { label: 'Cabernet', to: '/bridesmaid-dresses?color=cabernet' },
          { label: 'Terracotta', to: '/bridesmaid-dresses?color=terracotta' },
        ],
      },
      {
        titleKey: 'nav.featured',
        links: [
          { label: 'Mix & Match', to: '/bridesmaid-dresses' },
          { label: 'Convertible Styles', to: '/bridesmaid-dresses' },
          { label: 'Junior Bridesmaids', to: '/bridesmaid-dresses' },
          { label: 'Free Swatches', to: '/bridesmaid-dresses' },
        ],
      },
      {
        titleKey: 'nav.shopBySilhouette',
        links: [
          { label: 'A-Line', to: '/bridesmaid-dresses?silhouette=A-Line' },
          { label: 'Sheath', to: '/bridesmaid-dresses?silhouette=Sheath' },
          { label: 'Mermaid', to: '/bridesmaid-dresses?silhouette=Mermaid' },
        ],
      },
    ],
    feature: { img: PRODUCT_IMAGES[42], label: 'Ninety Shades', to: '/bridesmaid-dresses' },
  },
  occasions: {
    columns: [
      {
        titleKey: 'nav.featured',
        links: [
          { label: 'Mother of the Bride', to: '/special-occasions?sub=mother' },
          { label: 'Wedding Guest', to: '/special-occasions?sub=guest' },
          { label: 'Flower Girl', to: '/special-occasions?sub=flowergirl' },
          { label: 'Cocktail & Party', to: '/evening-dresses' },
        ],
      },
      {
        titleKey: 'nav.shopByColor',
        links: [
          { label: 'Navy', to: '/special-occasions?color=navy' },
          { label: 'Champagne', to: '/special-occasions?color=champagne' },
          { label: 'Plum', to: '/special-occasions?color=plum' },
          { label: 'Emerald', to: '/special-occasions?color=emerald' },
        ],
      },
    ],
    feature: { img: PRODUCT_IMAGES[78], label: 'For Every Guest', to: '/special-occasions' },
  },
}

export const FOOTER_NAV = {
  shop: [
    { label: 'Wedding Gowns', to: '/wedding-dresses' },
    { label: 'Evening & Cocktail', to: '/evening-dresses' },
    { label: 'Bridesmaids', to: '/bridesmaid-dresses' },
    { label: 'Mother of the Bride', to: '/special-occasions' },
    { label: 'Accessories', to: '/accessories' },
    { label: 'Lookbook', to: '/lookbook/eden-2026' },
  ],
  care: [
    { label: 'Size Guide', to: '/size-guide' },
    { label: 'Shipping & Returns', to: '/shipping-returns' },
    { label: 'FAQ', to: '/faq' },
    { label: 'Contact Us', to: '/contact' },
    { label: 'Track Your Order', to: '/account/orders' },
    { label: 'Real Brides', to: '/style-gallery' },
  ],
  about: [
    { label: 'Our Story', to: '/about' },
    { label: 'The Atelier', to: '/atelier' },
    { label: 'Sustainability', to: '/about' },
    { label: 'Careers', to: '/about' },
    { label: 'Press', to: '/about' },
  ],
}
