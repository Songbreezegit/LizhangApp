import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdir, mkdtemp, cp, writeFile, rm, readdir, stat } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { build, root, markdown, inline, pages } from './build.mjs';
import { legalDocuments, sha256, websiteContentDigest } from './legal-consistency.mjs';

const documents = ['index.html','404.html',...pages.map(([slug]) => slug+'/index.html')];
// 这些值仅用于临时测试，不代表真实主体、备案或正式政策已经确认。
const approvedExample = {
  reviewStatus:'approved', icpApproved:true, operator:'自动测试主体',
  contactEmail:'support@example.invalid', effectiveDate:'2026-10-09',
  icpNumber:'自动测试备案号', appVersion:'1.0.0', appVersionCode:24, policyVersion:'1.0.0-policy-v1',
};
async function refreshFixtureLegal(projectRoot, { approve = false } = {}) {
  const manifestPath = path.join(projectRoot, 'legal/android-content-manifest.json');
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
  for (const name of [...legalDocuments, 'metadata.properties']) {
    const input = manifest.inputs.find(input => input.path === `src/main/assets/legal/${name}`);
    input.sha256 = sha256(await readFile(path.join(projectRoot, name.endsWith('.md') ? 'content' : 'legal', name)));
  }
  manifest.androidLegalContentSha256 = sha256(manifest.inputs.map(input => `${input.path}\n${input.sha256}\n`).join(''));
  await writeFile(manifestPath, JSON.stringify(manifest));
  if (approve) {
    const approvalPath = path.join(projectRoot, 'legal/legal-approval.properties');
    const approval = await readFile(approvalPath, 'utf8');
    await writeFile(approvalPath, approval.replace(/^approved_content_sha256=.*$/m, `approved_content_sha256=${manifest.androidLegalContentSha256}`)
      .replace(/^website_content_sha256=.*$/m, `website_content_sha256=${await websiteContentDigest(projectRoot)}`));
  }
}
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
  await cp(path.join(root, 'legal'), path.join(projectRoot, 'legal'), { recursive: true });
  if (approved) {
    const shared = `approval_status=approved\noperator_name=${approvedExample.operator}\ncontact_email=${approvedExample.contactEmail}\neffective_date=${approvedExample.effectiveDate}\npolicy_version=${approvedExample.policyVersion}\nfiling_record=${approvedExample.icpNumber}\nreviewed_by=自动测试审核者\nminors_arrangement=自动测试：供成年人使用\n`;
    await writeFile(path.join(projectRoot, 'legal/metadata.properties'), shared);
    await writeFile(path.join(projectRoot, 'legal/legal-approval.properties'), `${shared}approved_at=2026-10-10\nfiling_status=confirmed\napproved_content_sha256=pending\nwebsite_content_sha256=pending\nversion_code=24\nhighest_distributed_version_code=23\ndistribution_history_status=confirmed\napp_filing_status=confirmed\napp_filing_material=自动测试：适用材料已经测试主体确认\n`);
  }
  await refreshFixtureLegal(projectRoot, { approve: approved });
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
  assert.equal(manifest.auditCommit,'d0a71a893ce662a44ab7b8a9e9f3f174e51bad42');
  assert.match(manifest.androidLegalContentSha256, /^[a-f0-9]{64}$/);
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
    ['网站主体偏离 App 批准',{operator:'另一个自动测试主体'}],
    ['网站政策版本偏离 App 批准',{policyVersion:'1.0.0-policy-v2'}],
    ['候选名称不允许正式构建',{appVersion:'1.0.0-rc3'}],
    ['网站版本代码偏离 App 批准',{appVersionCode:23}],
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
    await refreshFixtureLegal(projectRoot, { approve: true });
    await assert.rejects(build({isPublic:true,projectRoot}),/正式构建已停止/);
    await assert.rejects(stat(path.join(projectRoot,'dist')),{code:'ENOENT'});
  });
  for (const marker of ['审核稿','初稿','待开发者审核','本地审阅版本','尚未公开上线','待运营者确认','尚待补齐','【待填写】','TODO','审**核**稿','尚未通过法律或应用商店审核','尚未完成法律或应用商店合规审核','本稿不表示已经通过法律审核','供开发者审核','未确认','未定稿','RC2 候选','RC3 候选','candidate','未成年人安排待本人确认']) await t.test(marker,async (subtest) => {
    const projectRoot = await fixture(subtest,{approved:true});
    await writeFile(path.join(projectRoot,'content','privacy.md'),`# 隐私政策\n\n## 说明\n\n${marker}。`);
    await refreshFixtureLegal(projectRoot, { approve: true });
    await assert.rejects(build({isPublic:true,projectRoot}),/正式构建已停止/);
    await assert.rejects(stat(path.join(projectRoot,'dist')),{code:'ENOENT'});
  });
});
test('最后一页未定稿时保留原审核产物，不留下半套正式站点',async (t) => {
  const projectRoot = await fixture(t,{approved:true});
  const {dist} = await build({projectRoot});
  const snapshot = await Promise.all([...documents,'robots.txt','release-manifest.json'].map(async name => [name,await readFile(path.join(dist,name),'utf8')]));
  await writeFile(path.join(projectRoot,'content','contact.md'),'# 联系开发者\n\n联系邮箱待确认。');
  await refreshFixtureLegal(projectRoot, { approve: true });
  await assert.rejects(build({isPublic:true,projectRoot}),/contact 原文/);
  for (const [name,body] of snapshot) assert.equal(await readFile(path.join(dist,name),'utf8'),body,name);
  assert.equal(JSON.parse(await readFile(path.join(dist,'release-manifest.json'),'utf8')).mode,'review');
});
test('仅修改批准配置不能把当前真实草稿变为正式政策',async (t) => {
  const projectRoot = await fixture(t,{config:approvedExample});
  await assert.rejects(build({isPublic:true,projectRoot}),/共享的法律内容仍未获开发者批准/);
  await assert.rejects(stat(path.join(projectRoot,'dist')),{code:'ENOENT'});
});
test('正式批准绑定正文摘要、主体、政策版本与实际分发历史', async (t) => {
  const cases = [
    ['批准状态仍待确认', 'approval_status', 'pending'],
    ['已批准摘要失效', 'approved_content_sha256', '0'.repeat(64)],
    ['已批准网站摘要失效', 'website_content_sha256', '0'.repeat(64)],
    ['批准主体与元数据不同', 'operator_name', '另一自动测试主体'],
    ['批准政策版本与元数据不同', 'policy_version', '1.0.0-policy-v2'],
    ['未成年人安排仍待确认', 'minors_arrangement', 'pending'],
    ['实际分发历史未确认', 'distribution_history_status', 'pending'],
    ['版本代码已经分发过', 'highest_distributed_version_code', '24'],
    ['APP 备案或适用材料未确认', 'app_filing_status', 'pending'],
    ['APP 材料说明仍为占位', 'app_filing_material', 'pending'],
    ['网站备案明确拒绝', 'filing_status', 'denied'],
    ['网站备案未经明确确认', 'filing_status', 'approved'],
    ['批准日期无效', 'approved_at', '无效日期'],
    ['批准日期不存在', 'approved_at', '2026-02-30'],
    ['批准日期格式错误', 'approved_at', '2026-1-2'],
  ];
  for (const [name, key, value] of cases) await t.test(name, async (subtest) => {
    const projectRoot = await fixture(subtest, { approved: true });
    const target = path.join(projectRoot, 'legal/legal-approval.properties');
    await writeFile(target, (await readFile(target, 'utf8')).replace(new RegExp(`^${key}=.*$`, 'm'), `${key}=${value}`));
    await assert.rejects(build({ isPublic: true, projectRoot }), /正式构建已停止/);
    await assert.rejects(stat(path.join(projectRoot,'dist')), { code: 'ENOENT' });
  });
  for (const value of ['未批准', '未审核']) await t.test(`双方未成年人安排为${value}`, async (subtest) => {
    const projectRoot = await fixture(subtest, { approved: true });
    for (const name of ['metadata.properties', 'legal-approval.properties']) {
      const target = path.join(projectRoot, 'legal', name);
      await writeFile(target, (await readFile(target, 'utf8')).replace(/^minors_arrangement=.*$/m, `minors_arrangement=${value}`));
    }
    await refreshFixtureLegal(projectRoot, { approve: true });
    await assert.rejects(build({ isPublic: true, projectRoot }), /minors_arrangement 必须由开发者确认/);
    await assert.rejects(stat(path.join(projectRoot, 'dist')), { code: 'ENOENT' });
  });
  const projectRoot = await fixture(t, { approved: true });
  await writeFile(path.join(projectRoot, 'content/privacy.md'), '# 隐私政策\n\n## 新正文\n\n自动测试：模拟批准后修改正文。\n');
  await refreshFixtureLegal(projectRoot);
  await assert.rejects(build({ isPublic: true, projectRoot }), /批准摘要未覆盖/);
  for (const name of ['faq.md', 'contact.md']) {
    const websiteFixture = await fixture(t, { approved: true });
    const target = path.join(websiteFixture, 'content', name);
    await writeFile(target, `${await readFile(target, 'utf8')}\n自动测试：新增已定稿网站说明。\n`);
    await assert.rejects(build({ isPublic: true, projectRoot: websiteFixture }), /重新批准网站内容摘要/);
  }
});
test('正式政策编号和版本代码边界与 Android 发布门禁一致', async (t) => {
  const policyCases = ['1.0.0-rc3-policy-v1', '1.0.0-RC2-policy-v1', '正式政策第一版'];
  for (const value of policyCases) await t.test(value, async (subtest) => {
    const projectRoot = await fixture(subtest, { approved: true, config: { policyVersion: value } });
    for (const name of ['metadata.properties', 'legal-approval.properties']) {
      const target = path.join(projectRoot, 'legal', name);
      await writeFile(target, (await readFile(target, 'utf8')).replace(/^policy_version=.*$/m, `policy_version=${value}`));
    }
    await refreshFixtureLegal(projectRoot, { approve: true });
    await assert.rejects(build({ isPublic: true, projectRoot }), /正式政策版本格式无效或仍使用候选编号/);
  });
  for (const value of [22, 2100000001, 24.5, '24']) await t.test(`版本代码 ${value}（${typeof value}）`, async (subtest) => {
    const projectRoot = await fixture(subtest, { approved: true, config: { appVersionCode: value } });
    const target = path.join(projectRoot, 'legal/legal-approval.properties');
    const approval = (await readFile(target, 'utf8')).replace(/^version_code=.*$/m, `version_code=${value}`).replace(/^highest_distributed_version_code=.*$/m, 'highest_distributed_version_code=21');
    await writeFile(target, approval);
    await assert.rejects(build({ isPublic: true, projectRoot }), /23 至 2100000000 的整数版本代码/);
  });
  for (const value of [23, 2100000000]) await t.test(`合法边界 ${value}`, async (subtest) => {
    const projectRoot = await fixture(subtest, { approved: true, config: { appVersionCode: value } });
    const target = path.join(projectRoot, 'legal/legal-approval.properties');
    const approval = (await readFile(target, 'utf8')).replace(/^version_code=.*$/m, `version_code=${value}`).replace(/^highest_distributed_version_code=.*$/m, `highest_distributed_version_code=${value - 1}`);
    await writeFile(target, approval);
    await build({ isPublic: true, projectRoot });
  });
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
