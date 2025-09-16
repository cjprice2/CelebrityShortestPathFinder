import { dirname } from "path";
import { fileURLToPath } from "url";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({
  baseDirectory: __dirname,
});

const eslintConfig = [
  // Global ignores - these will be ignored everywhere
  {
    ignores: [
      "**/node_modules/**",
      "**/.next/**",
      "**/out/**", 
      "**/build/**",
      "**/dist/**",
      "src/.next/**",
    ]
  },
  // Extend Next.js ESLint config properly
  ...compat.extends("next/core-web-vitals"),
  // Override to only apply to source files
  {
    files: ["src/**/*.{js,jsx,ts,tsx}"],
    rules: {
      // Keep existing rules from next/core-web-vitals
    }
  }
];

export default eslintConfig;
