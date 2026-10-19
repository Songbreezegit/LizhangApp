import { readFileSync, mkdirSync, writeFileSync, copyFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { escapeHtml as e, renderMarkdown } from './markdown.mjs';
import { loadLegal } from './legal.mjs';

const website = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const project = resolve(website, '..');
const argument = process.argv[2] || '--review';
if (process.argv.length > 3 || !['--review', '--production'].includes(argument)) {
  throw new Error('只接受 --review（默认）或 --production；构建不会部署。');
}
const mode = argument === '--production' ? 'production' : 'review';
const dist = resolve(website, mode === 'production' ? 'dist-production' : 'dist');
const { metadata, config, policyVersion, legalDigest, siteDigest, renderDocument, renderWebsitePrivacy, validDate } = loadLegal(project, mode);
const websitePrivacy = renderWebsitePrivacy();

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
  const markdown = renderDocument(route.file) + (route.path === '/privacy/' && websitePrivacy ? '\n\n' + websitePrivacy : '');
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
  const filing = '<a href="https://beian.miit.gov.cn/" rel="noreferrer">' + e(config.icp.number) + '</a>';
  const publicSecurity = config.publicSecurity.number === null ? '' :
    ' · <a href="' + e(config.publicSecurity.url) + '" rel="noreferrer">' + e(config.publicSecurity.number) + '</a>';
  const indexing = mode === 'review' || !path
    ? '<meta name="robots" content="noindex, nofollow">\n' : '';
  const canonical = mode === 'production' && path
    ? '<link rel="canonical" href="https://' + e(config.domain) + e(path) + '">\n' : '';
  return '<!doctype html>\n<html lang="zh-CN">\n<head>\n<meta charset="utf-8">\n' +
    '<meta name="viewport" content="width=device-width, initial-scale=1">\n' +
    indexing + canonical +
    '<meta name="referrer" content="no-referrer">\n' +
    '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; img-src \'self\'; style-src \'self\'; base-uri \'none\'; form-action \'none\'">\n' +
    '<title>' + e(title) + ' · 礼账</title>\n<meta name="description" content="' + e(description) + '">\n' +
    '<link rel="icon" href="/assets/logo-cat.png" type="image/png">\n' +
    '<link rel="stylesheet" href="/assets/style.css">\n</head>\n<body>\n' +
    '<a class="skip-link" href="#main">跳到正文</a>\n' +
    (mode === 'review' ? '<div class="preview-banner">审核预览 · 运营者资料及备案号已确认 · 法律正文、生效日期和部署仍待本人验收批准</div>\n' : '') +
    '<header class="site-header wrap"><a class="brand" href="/" aria-label="礼账首页">' +
    image('logo-cat.png', '') + '<span>礼账<small>让往来清楚，让心意留住</small></span></a>' +
    '<nav aria-label="主导航">' + navigation + '</nav></header>\n' +
    '<main id="main" class="wrap">' + body + '</main>\n' +
    '<footer class="site-footer wrap"><div><a class="footer-brand" href="/">礼账</a>' +
    '<p>本机记录人情往来，认真保管每一份心意。</p></div><div class="footer-links">' +
    '<a href="/privacy/">隐私政策</a><a href="/terms/">用户协议</a><a href="/contact/">联系与反馈</a></div>' +
    '<p class="footer-status">' + e(config.domain) + ' · ' + filing + publicSecurity +
    '<br>运营者：' + e(metadata.operator_name) + '（' + e(metadata.operator_type) + '） · ' +
    '<a href="mailto:' + e(metadata.contact_email) + '">' + e(metadata.contact_email) + '</a>' +
    (mode === 'review' ? '<br>审核产物，不能直接用于正式发布。' : '') + '</p></footer>\n</body>\n</html>\n';
}

function documentPage(route) {
  const rendered = documents.get(route.path);
  const toc = rendered.contents.map(item => '<li><a href="#' + item.id + '">' + e(item.label) + '</a></li>').join('');
  return '<header class="page-heading"><p class="eyebrow">礼账文档</p><h1>' + route.title +
    '</h1><p>' + route.intro + '</p></header>' +
    (route.path === '/privacy/' && mode === 'review' && !websitePrivacy
      ? '<aside class="callout">审核说明：官网访问补充说明须在核实实际日志范围、保存期限及必要接入方后补齐；当前审核包不能正式发布。</aside>' : '') +
    '<div class="document-layout">' +
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
    '<a class="card" href="/contact/"><span>联系与反馈</span><small>开发者联系方式与反馈说明</small><b aria-hidden="true">↗</b></a></div></section>';
}

function contact() {
  const fields = [
    ['运营者', metadata.operator_name + '（' + metadata.operator_type + '）'],
    ['隐私联系邮箱', metadata.contact_email],
    ...(validDate(metadata.effective_date) ? [['政策生效日期', metadata.effective_date]] : []),
    ['正式政策版本', metadata.formal_policy_version], ['未成年人服务安排', metadata.minor_service_arrangement]
  ].map(([label, value]) => '<div><dt>' + e(label) + '</dt><dd>' + e(value) + '</dd></div>').join('');
  const emailReady = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(metadata.contact_email);
  const mail = emailReady
    ? '<p><a class="button primary" href="mailto:' + e(metadata.contact_email) + '">通过邮箱联系</a></p>'
    : '<p class="callout">真实联系方式待开发者本人确认，当前占位不能用于实际投递。正式发布前须补齐有效渠道。</p>';
  return '<header class="page-heading"><p class="eyebrow">有疑问，先把事情说清楚</p><h1>联系与反馈</h1>' +
    '<p>查看运营者资料，了解功能反馈和隐私请求的联系方法。</p></header>' +
    '<div class="contact-grid"><section class="card contact-details"><h2>开发者与隐私联系</h2>' +
    '<dl>' + fields + '</dl>' + mail + '<p class="small-text">网站 ICP 备案号：' + e(config.icp.number) + '。</p></section>' +
    '<section class="card feedback"><h2>反馈时可以说明什么？</h2><ol><li>礼账版本、Android 版本与手机型号。</li>' +
    '<li>操作步骤、预期结果与实际提示。</li><li>能说明问题的虚构或已遮罩示例。</li></ol>' +
    '<p class="callout">请勿发送真实通讯录、礼金金额、完整账本、备份文件或备份密码。</p>' +
    '<h2>个人信息请求</h2><p>可通过上述邮箱提出政策解释、查看、更正或删除等请求。开发者不持有你的账本副本，无法远程操作本机记录；可先按隐私政策在应用内自行管理。</p>' +
    '<p>运营者按具体请求沟通必要核验和处理方式并依法答复；对处理有异议时可继续通过邮箱说明或依法向有权部门反映。网站不提供在线反馈表单。</p>' +
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
writeFileSync(resolve(dist, 'robots.txt'), mode === 'production'
  ? 'User-agent: *\nAllow: /\nDisallow: /404.html\n' : 'User-agent: *\nDisallow: /\n', 'utf8');
writeFileSync(resolve(dist, 'BUILD-INFO.json'), JSON.stringify({ mode, domain: config.domain, policyVersion,
  legalContentSha256: legalDigest, siteContentSha256: siteDigest,
  acceptance: '未测试，等待用户验收', deployable: mode === 'production' }, null, 2) + '\n', 'utf8');
console.log('礼账静态网站已生成：' + dist);
console.log('模式：' + mode + '；内容标识：' + policyVersion + '；未测试，等待用户验收。');
console.log('legal_content_sha256=' + legalDigest);
console.log('site_content_sha256=' + siteDigest);
