import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, readdir, stat } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { build, root, markdown, inline, pages } from './build.mjs';

test('审核版完整构建、内链资源、隐私边界与部署清单',async () => {
  const {dist} = await build();
  const documents = ['index.html','404.html',...pages.map(([slug]) => slug+'/index.html')];
  const all = new Map();
  for (const name of documents) all.set(name,await readFile(path.join(dist,name),'utf8'));
  for (const [name,html] of all) {
    assert.match(html,/<html lang="zh-CN">/);
    assert.match(html,/<meta name="viewport"/);
    assert.match(html,/noindex,nofollow/);
    assert.match(html,/id="main"/);
    assert.doesNotMatch(html,/<script|<iframe|<form|https:\/\/fonts\.|analytics|googletag|localStorage|mailto:待/);
    assert.match(html,/尚未完成法律或应用商店合规审核/);
    for (const [,url] of html.matchAll(/(?:href|src)="([^"]+)"/g)) {
      if (url.startsWith('https:')) continue;
      if (url.startsWith('#')) {assert.ok(html.includes(`id="${url.slice(1)}"`),`${name} 缺少锚点 ${url}`);continue;}
      assert.ok(url.startsWith('/'),`${name} 链接不是绝对站内路径`);
      const target=url==='/'?'index.html':url.slice(1)+(url.endsWith('/')?'index.html':'');
      assert.ok((await stat(path.join(dist,target))).isFile(),`${name} 链接不存在：${url}`);
    }
  }
  const manifest=JSON.parse(await readFile(path.join(dist,'release-manifest.json'),'utf8'));
  assert.equal(manifest.mode,'review');
  assert.equal(manifest.auditCommit,'1ee3ea9e58a2dfb336ba3fca774dd3cf5ab674bc');
  for (const [name,hash] of Object.entries(manifest.files)) assert.equal(createHash('sha256').update(await readFile(path.join(dist,name))).digest('hex'),hash);
  assert.equal((await readFile(path.join(dist,'robots.txt'),'utf8')).trim(),'User-agent: *\nDisallow: /');
});
test('缺少审批与备案时拒绝正式构建',async () => {
  await assert.rejects(build({isPublic:true}),/正式构建已停止/);
});
test('Markdown不执行HTML且拒绝危险链接',() => {
  const doc=markdown('# 标题\n\n## 数据\n\n<script>alert(1)</script>\n\n- **本地**存储\n\n| 权限 | 作用 |\n| --- | --- |\n| 通讯录 | 导入 |');
  assert.match(doc.html,/&lt;script&gt;/);
  assert.match(doc.html,/<strong>本地<\/strong>/);
  assert.equal(doc.headings.length,1);
  assert.match(doc.html,/<th scope="col">权限<\/th>/);
  assert.throws(()=>inline('[链接](javascript:alert)'),/不支持/);
  assert.throws(()=>inline('[链接](\/\/example.com)'),/不支持/);
});
