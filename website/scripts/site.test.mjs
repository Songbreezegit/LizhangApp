import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdir, mkdtemp, cp, writeFile, rm, readdir, stat } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { build, root, markdown, inline, pages } from './build.mjs';

const documents = ['index.html','404.html',...pages.map(([slug]) => slug+'/index.html')];
// 这些值仅用于临时测试，不代表真实主体、备案或正式政策已经确认。
const approvedExample = {
  reviewStatus:'approved', icpApproved:true, operator:'自动测试主体',
  contactEmail:'support@example.invalid', effectiveDate:'2026-10-09',
  icpNumber:'自动测试备案号',
};
async function fixture(t, { approved = false, config = {} } = {}) {
  const parent = path.resolve(root,'.preview');
  await mkdir(parent,{recursive:true});
  const projectRoot = await mkdtemp(path.join(parent,'site-test-'));
  t.after(async () => {
    if (path.dirname(path.resolve(projectRoot)) !== parent) throw new Error('测试清理路径超出专用目录');
    await rm(projectRoot,{recursive:true,force:true});
  });
  const original = JSON.parse(await readFile(path.join(root,'site.config.json'),'utf8'));
  await writeFile(path.join(projectRoot,'site.config.json'),JSON.stringify({...original,...(approved?approvedExample:{}),...config}));
  await cp(path.join(root,'assets'),path.join(projectRoot,'assets'),{recursive:true});
  await mkdir(path.join(projectRoot,'content'));
  for (const [slug,title] of pages) {
    const source = approved ? `# ${title}\n\n## 文档说明\n\n这是自动测试使用的已定稿样例。查看[帮助中心](/help/)。\n` : await readFile(path.join(root,'content',`${slug}.md`),'utf8');
    await writeFile(path.join(projectRoot,'content',`${slug}.md`),source);
  }
  return projectRoot;
}
async function checkOutput(dist, mode) {
  for (const name of documents) {
    const html = await readFile(path.join(dist,name),'utf8');
    assert.match(html,/<html lang="zh-CN">/);
    assert.match(html,/<meta name="viewport"/);
    assert.match(html,/id="main"/);
    assert.doesNotMatch(html,/<script|<iframe|<form|https:\/\/fonts\.|analytics|googletag|localStorage|mailto:待/);
    if (mode === 'review') {
      assert.match(html,/noindex,nofollow/);
      assert.match(html,/本地审阅稿/);
      assert.match(html,/尚未完成法律或应用商店合规审核/);
      assert.doesNotMatch(html,/rel="canonical"/);
    } else {
      assert.doesNotMatch(html,/审核稿|审阅稿|尚未生效|待确认|本地审阅|review-notice|未公开上线/);
      assert.match(html,/自动测试备案号/);
      if (name === '404.html') {
        assert.match(html,/noindex,nofollow/);
        assert.doesNotMatch(html,/rel="canonical"/);
      } else {
        assert.match(html,/content="index,follow"/);
        const route = name === 'index.html' ? '' : name.replace('index.html','');
        assert.ok(html.includes(`rel="canonical" href="https://lizhang.songisle.xyz/${route}"`),`${name} 的 canonical 错误`);
      }
    }
    for (const [,url] of html.matchAll(/(?:href|src)="([^"]+)"/g)) {
      if (url.startsWith('https:')) continue;
      if (url.startsWith('#')) {assert.ok(html.includes(`id="${url.slice(1)}"`),`${name} 缺少锚点 ${url}`);continue;}
      assert.ok(url.startsWith('/'),`${name} 链接不是绝对站内路径`);
      const target=url==='/'?'index.html':url.slice(1)+(url.endsWith('/')?'index.html':'');
      assert.ok((await stat(path.join(dist,target))).isFile(),`${name} 链接不存在：${url}`);
    }
  }
  const manifest=JSON.parse(await readFile(path.join(dist,'release-manifest.json'),'utf8'));
  assert.equal(manifest.mode,mode);
  assert.equal(manifest.auditCommit,'1ee3ea9e58a2dfb336ba3fca774dd3cf5ab674bc');
  for (const [name,hash] of Object.entries(manifest.files)) assert.equal(createHash('sha256').update(await readFile(path.join(dist,name))).digest('hex'),hash);
  assert.equal((await readFile(path.join(dist,'robots.txt'),'utf8')).trim(),`User-agent: *\n${mode==='review'?'Disallow':'Allow'}: /`);
}
test('审核版完整构建、内链资源、隐私边界与部署清单',async (t) => {
  const projectRoot = await fixture(t);
  const {dist} = await build({projectRoot});
  await checkOutput(dist,'review');
});
test('已确认的临时样例可正式构建，404不显示审核状态且禁止收录',async (t) => {
  const trackedConfig = await readFile(path.join(root,'site.config.json'),'utf8');
  const trackedPrivacy = await readFile(path.join(root,'content','privacy.md'),'utf8');
  const projectRoot = await fixture(t,{approved:true});
  const {dist} = await build({isPublic:true,projectRoot});
  await checkOutput(dist,'public');
  assert.equal(await readFile(path.join(root,'site.config.json'),'utf8'),trackedConfig);
  assert.equal(await readFile(path.join(root,'content','privacy.md'),'utf8'),trackedPrivacy);
});
test('未确认配置或无效正式元数据拒绝发布',async (t) => {
  const cases = [
    ['文档未批准',{reviewStatus:'draft'}],
    ['备案未批准',{icpApproved:false}],
    ['字符串 false 不算批准',{icpApproved:'false'}],
    ['数字 1 不算批准',{icpApproved:1}],
    ['主体为空白',{operator:'   '}],
    ['主体类型无效',{operator:true}],
    ['联系渠道待确认',{contactEmail:'待确认'}],
    ['邮箱格式无效',{contactEmail:'邮箱'}],
    ['日期待填写',{effectiveDate:'待填写'}],
    ['日期不存在',{effectiveDate:'2026-02-30'}],
    ['备案号待确认',{icpNumber:'待确认'}],
    ['错误主站域名',{domain:'songisle.xyz'}],
    ['页脚含审核状态',{updatedAt:'审核稿'}],
  ];
  for (const [name,config] of cases) await t.test(name,async (subtest) => {
    const projectRoot = await fixture(subtest,{approved:true,config});
    await assert.rejects(build({isPublic:true,projectRoot}),/正式构建已停止/);
    await assert.rejects(stat(path.join(projectRoot,'dist')),{code:'ENOENT'});
  });
});
test('所有正文页的审核状态与未确认内容均拒绝正式构建',async (t) => {
  for (const [slug] of pages) await t.test(slug,async (subtest) => {
    const projectRoot = await fixture(subtest,{approved:true});
    await writeFile(path.join(projectRoot,'content',`${slug}.md`),'# 正文\n\n## 说明\n\n尚未生效。');
    await assert.rejects(build({isPublic:true,projectRoot}),/正式构建已停止/);
    await assert.rejects(stat(path.join(projectRoot,'dist')),{code:'ENOENT'});
  });
  for (const marker of ['审核稿','初稿','待开发者审核','本地审阅版本','尚未公开上线','待运营者确认','尚待补齐','【待填写】','TODO','审**核**稿','尚未通过法律或应用商店审核','尚未完成法律或应用商店合规审核','本稿不表示已经通过法律审核','供开发者审核','未确认','未定稿']) await t.test(marker,async (subtest) => {
    const projectRoot = await fixture(subtest,{approved:true});
    await writeFile(path.join(projectRoot,'content','privacy.md'),`# 隐私政策\n\n## 说明\n\n${marker}。`);
    await assert.rejects(build({isPublic:true,projectRoot}),/正式构建已停止/);
    await assert.rejects(stat(path.join(projectRoot,'dist')),{code:'ENOENT'});
  });
});
test('最后一页未定稿时保留原审核产物，不留下半套正式站点',async (t) => {
  const projectRoot = await fixture(t,{approved:true});
  const {dist} = await build({projectRoot});
  const snapshot = await Promise.all([...documents,'robots.txt','release-manifest.json'].map(async name => [name,await readFile(path.join(dist,name),'utf8')]));
  await writeFile(path.join(projectRoot,'content','contact.md'),'# 联系开发者\n\n联系邮箱待确认。');
  await assert.rejects(build({isPublic:true,projectRoot}),/contact 原文/);
  for (const [name,body] of snapshot) assert.equal(await readFile(path.join(dist,name),'utf8'),body,name);
  assert.equal(JSON.parse(await readFile(path.join(dist,'release-manifest.json'),'utf8')).mode,'review');
});
test('仅修改批准配置不能把当前真实草稿变为正式政策',async (t) => {
  const projectRoot = await fixture(t,{config:approvedExample});
  await assert.rejects(build({isPublic:true,projectRoot}),/privacy 原文.*审核稿/);
  await assert.rejects(stat(path.join(projectRoot,'dist')),{code:'ENOENT'});
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
