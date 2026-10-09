import { readFile, mkdir, writeFile, copyFile, rm, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { createHash } from 'node:crypto';

export const root = fileURLToPath(new URL('../', import.meta.url));
export const pages = [
  ['privacy', '隐私政策', '了解权限、数据存储与您的选择。'],
  ['terms', '用户协议', '了解使用规则与服务范围。'],
  ['help', '帮助中心', '从第一笔记录，到安全迁移账本。'],
  ['faq', '常见问题', '关于权限、提醒和备份的常见疑问。'],
  ['contact', '联系开发者', '问题反馈与隐私联系渠道。'],
];
export const escape = (value) => String(value).replace(/[&<>"']/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
// 文档只允许本地相对链接或 HTTPS 链接，不执行 Markdown 中的 HTML。
export function inline(value) {
  return escape(value).replace(/\[([^\]]+)\]\(([^\s)]+)\)/g, (_, label, url) => {
    if (!/^(https:\/\/|\/(?!\/)|#)/.test(url)) throw new Error(`不支持的文档链接：${url}`);
    return `<a href="${url}"${url.startsWith('https:') ? ' rel="noreferrer"' : ''}>${label}</a>`;
  }).replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>').replace(/`([^`]+)`/g, '<code>$1</code>');
}
export function markdown(source) {
  const lines = source.trim().split(/\r?\n/), html = [], headings = [];
  let list = null, paragraph = [], table = [], headingCount = 0;
  const flushParagraph = () => { if (paragraph.length) { html.push(`<p>${inline(paragraph.join(' '))}</p>`); paragraph = []; } };
  const flushList = () => { if (list) { html.push(`</${list}>`); list = null; } };
  const flushTable = () => {
    if (!table.length) return;
    const rows = table.filter((row) => !/^\|[\s:|-]+\|$/.test(row)).map((row) => row.replace(/^\||\|$/g, '').split('|').map((cell) => inline(cell.trim())));
    html.push(`<div class="table-scroll" role="region" aria-label="文档表格" tabindex="0"><table><thead><tr>${rows[0].map(c => `<th scope="col">${c}</th>`).join('')}</tr></thead><tbody>${rows.slice(1).map(row => `<tr>${row.map(c => `<td>${c}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`);
    table = [];
  };
  for (const line of lines) {
    if (line.startsWith('|')) { flushParagraph(); flushList(); table.push(line); continue; }
    flushTable();
    const heading = /^(#{1,3})\s+(.+)$/.exec(line);
    if (heading) {
      flushParagraph(); flushList();
      if (heading[1].length === 1) continue;
      const id = `section-${++headingCount}`;
      html.push(`<h${heading[1].length} id="${id}">${inline(heading[2])}</h${heading[1].length}>`);
      if (heading[1].length === 2) headings.push({id, title: heading[2]});
      continue;
    }
    const item = /^(?:([-*])\s+|\d+\.\s+)(.+)$/.exec(line);
    if (item) {
      flushParagraph();
      const next = item[1] ? 'ul' : 'ol';
      if (next !== list) { flushList(); list = next; html.push(`<${list}>`); }
      html.push(`<li>${inline(item[2])}</li>`); continue;
    }
    flushList();
    if (!line.trim() || /^---+$/.test(line)) { flushParagraph(); continue; }
    paragraph.push(line.trim());
  }
  flushParagraph(); flushList(); flushTable();
  return { html: html.join('\n'), headings };
}
function navigation(current) {
  return `<header class="site-header"><a class="brand" href="/"><img src="/assets/mark.svg" alt="" width="36" height="36"><span>礼账<span class="brand-sub">文档与帮助</span></span></a><nav aria-label="主导航">${[['', '首页'], ...pages.filter(p=>p[0] !== 'faq')].map(([slug,label]) => `<a href="/${slug ? slug + '/' : ''}"${slug === current ? ' aria-current="page"' : ''}>${label}</a>`).join('')}</nav></header>`;
}
function layout(config, slug, title, description, content, isPublic, { isNotFound = false } = {}) {
  const indexable = isPublic && !isNotFound;
  return `<!doctype html>\n<html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${escape(title)} · 礼账</title><meta name="description" content="${escape(description)}"><meta name="robots" content="${indexable ? 'index,follow' : 'noindex,nofollow'}"><link rel="icon" href="/assets/mark.svg" type="image/svg+xml"><link rel="stylesheet" href="/assets/style.css">${indexable ? `<link rel="canonical" href="https://${escape(config.domain)}/${slug ? slug+'/' : ''}">` : ''}</head><body><a class="skip-link" href="#main">跳到正文</a><div class="shell">${navigation(slug)}${!isPublic ? '<div class="review-notice"><span class="status-dot"></span>本地审阅稿<span class="notice-detail">资料待确认 · 备案前未公开上线</span></div>' : ''}${content}<footer class="site-footer"><div><a class="footer-brand" href="/">礼账</a><p>记录人情，也认真对待您的数据。</p></div><div class="footer-links"><a href="/privacy/">隐私政策</a><a href="/terms/">用户协议</a><a href="/faq/">常见问题</a><a href="/contact/">联系开发者</a></div><p class="footer-meta">文档更新 ${escape(config.updatedAt)} · 依据 Android ${escape(config.appVersion)}${isPublic ? `<br><a href="https://beian.miit.gov.cn/" rel="noreferrer">${escape(config.icpNumber)}</a>` : '<br>审核稿尚未完成法律或应用商店合规审核。'}</p></footer></div></body></html>\n`;
}
function home(config) {
  return `<main id="main"><section class="hero"><div class="hero-copy"><span class="eyebrow">礼账使用指南</span><h1>把人情记好，<br>把安心留住<span class="coral">。</span></h1><p>收礼、送礼、联系人与日期提醒。<br>这里有使用方法，也有关于您的数据的清楚说明。</p><div class="hero-actions"><a class="button" href="/help/">开始了解 <span aria-hidden="true">↗</span></a><a class="text-link" href="/privacy/">阅读隐私政策 <span aria-hidden="true">→</span></a></div><p class="hero-caption">无需账号 · 核心功能可离线使用</p></div><img class="hero-art" src="/assets/ledger.svg" alt="账本与本地保护标记的示意插画" width="560" height="460"></section><section class="principles" aria-label="数据处理概要"><div><span>01 / 本机存储</span><p>账本留在您的设备中。</p></div><div><span>02 / 自主选择</span><p>通讯录导入由您确认。</p></div><div><span>03 / 主动备份</span><p>迁移前，先保存好账本。</p></div></section><section class="directory"><div class="section-intro"><span class="eyebrow">您需要的说明</span><h2>从这里找到答案。</h2><p>每一份文档都依据应用实际行为编写，<br>让操作步骤和数据去向更容易理解。</p></div><div class="directory-list">${pages.map(([slug,title,description], i) => `<a href="/${slug}/" class="directory-row"><span class="row-number">0${i+1}</span><div><h3>${title}</h3><p>${description}</p></div><span class="row-arrow" aria-hidden="true">↗</span></a>`).join('')}</div></section><section class="backup-callout"><div><span class="eyebrow">换手机之前</span><h2>先备份，再迁移。</h2><p>普通备份和密码加密备份都需要您主动保存。<br>独立提醒及应用设置不在账本备份中，恢复后请另行检查。</p></div><a class="text-link" href="/help/">查看备份与恢复说明 <span aria-hidden="true">→</span></a></section></main>`;
}
// 正式正文须由开发者审核定稿；拒绝草稿，不能通过自动删掉状态词伪造确认。
const reviewMarkers = /审核稿|审阅稿|草稿|初稿|本稿|未确认|未定稿|(?:尚)?未生效|(?:尚)?未(?:完成|通过)[^。\n]{0,40}审核|供(?:开发者|运营者)(?:审核|确认)|本地审阅|尚未公开上线|待(?:确认|填写|补充|补齐|审核|核实|开发者审核|运营者确认)|尚待|【待|\b(?:TODO|TBD)\b/iu;
function assertFinalText(text, label) {
  const match = reviewMarkers.exec(text);
  if (match) throw new Error(`正式构建已停止：${label} 仍有审核状态或待确认内容（${match[0]}）。`);
}
function assertPublicConfig(config) {
  if (config.reviewStatus !== 'approved' || config.icpApproved !== true) {
    throw new Error('正式构建已停止：须完成文档审核与真实备案确认。');
  }
  for (const key of ['operator', 'contactEmail', 'effectiveDate', 'icpNumber']) {
    if (typeof config[key] !== 'string' || !config[key].trim()) throw new Error(`正式构建已停止：${key} 必须填写已确认内容。`);
    assertFinalText(config[key], key);
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(config.contactEmail)) throw new Error('正式构建已停止：联系方式邮箱格式无效。');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(config.effectiveDate) || Number.isNaN(Date.parse(config.effectiveDate)) || new Date(config.effectiveDate).toISOString().slice(0,10) !== config.effectiveDate) throw new Error('正式构建已停止：政策生效日期必须为有效的 YYYY-MM-DD 日期。');
  if (config.domain !== 'lizhang.songisle.xyz') throw new Error('正式构建已停止：须使用已指定的礼账文档站子域。');
}
export async function build({ isPublic = false, projectRoot = root } = {}) {
  const sourceRoot = path.resolve(projectRoot);
  const config = JSON.parse(await readFile(path.join(sourceRoot, 'site.config.json'), 'utf8'));
  if (isPublic) assertPublicConfig(config);
  // 先读齐、渲染并检查所有页面，拒绝时不删除旧产物或留下半套正式页面。
  const output = new Map();
  output.set('index.html', layout(config,'','文档与帮助','礼账应用的隐私政策、用户协议与中文使用指南。',home(config),isPublic));
  for (const [slug,title,description] of pages) {
    const source = await readFile(path.join(sourceRoot,'content',slug+'.md'),'utf8');
    if (isPublic) assertFinalText(source, `${slug} 原文`);
    const doc = markdown(source);
    const content = `<main id="main"><div class="document-heading"><a class="breadcrumb" href="/">首页 <span aria-hidden="true">/</span></a><span class="eyebrow">${escape(config.updatedAt)} 更新</span><h1>${title}</h1><p>${description}</p></div><div class="document-layout"><aside class="toc"><span class="toc-label">本页目录</span><nav aria-label="${title}目录">${doc.headings.map(h => `<a href="#${h.id}">${escape(h.title)}</a>`).join('')}</nav></aside><article class="prose">${doc.html}</article></div><div class="document-bottom"><p>还有疑问？</p><a class="text-link" href="/contact/">联系开发者 <span aria-hidden="true">→</span></a><a class="back-top" href="#main">返回顶部 ↑</a></div></main>`;
    output.set(`${slug}/index.html`, layout(config,slug,title,description,content,isPublic));
  }
  output.set('404.html', layout(config,'','页面未找到','所请求的页面不存在。','<main id="main" class="not-found"><span class="eyebrow">404</span><h1>这页还没有记录。</h1><p>可以从首页重新找到您需要的说明。</p><a class="button" href="/">返回首页 →</a></main>',isPublic,{isNotFound:true}));
  if (isPublic) {
    for (const [name, html] of output) assertFinalText(html.replace(/<[^>]*>/g,''), `${name} 可见正文`);
  }
  const dist = path.join(sourceRoot, 'dist');
  await rm(dist, {recursive:true, force:true});
  await mkdir(path.join(dist, 'assets'), {recursive:true});
  for (const name of ['mark.svg', 'ledger.svg', 'style.css']) await copyFile(path.join(sourceRoot,'assets',name), path.join(dist,'assets',name));
  for (const [name, html] of output) {
    await mkdir(path.dirname(path.join(dist,name)),{recursive:true});
    await writeFile(path.join(dist,name),html);
  }
  await writeFile(path.join(dist,'robots.txt'), isPublic ? 'User-agent: *\nAllow: /\n' : 'User-agent: *\nDisallow: /\n');
  const files = {};
  async function walk(dir) {
    for (const entry of await readdir(dir,{withFileTypes:true})) {
      const file = path.join(dir,entry.name);
      if (entry.isDirectory()) await walk(file);
      else files[path.relative(dist,file).replaceAll('\\','/')] = createHash('sha256').update(await readFile(file)).digest('hex');
    }
  }
  await walk(dist);
  await writeFile(path.join(dist,'release-manifest.json'), JSON.stringify({appVersion:config.appVersion,auditCommit:config.auditCommit,mode:isPublic?'public':'review',files},null,2)+'\n');
  return {dist, fileCount:Object.keys(files).length + 1};
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = await build({isPublic:process.argv.includes('--public')});
  console.log(`静态网站构建完成：${result.fileCount} 个文件，${result.dist}`);
}
