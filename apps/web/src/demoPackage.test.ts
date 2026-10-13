import { describe, expect, it } from "vitest";
import JSZip from "jszip";
import { buildDemoPackageZip, isSafePackagePath, normalizeDemoPackageFiles, packageRootName } from "./demoPackage";

describe("demo package files", () => {
  it("sorts important scaffold files before the business tree", () => {
    const result = normalizeDemoPackageFiles({
      "src/ui/pc/pages/inventory/views/list/index.vue": "<template />",
      "package.json": "{}",
      "application.json": "{}",
      "src/ui/pc/pages/inventory/index.ts": "export default {}",
      "src/ui/pc/pages/inventory/views/list/style.scss": ".page {}",
      "src/ui/pc/pages/inventory/views/list/list.test.ts": "test('ok', () => {})",
    });

    expect(result.rejected).toEqual([]);
    expect(result.files.map((file) => file.path)).toEqual([
      "application.json",
      "package.json",
      "src/ui/pc/pages/inventory/index.ts",
      "src/ui/pc/pages/inventory/views/list/index.vue",
      "src/ui/pc/pages/inventory/views/list/style.scss",
      "src/ui/pc/pages/inventory/views/list/list.test.ts",
    ]);
  });

  it("sorts PRD pack files with README and numbered chapters first", () => {
    const result = normalizeDemoPackageFiles({
      "modules/voucher.md": "# 凭证",
      "open-questions.md": "# 待确认",
      "data/catalog.json": "{}",
      "README.md": "# 顺序",
      "00-overview.md": "# 概述",
      "03-permissions.md": "| 角色 | 动作 |",
    });
    expect(result.rejected).toEqual([]);
    expect(result.files.map((file) => file.path)).toEqual([
      "README.md",
      "00-overview.md",
      "03-permissions.md",
      "modules/voucher.md",
      "open-questions.md",
      "data/catalog.json",
    ]);
  });

  it("sorts POC files with index.html first followed by styles scripts and views", () => {
    const result = normalizeDemoPackageFiles({
      "js/views/customers.js": "window.Views.customers = {};",
      "data/mock.json": "{}",
      "css/app.css": "body {}",
      "index.html": "<!DOCTYPE html><html></html>",
      "js/app.js": "console.log('app');",
      "js/router.js": "console.log('router');",
      "js/store.js": "console.log('store');",
    });

    expect(result.rejected).toEqual([]);
    expect(result.files.map((file) => file.path)).toEqual([
      "index.html",
      "css/app.css",
      "js/app.js",
      "js/router.js",
      "js/store.js",
      "js/views/customers.js",
      "data/mock.json",
    ]);
  });

  it("filters traversal, environment, binary-looking and credential files", () => {
    const result = normalizeDemoPackageFiles({
      "../escape.ts": "bad",
      "/absolute.ts": "bad",
      ".env.example": "SECRET=bad",
      "src/secret-config.ts": "bad",
      "src/credentials.json": "bad",
      "src/config.ts": "const API_KEY = 'not-a-placeholder-secret';",
      "src/token.ts": "const TOKEN = '1234567890';",
      "src/password.ts": "const PASSWORD = '1234567890';",
      "src/config.json": '{"apiKey":"1234567890"}',
      "src/auth.json": '{"password":"1234567890"}',
      "src/settings.yaml": "password: 1234567890",
      "src/request.headers": "Authorization: Bearer 1234567890",
      "src/key.pem": "-----BEGIN PRIVATE KEY-----",
      "node_modules/pkg/index.js": "export {}",
      "dist/index.js": "built",
      ".git/config": "gitdir",
      ".DS_Store": "binary",
      "src/data.bin": "\u0000binary",
      "src/index.ts": "export {}",
    });

    expect(result.files.map((file) => file.path)).toEqual(["src/index.ts"]);
    expect(result.rejected).toHaveLength(18);
  });

  it("rejects an explicit null package_files value but allows a missing field", () => {
    expect(normalizeDemoPackageFiles(undefined)).toEqual({ files: [], rejected: [] });
    expect(normalizeDemoPackageFiles(null)).toEqual({ files: [], rejected: ["package_files"] });
  });

  it("accepts the safe relative POSIX paths used by the scaffold", () => {
    expect(isSafePackagePath("application.json")).toBe(true);
    expect(isSafePackagePath("src/ui/pc/pages/inventory/index.ts")).toBe(true);
    expect(isSafePackagePath("src\\ui\\pc\\index.ts")).toBe(false);
    expect(isSafePackagePath("C:/temp/index.ts")).toBe(false);
    expect(isSafePackagePath("src/../index.ts")).toBe(false);
    expect(isSafePackagePath("src//index.ts")).toBe(false);
    expect(isSafePackagePath("src/index.ts/")).toBe(false);
    expect(isSafePackagePath("src/.env.production")).toBe(false);
    expect(isSafePackagePath("src/secret-config.ts")).toBe(false);
    expect(isSafePackagePath("src/credentials.json")).toBe(false);
  });

  it("creates a zip with a sanitized generic demo package root", async () => {
    const files = normalizeDemoPackageFiles({ "application.json": "{}", "src/index.ts": "export {}" }).files;
    const blob = await buildDemoPackageZip("库存 / Demo", files);
    expect(packageRootName("库存 / Demo")).toBe("hermes-demo-库存-Demo");
    expect(packageRootName("示例 CRM", "hermes-poc")).toBe("hermes-poc-示例-CRM");
    expect(packageRootName("示例财务", "hermes-prd")).toBe("hermes-prd-示例财务");
    expect(blob.size).toBeGreaterThan(0);

    const archive = await JSZip.loadAsync(await blob.arrayBuffer());
    expect(Object.values(archive.files).filter((entry) => !entry.dir).map((entry) => entry.name).sort()).toEqual([
      "hermes-demo-库存-Demo/application.json",
      "hermes-demo-库存-Demo/src/index.ts",
    ]);
  });
});
