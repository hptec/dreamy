/** @type {import('tailwindcss').Config} */
// 与前台 (consumer Next.js) 同源设计 token —— 品牌视觉一致，editorial-luxe-coastal 风格
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts}'],
  theme: {
    extend: {
      colors: {
        canvas: '#FAF7F2',
        'canvas-warm': '#F3ECE2',
        ink: { DEFAULT: '#2B2925', soft: '#5C574F', faint: '#9A9389' },
        gold: { DEFAULT: '#C19A6B', soft: '#D4B896', deep: '#A67C4E' },
        sage: { DEFAULT: '#8B9D83', soft: '#A8B6A1' },
        blush: { DEFAULT: '#D8A7A0', soft: '#E8C9C4' },
        line: '#E5DDD1',
        // 后台专用：深色侧边栏与状态色
        sidebar: { DEFAULT: '#23211C', hover: '#2F2C25', active: '#3A352B' },
        ok: '#5C8A5A',
        warn: '#C99A3E',
        danger: '#B5564E',
        info: '#5E7A9B'
      },
      fontFamily: {
        display: ['Cormorant Garamond', 'Georgia', 'serif'],
        sans: ['Jost', 'system-ui', 'sans-serif']
      },
      letterSpacing: { luxe: '0.18em' },
      boxShadow: {
        soft: '0 2px 12px rgba(43, 41, 37, 0.06)',
        card: '0 8px 30px rgba(43, 41, 37, 0.10)',
        panel: '0 1px 3px rgba(43, 41, 37, 0.08)'
      },
      borderRadius: { luxe: '2px' },
      keyframes: {
        fadeup: { '0%': { opacity: '0', transform: 'translateY(12px)' }, '100%': { opacity: '1', transform: 'translateY(0)' } },
        shimmer: { '0%': { backgroundPosition: '-400px 0' }, '100%': { backgroundPosition: '400px 0' } }
      },
      animation: {
        fadeup: 'fadeup 0.5s ease-out both',
        shimmer: 'shimmer 1.4s linear infinite'
      }
    }
  },
  plugins: []
}
