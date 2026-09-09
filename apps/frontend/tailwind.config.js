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
      },
    },
  },
  plugins: [],
};
