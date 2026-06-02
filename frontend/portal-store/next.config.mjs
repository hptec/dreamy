/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  reactStrictMode: true,
  images: {
    // 原型阶段竞品参考图来自本地 public/，无需远程域名白名单
    unoptimized: true
  }
}

export default nextConfig
