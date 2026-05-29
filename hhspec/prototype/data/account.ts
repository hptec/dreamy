export interface OrderItem {
  name: string
  image: string
  color: string
  size: string
  qty: number
  price: number
  slug: string
}

export interface Order {
  id: string
  date: string
  status: 'Processing' | 'Shipped' | 'Delivered' | 'In Production'
  total: number
  items: OrderItem[]
  tracking?: { carrier: string; number: string; steps: { label: string; date: string; done: boolean }[] }
}

const C = '/competitor-refs'

export const orders: Order[] = [
  {
    id: '1001',
    date: 'May 18, 2026',
    status: 'Shipped',
    total: 1448,
    items: [
      { name: 'Aurelia A-Line Tulle Gown', image: `${C}/kissprom/wedding-aline-tulle-01.jpg`, color: 'Ivory', size: 'US 4', qty: 1, price: 1280, slug: 'aurelia-gown' },
      { name: 'Estelle Crystal Drop Earrings', image: `${C}/birdygrey/accessory-jewelry-01.jpg`, color: 'Gold', size: 'One Size', qty: 1, price: 68, slug: 'drop-earrings' }
    ],
    tracking: {
      carrier: 'FedEx International',
      number: 'FX-784512369',
      steps: [
        { label: 'Order placed', date: 'May 18', done: true },
        { label: 'In production', date: 'May 19', done: true },
        { label: 'Shipped', date: 'May 24', done: true },
        { label: 'Out for delivery', date: 'Expected May 28', done: false },
        { label: 'Delivered', date: '', done: false }
      ]
    }
  },
  {
    id: '1002',
    date: 'April 30, 2026',
    status: 'Delivered',
    total: 504,
    items: [
      { name: 'Meadow Sage Bridesmaid Dress', image: `${C}/davidsbridal/bridesmaid-sage-01.jpg`, color: 'Sage', size: 'US 6', qty: 3, price: 158, slug: 'meadow-bridesmaid' }
    ],
    tracking: {
      carrier: 'UPS Worldwide',
      number: 'UPS-119283746',
      steps: [
        { label: 'Order placed', date: 'Apr 30', done: true },
        { label: 'Shipped', date: 'May 3', done: true },
        { label: 'Delivered', date: 'May 7', done: true }
      ]
    }
  },
  {
    id: '1003',
    date: 'May 25, 2026',
    status: 'In Production',
    total: 1490,
    items: [
      { name: 'Celeste V-Neck Lace Gown', image: `${C}/kissprom/wedding-aline-lace-02.jpg`, color: 'Ivory', size: 'Custom', qty: 1, price: 1490, slug: 'celeste-lace-gown' }
    ]
  }
]

export function getOrder(id: string) {
  return orders.find((o) => o.id === id)
}

export const addresses = [
  { id: 'a1', name: 'Jane Doe', line1: '123 Coastal Avenue', city: 'Santa Barbara', state: 'CA', zip: '93101', country: 'United States', phone: '+1 (805) 555-0142', default: true },
  { id: 'a2', name: 'Jane Doe', line1: '88 Vineyard Lane', city: 'Sonoma', state: 'CA', zip: '95476', country: 'United States', phone: '+1 (707) 555-0199', default: false }
]
