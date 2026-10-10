import { readFileSync, mkdirSync, writeFileSync, copyFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { escapeHtml as e, renderMarkdown } from './markdown.mjs';

const website = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const project = resolve(website, '..');
const legal = resolve(project, 'app/src/main/assets/legal');
const dist = resolve(website, 'dist');
const read = path => readFileSync(path, 'utf8');

if (process.argv.length > 2) throw new Error('此构建仅生成本地预览，不接受正式发布或部署参数。');

function properties(path) {
  return Object.fromEntries(read(path).split(/\r?\n/).map(line => line.trim())
    .filter(line => line && !line.startsWith('#')).map(line => {
      const at = line.indexOf('=');
      if (at < 1) throw new Error('法律元数据格式无效。');
      return [line.slice(0, at).trim(), line.slice(at + 1).trim()];
    }));
}

const metadata = properties(resolve(legal, 'metadata.properties'));
const approval = properties(resolve(project, 'release/legal-approval.properties'));
const sourceVersion = /CURRENT_VERSION\s*=\s*"([^"]+)"/.exec(read(resolve(project,
  'app/src/main/java/com/yangsong/lizhang/domain/legal/LegalDocument.kt')))?.[1];
const policyVersion = metadata.policy_version;
if (!policyVersion || policyVersion !== sourceVersion || policyVersion !== approval.policy_version) {
  throw new Error('Android 告知版本与文档元数据不一致，请同步更新规范源后再构建。');
}
for (const key of ['operator_name', 'operator_address', 'contact_email', 'effective_date',
  'formal_policy_version', 'minor_service_arrangement', 'approval_status']) {
  if (!metadata[key]) throw new Error('缺少法律资料或明确占位：' + key);
}

const config = JSON.parse(read(resolve(website, 'site.config.json')));
if (config.domain !== 'lizhang.songisle.xyz' || typeof config.icp?.approved !== 'boolean') {
  throw new Error('网站域名或备案配置无效。');
}
if (!config.icp.approved && config.icp.number !== null) {
  throw new Error('备案审核中不得填写备案号或生成备案链接。');
}
if (config.icp.approved && (typeof config.icp.number !== 'string' ||
  !/^[\u4e00-\u9fff]ICP备\d+号(?:-\d+)?$/.test(config.icp.number))) {
  throw new Error('备案通过后须填写本人核实的真实网站备案号。');
}

const routes = [
  { path: '/', label: '首页' },
  { path: '/privacy/', label: '隐私政策', file: 'privacy.md', title: '礼账隐私政策',
    intro: '了解本机数据、可选权限，以及你管理信息的方式。' },
  { path: '/terms/', label: '用户协议', file: 'terms.md', title: '礼账用户协议',
    intro: '了解服务范围、备份与删除规则，以及使用中的重要提示。' },
  { path: '/help/', label: '使用帮助', file: 'help.md', title: '礼账使用帮助',
    intro: '从第一笔往来开始，查阅导入、提醒、备份与常见问题。' },
  { path: '/contact/', label: '联系与反馈' }
];
const documents = new Map(routes.filter(route => route.file).map(route => {
  const markdown = read(resolve(legal, route.file));
  if (!markdown.includes(policyVersion)) throw new Error(route.file + ' 缺少当前告知内容标识。');
  return [route.path, renderMarkdown(markdown)];
}));

// 品牌图片直接来自当前 Android 源，不复制到另一套需要单独维护的源码目录。
const drawable = resolve(project, 'app/src/main/res/drawable-nodpi');
const images = [
  { source: 'launcher_cat.png', target: 'logo-cat.png' },
  { source: 'home_hero_cat.png', target: 'home-cat.png' }
];
function image(name, alt, className = '', lazy = false) {
  const asset = images.find(item => item.target === name);
  const data = readFileSync(resolve(drawable, asset.source));
  const width = data.readUInt32BE(16), height = data.readUInt32BE(20);
  return '<img src="/assets/' + e(name) + '" alt="' + e(alt) + '" class="' + e(className) +
    '" width="' + width + '" height="' + height + '"' + (lazy ? ' loading="lazy"' : '') + '>';
}

function layout(path, title, description, body) {
  const navigation = routes.map(route => '<a href="' + route.path + '"' +
    (route.path === path ? ' aria-current="page"' : '') + '>' + route.label + '</a>').join('\n');
  const filing = config.icp.approved
    ? '<a href="https://beian.miit.gov.cn/" rel="noreferrer">' + e(config.icp.number) + '</a>'
    : '<span>ICP备案审核中</span>';
  return '<!doctype html>\n<html lang="zh-CN">\n<head>\n<meta charset="utf-8">\n' +
    '<meta name="viewport" content="width=device-width, initial-scale=1">\n' +
    '<meta name="robots" content="noindex, nofollow">\n' +
    '<meta name="referrer" content="no-referrer">\n' +
    '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; img-src \'self\'; style-src \'self\'; base-uri \'none\'; form-action \'none\'">\n' +
    '<title>' + e(title) + ' · 礼账</title>\n<meta name="description" content="' + e(description) + '">\n' +
    '<link rel="icon" href="/assets/logo-cat.png" type="image/png">\n' +
    '<link rel="stylesheet" href="/assets/style.css">\n</head>\n<body>\n' +
    '<a class="skip-link" href="#main">跳到正文</a>\n' +
    '<div class="preview-banner">本地预览 · 文案及运营资料待开发者最终确认 · 尚未正式发布</div>\n' +
    '<header class="site-header wrap"><a class="brand" href="/" aria-label="礼账首页">' +
    image('logo-cat.png', '') + '<span>礼账<small>让往来清楚，让心意留住</small></span></a>' +
    '<nav aria-label="主导航">' + navigation + '</nav></header>\n' +
    '<main id="main" class="wrap">' + body + '</main>\n' +
    '<footer class="site-footer wrap"><div><a class="footer-brand" href="/">礼账</a>' +
    '<p>本机记录人情往来，认真保管每一份心意。</p></div><div class="footer-links">' +
    '<a href="/privacy/">隐私政策</a><a href="/terms/">用户协议</a><a href="/contact/">联系与反馈</a></div>' +
    '<p class="footer-status">目标域名：' + e(config.domain) + ' · ' + filing +
    '<br>当前为内部本地预览，备案审核状态不代表备案已通过。</p></footer>\n</body>\n</html>\n';
}

function documentPage(route) {
  const rendered = documents.get(route.path);
  const toc = rendered.contents.map(item => '<li><a href="#' + item.id + '">' + e(item.label) + '</a></li>').join('');
  return '<header class="page-heading"><p class="eyebrow">礼账文档</p><h1>' + route.title +
    '</h1><p>' + route.intro + '</p></header><div class="document-layout">' +
    '<nav class="contents card" aria-label="本页目录"><p>本页内容</p><ol>' + toc + '</ol></nav>' +
    '<article class="document card">' + rendered.html + '</article></div>' +
    '<div class="document-next"><a href="/help/">查看使用帮助</a><a href="/contact/">联系与反馈 →</a></div>';
}

function home() {
  const features = [
    ['01', '记一笔往来', '收到与送出分开记录。联系人、事由、日期与备注，让每一笔心意都有来处。'],
    ['02', '查一个联系人', '查看联系人的累计收送与历史往来。需要批量添加时，可主动选择通讯录导入。'],
    ['03', '看清一年收送', '按年份、月份和事由查看统计，通过日历或搜索找到需要的记录。'],
    ['04', '记得重要日子', '独立创建日期提醒，可单次或每年重复。提醒与账本分别管理，通知由你开启。']
  ].map(item => '<article class="feature card"><span class="feature-number">' + item[0] +
    '</span><h3>' + item[1] + '</h3><p>' + item[2] + '</p></article>').join('');
  return '<section class="hero"><div class="hero-copy"><p class="eyebrow">本地人情礼金记录工具</p>' +
    '<h1>人情记在心里，<br><span>往来记在礼账。</span></h1>' +
    '<p class="hero-description">记下收到与送出的心意，把联系人、礼金和重要日子整理清楚。记录留在自己的手机里，日后想起时，随手就能找到。</p>' +
    '<div class="hero-actions"><a class="button primary" href="/help/">从使用帮助开始</a>' +
    '<a class="button secondary" href="/privacy/">了解隐私保护</a></div>' +
    '<ul class="trust-points" aria-label="产品特点"><li>本机保存</li><li>无需账号</li><li>无广告</li></ul></div>' +
    '<figure class="hero-art">' + image('home-cat.png', '礼账品牌小猫抱着红包') + '</figure></section>' +
    '<section class="section" aria-labelledby="features"><div class="section-heading"><p class="eyebrow">日常往来，慢慢记清楚</p>' +
    '<h2 id="features">需要的功能，放在顺手的地方。</h2></div><div class="features">' + features + '</div></section>' +
    '<section class="backup-panel card"><div><p class="eyebrow">自己保管，也能安心迁移</p>' +
    '<h2>给记录留一份副本。</h2><p>导出 Excel 或 CSV，方便查看与整理；创建礼账备份，保留完整的联系人和礼金关系。需要保护备份内容时，可选择密码备份。</p>' +
    '<p class="backup-note">普通备份和表格文件未加密；恢复会替换现有账本，提醒与设置不在备份中。</p></div>' +
    '<a class="text-link" href="/help/#section-6">阅读备份与恢复说明 →</a></section>' +
    '<section class="reading-panel section"><div><p class="eyebrow">开始之前，把规则说清楚</p>' +
    '<h2>关于数据，你随时可以查阅。</h2><p>通讯录与通知由你主动授权。政策、协议和帮助在应用内也可完整离线阅读。</p></div>' +
    '<div class="reading-links"><a class="card" href="/privacy/"><span>隐私政策</span><small>数据范围、权限与删除规则</small><b aria-hidden="true">↗</b></a>' +
    '<a class="card" href="/terms/"><span>用户协议</span><small>使用范围与重要操作提示</small><b aria-hidden="true">↗</b></a>' +
    '<a class="card" href="/contact/"><span>联系与反馈</span><small>联系方式状态与反馈准备</small><b aria-hidden="true">↗</b></a></div></section>';
}

function contact() {
  const fields = [
    ['运营者姓名或主体', metadata.operator_name], ['联系地址', metadata.operator_address],
    ['隐私联系邮箱', metadata.contact_email], ['政策生效日期', metadata.effective_date],
    ['正式政策版本', metadata.formal_policy_version], ['未成年人服务安排', metadata.minor_service_arrangement]
  ].map(([label, value]) => '<div><dt>' + e(label) + '</dt><dd>' + e(value) + '</dd></div>').join('');
  const emailReady = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(metadata.contact_email);
  const mail = emailReady
    ? '<p><a class="button primary" href="mailto:' + e(metadata.contact_email) + '">通过邮箱联系</a></p>'
    : '<p class="callout">真实联系方式待开发者本人确认，当前占位不能用于实际投递。正式发布前须补齐有效渠道。</p>';
  return '<header class="page-heading"><p class="eyebrow">有疑问，先把事情说清楚</p><h1>联系与反馈</h1>' +
    '<p>查阅运营资料状态，了解功能反馈和隐私请求的准备方式。</p></header>' +
    '<div class="contact-grid"><section class="card contact-details"><h2>开发者与隐私联系</h2>' +
    '<dl>' + fields + '</dl>' + mail + '<p class="small-text">运营与联系资料仍待开发者本人确认。网站 ICP 备案仍在审核，当前仅本地预览。</p></section>' +
    '<section class="card feedback"><h2>反馈时可以说明什么？</h2><ol><li>礼账版本、Android 版本与手机型号。</li>' +
    '<li>操作步骤、预期结果与实际提示。</li><li>能说明问题的虚构或已遮罩示例。</li></ol>' +
    '<p class="callout">请勿发送真实通讯录、礼金金额、完整账本、备份文件或备份密码。</p>' +
    '<h2>个人信息请求</h2><p>可在正式渠道公布后提出政策解释、查看、更正或删除等请求。开发者不持有你的账本副本，无法远程操作本机记录；可先按隐私政策在应用内自行管理。</p>' +
    '<p>正式请求受理、必要核验、答复及申诉安排仍待开发者确认。网站不提供在线反馈表单。</p>' +
    '<a class="text-link" href="/privacy/#section-8">查看信息管理方式 →</a></section></div>';
}

// 所有输入先读取并校验，再写入独立的生成目录。
mkdirSync(resolve(dist, 'assets'), { recursive: true });
copyFileSync(resolve(website, 'src/style.css'), resolve(dist, 'assets/style.css'));
for (const asset of images) copyFileSync(resolve(drawable, asset.source), resolve(dist, 'assets', asset.target));
for (const route of routes) {
  const dir = route.path === '/' ? dist : resolve(dist, route.path.slice(1));
  mkdirSync(dir, { recursive: true });
  const title = route.title || (route.path === '/' ? '本地人情礼金记录工具' : '联系与反馈');
  const body = route.file ? documentPage(route) : route.path === '/' ? home() : contact();
  writeFileSync(resolve(dir, 'index.html'), layout(route.path, title, route.intro || title, body), 'utf8');
}
writeFileSync(resolve(dist, '404.html'), layout('', '页面未找到', '请通过主导航查阅礼账说明。',
  '<section class="page-heading"><p class="eyebrow">404</p><h1>暂时没有这个页面。</h1><p>可以返回首页，或通过导航查看帮助。</p><a class="button primary" href="/">返回首页</a></section>'), 'utf8');
writeFileSync(resolve(dist, 'robots.txt'), 'User-agent: *\nDisallow: /\n', 'utf8');
console.log('礼账静态网站已生成：' + dist);
console.log('共 5 个页面，仅供本地预览；候选内容标识：' + policyVersion);
