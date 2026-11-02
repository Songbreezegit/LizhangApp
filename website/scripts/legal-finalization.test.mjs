import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadLegal, properties } from './legal.mjs';
import { renderMarkdown } from './markdown.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const legal = loadLegal(project, 'production');
const approval = properties(resolve(project, 'release/legal-approval.properties'));
const read = path => readFileSync(resolve(project, path), 'utf8');

test('定稿主体取自元数据且仅在两份正文末尾按三段显示', () => {
  for (const name of ['privacy', 'terms']) {
    const markdown = legal.renderDocument(name + '.md');
    const contact = markdown.slice(markdown.lastIndexOf('\n## '));
    const opening = markdown.slice(0, markdown.indexOf('\n## '));
    const operator = `运营主体：${legal.metadata.operator_name}（个人开发者）`;
    assert.equal(markdown.split(legal.metadata.operator_name).length - 1, 1);
    assert.ok(contact.includes(operator));
    assert.ok(contact.indexOf(operator) < contact.indexOf('联系邮箱：'));
    assert.ok(contact.indexOf('联系邮箱：') < contact.indexOf('官方网站：'));
    assert.ok(!opening.includes(legal.metadata.operator_name));
    assert.ok(!opening.includes(legal.metadata.contact_email));
    assert.ok(markdown.includes('生效日期：' + legal.metadata.effective_date));
    const html = renderMarkdown(markdown).html;
    assert.ok(html.includes(`<p>${operator}</p>`));
    assert.ok(html.includes(`<p>联系邮箱：<strong>${legal.metadata.contact_email}</strong></p>`));
    assert.ok(html.includes(`<p>官方网站：<strong>https://${legal.metadata.website_domain}</strong></p>`));
  }
});

test('Android 生成正文与网站复用正文逐字一致且网页保留完整正文和官网补充说明', () => {
  for (const name of ['privacy', 'terms', 'help']) {
    const markdown = legal.renderDocument(name + '.md');
    assert.equal(read(`app/build/generated/offlineLegalAssets/legal/${name}.md`), markdown);
    const websiteMarkdown = markdown + (name === 'privacy' ? '\n\n' + legal.renderWebsitePrivacy() : '');
    const html = read(`website/dist-production/${name}/index.html`);
    assert.ok(html.includes(renderMarkdown(websiteMarkdown).html), `${name} 应包含完整正文`);
    if (name === 'privacy') assert.ok(html.includes('官网访问补充说明'));
  }
});

test('生产目录仅有公开静态资源且没有内部批准及证书资料', () => {
  const directory = resolve(project, 'website/dist-production');
  const walk = path => readdirSync(resolve(directory, path), { withFileTypes: true }).flatMap(entry => {
    const name = path ? path + '/' + entry.name : entry.name;
    assert.ok(!entry.isSymbolicLink(), '公开资源不能是符号链接');
    return entry.isDirectory() ? walk(name) : [name];
  });
  assert.deepEqual(walk('').sort(), ['404.html', 'assets/home-cat.png', 'assets/logo-cat.png',
    'assets/style.css', 'contact/index.html', 'help/index.html', 'index.html', 'privacy/index.html',
    'robots.txt', 'terms/index.html'].sort());
  for (const name of ['privacy', 'terms', 'help']) {
    const html = read(`website/dist-production/${name}/index.html`);
    assert.doesNotMatch(html, /pending|\{\{|\}\}|approval_status|source_branch|code_baseline|reviewed_by|approved_at|approved_content_sha256|signing_identity_status|signing_certificate_sha256|-----BEGIN .*PRIVATE KEY-----/i);
    assert.ok(!html.includes(legal.policyVersion));
    assert.ok(!html.includes('BUILD-INFO.json'));
  }
});

test('法律批准与版本一致但 Android 备案发布条件和正式签名仍待批准', () => {
  assert.equal(legal.metadata.approval_status, 'approved');
  assert.equal(approval.approval_status, 'approved');
  assert.equal(approval.approved_content_sha256, legal.legalDigest);
  assert.equal(approval.effective_date, legal.metadata.effective_date);
  assert.equal(approval.reviewed_by, legal.metadata.operator_name);
  assert.equal(approval.policy_version, legal.policyVersion);
  assert.ok(legal.validDate(approval.effective_date));
  assert.ok(legal.validDate(approval.approved_at));
  for (const key of ['filing_status', 'signing_identity_status', 'signing_certificate_sha256']) {
    assert.equal(approval[key], 'pending');
  }
});
