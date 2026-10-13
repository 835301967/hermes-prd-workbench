import JSZip from "jszip";

export type DemoPackageFile = {
  path: string;
  content: string;
};

export type DemoPackageFiles = {
  files: DemoPackageFile[];
  rejected: string[];
};

const SENSITIVE_FILE_NAME = /^(?:\.env(?:\..*)?|\.npmrc|credentials.*|secret.*|id_rsa.*|node_modules|dist|build|coverage|\.git|\.ds_store|\.vite|target|__pycache__|.*\.(?:pem|key|map))$/i;
const SECRET_ASSIGNMENT = /["']?(?:api[_-]?key|access[_-]?token|auth(?:orization)?|bearer|client[_-]?secret|database[_-]?(?:password|url)|(?:db|mysql|redis)[_-]?password|password|token)["']?\s*[:=]\s*(?:["'`]\s*(?!\$\{|YOUR_|CHANGE_ME|REPLACE_|<)[^"'`\r\n]{8,}["'`]|(?!\$\{|YOUR_|CHANGE_ME|REPLACE_|<)(?:Bearer\s+)?[^\s,#\r\n]{8,})/i;
const SECRET_MARKER = /-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----/i;

export function isSafePackagePath(path: string) {
  if (!path || path.length > 512 || path.includes("\\") || path.startsWith("/") || /^[A-Za-z]:/.test(path)) {
    return false;
  }
  const parts = path.split("/");
  return parts.every((part) => part.length > 0 && part !== "." && part !== ".." && !SENSITIVE_FILE_NAME.test(part));
}

function isSafePackageContent(content: string) {
  return !content.includes("\u0000") && !SECRET_ASSIGNMENT.test(content) && !SECRET_MARKER.test(content);
}

function packagePriority(path: string) {
  if (path === "README.md") return -4;
  if (/^\d{2}-/.test(path)) return -3;
  if (path.startsWith("modules/")) return -2;
  if (path === "open-questions.md") return -1;
  if (path === "index.html") return 0;
  if (path === "css/app.css") return 1;
  if (path === "js/app.js") return 2;
  if (path === "js/router.js") return 3;
  if (path === "js/store.js") return 4;
  if (path.startsWith("js/views/")) return 5;
  if (path === "data/catalog.json") return 6;
  if (path === "data/mock.json") return 7;
  if (path === "application.json") return 8;
  if (path === "package.json") return 9;
  if (path === "vite.config.ts") return 10;
  if (path === "main.ts") return 11;
  if (path === "mainH5.ts") return 12;
  if (path.endsWith("/index.ts")) return 13;
  if (path.endsWith(".vue")) return 14;
  if (path.endsWith(".scss")) return 15;
  if (path.endsWith(".test.ts") || path.endsWith(".spec.ts")) return 16;
  return 17;
}

export function normalizeDemoPackageFiles(raw: unknown): DemoPackageFiles {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    return { files: [], rejected: raw === undefined ? [] : ["package_files"] };
  }

  const files: DemoPackageFile[] = [];
  const rejected: string[] = [];
  for (const [path, value] of Object.entries(raw)) {
    if (typeof value !== "string" || !isSafePackagePath(path) || !isSafePackageContent(value)) {
      rejected.push(path || "<empty>");
      continue;
    }
    files.push({ path, content: value });
  }
  files.sort((left, right) => packagePriority(left.path) - packagePriority(right.path) || left.path.localeCompare(right.path));
  return { files, rejected };
}

export function packageRootName(title: string, prefix = "hermes-demo") {
  const normalized = (title || "package")
    .trim()
    .replace(/[\\/:*?"<>|]+/g, "-")
    .replace(/\s+/g, "-")
    .replace(/[^a-zA-Z0-9_\-\u4e00-\u9fff]/g, "-")
    .replace(/-{2,}/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 72);
  return `${prefix}-${normalized || "package"}`;
}

export async function buildDemoPackageZip(title: string, files: DemoPackageFile[], prefix = "hermes-demo") {
  const root = packageRootName(title, prefix);
  const zip = new JSZip();
  for (const file of files) {
    // Re-check before creating the archive because this function is also useful outside the view.
    if (isSafePackagePath(file.path) && isSafePackageContent(file.content)) {
      zip.file(`${root}/${file.path}`, file.content);
    }
  }
  return zip.generateAsync({ type: "blob", compression: "DEFLATE" });
}
