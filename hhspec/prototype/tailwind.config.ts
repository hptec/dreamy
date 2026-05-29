import type { Config } from 'tailwindcss'

const config: Config = {
  content: [
    './app/**/*.{ts,tsx}',
    './components/**/*.{ts,tsx}'
  ],
  theme: {
    extend: {
      colors: {
        canvas: '#FAF7F2',
        surface: '#FFFFFF',
        ink: {
          DEFAULT: '#2B2925',
          soft: '#6B5D4F',
          faint: '#9A8E7E'
        },
        gold: {
          DEFAULT: '#C19A6B',
          deep: '#A07E52',
          light: '#D4A574'
        },
        sage: {
          DEFAULT: '#8B9D83',
          deep: '#6B7D63',
          light: '#A8B89F'
        },
        blush: {
          DEFAULT: '#D8A7A0',
          light: '#EBD3CE'
        },
        line: '#E7DFD3',
        muted: '#F0EBE3'
      },
      fontFamily: {
        display: ['var(--font-display)', 'Georgia', 'serif'],
        sans: ['var(--font-sans)', 'ui-sans-serif', 'system-ui', 'sans-serif']
      },
      letterSpacing: {
        luxe: '0.18em',
        wide2: '0.28em'
      },
      boxShadow: {
        soft: '0 1px 3px rgba(43,41,37,0.05)',
        card: '0 8px 30px rgba(43,41,37,0.07)',
        lift: '0 18px 50px rgba(43,41,37,0.12)'
      },
      borderRadius: {
        xl2: '1.25rem'
      },
      transitionTimingFunction: {
        luxe: 'cubic-bezier(0.22, 1, 0.36, 1)'
      },
      maxWidth: {
        '8xl': '88rem'
      },
      keyframes: {
        fadeup: {
          '0%': { opacity: '0', transform: 'translateY(16px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' }
        },
        kenburns: {
          '0%': { transform: 'scale(1)' },
          '100%': { transform: 'scale(1.08)' }
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' }
        }
      },
      animation: {
        fadeup: 'fadeup 0.8s cubic-bezier(0.22,1,0.36,1) both',
        kenburns: 'kenburns 14s ease-out both',
        shimmer: 'shimmer 1.8s linear infinite'
      }
    }
  },
  plugins: []
}

export default config
