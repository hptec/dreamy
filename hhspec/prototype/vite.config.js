import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { viteSingleFile } from 'vite-plugin-singlefile'

export default defineConfig({
  // relative base so the static build opens directly via file:// and from any sub-path
  base: './',
  plugins: [
    vue(),
    // inline JS + CSS + fonts into a single index.html so it runs from file:// (double-click)
    viteSingleFile(),
  ],
})
