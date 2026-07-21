import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    // App.vue 中的 ShopCard 使用内联 template，需要包含运行时编译器的 Vue 构建版本。
    alias: {
      vue: "vue/dist/vue.esm-bundler.js"
    }
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8110",
        changeOrigin: true
      },
      "/mcp": {
        target: "http://localhost:8110",
        changeOrigin: true
      },
      "/actuator": {
        target: "http://localhost:8110",
        changeOrigin: true
      }
    }
  }
});
