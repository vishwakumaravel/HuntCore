import { copyFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const distDir = resolve(__dirname, "../dist");

await mkdir(distDir, { recursive: true });
await copyFile(resolve(distDir, "index.html"), resolve(distDir, "404.html"));
