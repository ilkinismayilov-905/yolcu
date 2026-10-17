import type { NextConfig } from 'next';

// NEXT_OUTPUT=export → static site in web/out, served by Spring Boot on :8080
const isExport = process.env.NEXT_OUTPUT === 'export';

const nextConfig: NextConfig = {
  transpilePackages: ['@yolcu/shared'],
  ...(isExport && { output: 'export', trailingSlash: true, images: { unoptimized: true } }),
};

export default nextConfig;
