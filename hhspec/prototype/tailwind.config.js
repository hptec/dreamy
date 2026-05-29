/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js}'],
  theme: {
    extend: {
      colors: {
        // Editorial ink scale (Ferrari chiaroscuro backbone)
        ink: {
          50: '#F5F4F2',
          100: '#E7E5E1',
          200: '#CCCCCC',
          300: '#A3A3A3',
          400: '#8F8F8F',
          500: '#666666',
          600: '#525252',
          700: '#303030',
          800: '#1C1C1C',
          900: '#181818',
          950: '#0A0A0A',
        },
        // Champagne gold — the single restrained luxury accent (replaces Ferrari red)
        champagne: {
          50: '#FAF6F0',
          100: '#F2E9DC',
          200: '#E6D5BF',
          300: '#D4BC9A',
          400: '#C2A175',
          500: '#B5946A',
          600: '#9A7B54',
          700: '#7D6344',
          800: '#5E4A33',
          900: '#3F3122',
        },
        // Rare dramatic secondary (couture bordeaux) — used extremely sparingly
        bordeaux: {
          500: '#7A2233',
          600: '#641B2A',
        },
        canvas: '#FBFAF8', // warm editorial off-white page surface
      },
      fontFamily: {
        serif: ['"Cormorant Garamond"', 'Georgia', 'Cambria', 'serif'],
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
      },
      letterSpacing: {
        label: '0.18em', // editorial uppercase labels
        wide2: '0.28em',
      },
      borderRadius: {
        DEFAULT: '2px', // razor precision
        sm: '2px',
        md: '3px',
      },
      maxWidth: {
        editorial: '1440px',
      },
      transitionTimingFunction: {
        editorial: 'cubic-bezier(0.22, 1, 0.36, 1)',
      },
      aspectRatio: {
        portrait: '3 / 4',
        gown: '37 / 48',
      },
      keyframes: {
        fadeUp: {
          '0%': { opacity: '0', transform: 'translateY(16px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        kenburns: {
          '0%': { transform: 'scale(1)' },
          '100%': { transform: 'scale(1.08)' },
        },
      },
      animation: {
        fadeUp: 'fadeUp 0.8s cubic-bezier(0.22,1,0.36,1) both',
        fadeIn: 'fadeIn 0.6s ease both',
        kenburns: 'kenburns 12s ease-out both',
      },
    },
  },
  plugins: [],
}
