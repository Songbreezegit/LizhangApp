import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadRelease } from './release.mjs';

const website = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const config = JSON.parse(readFileSync(resolve(website, 'site.config.json'), 'utf8'));
const release = loadRelease(config.release);
const page = name => readFileSync(resolve(website, 'dist-production', name), 'utf8');

test('下载页展示同一份获准正式 APK 的版本、文件大小和摘要', () => {
  assert.equal(release.versionName, '1.0.0');
  assert.equal(release.versionCode, 25);
  assert.equal(release.sizeBytes, 26813393);
  assert.equal(release.sha256, '26a0fed1a81286bf60bd7ba405c077fde0e5214c4be7267112ea8eaf936d4d55');
  const html = page('download/index.html');
  assert.ok(html.includes(`href="${release.apkUrl}" download="${release.apkFilename}"`));
  assert.ok(html.includes(`href="${release.latestReleaseUrl}"`));
  assert.ok(html.includes(`href="${release.checksumUrl}"`));
  assert.ok(html.includes(`id="apk-sha256" tabindex="0">${release.sha256}</code>`));
  assert.ok(html.includes(`Android ${release.minAndroidVersion}+`));
  assert.ok(html.includes(`${release.sizeMiB} MiB`));
  assert.ok(html.includes('26,813,393 字节'));
  assert.ok(html.includes('正式签名版本'));
  for (const name of ['往来记账', '联系人管理', '日历与统计', '日期提醒', '本地备份']) assert.ok(html.includes(name));
  assert.ok(html.includes('备份包含联系人和礼金记录，不包含独立提醒及应用设置'));
});

test('首页先进入站内下载页，下载页有独立 SEO 和原有法律入口', () => {
  assert.ok(page('index.html').includes('class="button primary" href="/download/">下载 Android 版'));
  assert.ok(page('index.html').includes('当前正式版 v' + release.versionName));
  assert.ok(page('index.html').includes('class="button secondary" href="/help/">使用帮助'));
  const html = page('download/index.html');
  assert.ok(html.includes('<link rel="canonical" href="https://lizhang.songisle.xyz/download/">'));
  assert.ok(html.includes('<title>Android 版下载 · 礼账</title>'));
  assert.ok(html.includes('name="description"'));
  for (const name of ['privacy', 'terms', 'help']) assert.ok(html.includes(`href="/${name}/"`));
  assert.ok(html.includes('渝ICP备2026025309号-1'));
  assert.ok(!html.includes('name="robots" content="noindex'));
});

test('复制摘要只加载同源脚本，其余页面继续禁止脚本，未开放内联脚本', () => {
  const html = page('download/index.html');
  assert.ok(html.includes('script-src \'self\''));
  assert.ok(html.includes('<script src="/assets/download.js" defer></script>'));
  assert.ok(html.includes('data-copy-checksum disabled'));
  assert.ok(html.includes('role="status" aria-live="polite"'));
  assert.ok(html.includes('校验值也可长按或选中复制'));
  for (const name of ['index.html', 'privacy/index.html', 'terms/index.html', 'help/index.html', 'contact/index.html']) {
    assert.doesNotMatch(page(name), /script-src|<script\b|unsafe-inline|unsafe-eval/);
  }
  assert.equal(page('assets/download.js'), readFileSync(resolve(website, 'src/download.js'), 'utf8'));
  assert.doesNotMatch(html, /unsafe-inline|unsafe-eval|onclick=|onload=/);
});

test('不接受错误的仓库、版本地址、文件名或无效校验信息', () => {
  for (const change of [
    { apkUrl: 'https://example.com/app.apk' },
    { apkUrl: release.apkUrl.replace('/v1.0.0/', '/v2.0.0/') },
    { apkFilename: 'app-debug.apk' },
    { releaseUrl: release.latestReleaseUrl },
    { checksumUrl: 'https://example.com/SHA256SUMS.txt' },
    { versionCode: 0 }, { sha256: 'pending' }, { sizeBytes: -1 }
  ]) assert.throws(() => loadRelease({ ...config.release, ...change }));
});

test('Nginx 仅下载页允许同源脚本，保留安全头及内部文件拦截', () => {
  const nginx = readFileSync(resolve(website, 'deploy/nginx-https.conf'), 'utf8');
  assert.equal((nginx.match(/script-src 'self'/g) || []).length, 1);
  const downloadLocation = nginx.match(/location \/download\/ \{([\s\S]*?)\n    \}/)?.[1];
  assert.ok(downloadLocation);
  for (const header of ['X-Content-Type-Options nosniff', 'Referrer-Policy no-referrer', 'X-Frame-Options DENY']) {
    assert.ok(downloadLocation.includes(header));
  }
  assert.ok(downloadLocation.includes("script-src 'self'"));
  assert.ok(nginx.includes('BUILD-INFO'));
  assert.doesNotMatch(nginx, /unsafe-inline|unsafe-eval|location \^~ \/download\//);
});
