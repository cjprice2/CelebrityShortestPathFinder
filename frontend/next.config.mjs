/** @type {import('next').NextConfig} */
const isGhPages = process.env.GITHUB_PAGES === 'true';
const repoName = 'CelebrityShortestPathFinder';

const nextConfig = {
  output: 'standalone', // Creates a minimal server bundle
  images: { unoptimized: true },
  // Only needed if deploying to project pages (username.github.io/repo)
  basePath: isGhPages ? `/${repoName}` : undefined,
  assetPrefix: isGhPages ? `/${repoName}/` : undefined,
  experimental: {},
};

export default nextConfig;