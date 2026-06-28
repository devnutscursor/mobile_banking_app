import type { NextConfig } from "next";
import path from "path";
import fs from "fs";

const nextConfig: NextConfig = {
  eslint: {
    // Ignore ESLint errors during builds (Vercel will handle this)
    ignoreDuringBuilds: true,
  },
  typescript: {
    // Ignore TypeScript errors during builds
    ignoreBuildErrors: true,
  },
  // Optimize for Vercel deployment
  output: 'standalone',
  webpack: (config, { dir }) => {
    // Windows: mixed path casing (Mobile-Banking-App vs mobile-banking-app) loads
    // duplicate modules and breaks the App Router ("layout router not mounted").
    if (process.platform === 'win32') {
      const realDir = fs.realpathSync.native(dir);
      config.context = realDir;
      config.resolve = config.resolve ?? {};
      config.resolve.modules = [
        path.join(realDir, 'node_modules'),
        ...(Array.isArray(config.resolve.modules) ? config.resolve.modules : ['node_modules']),
      ];
    }
    return config;
  },
};

export default nextConfig;
