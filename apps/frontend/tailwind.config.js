/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./App.tsx', './src/**/*.{js,jsx,ts,tsx}'],
  presets: [require('nativewind/preset')],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#2F6FED',
          tint: '#E8EFFD',
          dark: '#16294D',
        },
        // Dark theme tokens -- new names, deliberately not touching the
        // primary.tint/dark values above, since those are still used as-is
        // by the auth screens (light theme, not yet converted).
        background: '#0D0D0D',
        surface: '#1C1C1E',
        divider: '#2C2C2E',
        ink: '#F5F5F7',
        subtle: '#9CA3AF',
      },
    },
  },
  plugins: [],
};
