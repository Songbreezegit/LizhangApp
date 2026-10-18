import { readFileSync, readdirSync } from 'node:fs';
import { resolve, relative } from 'node:path';
import { createHash } from 'node:crypto';

const read = path => readFileSync(path, 'utf8');
const hash = data => createHash('sha256').update(data).digest('hex');
const locales = ['values', 'values-b+zh+Hant', 'values-en', 'values-ja', 'values-ko', 'values-es', 'values-fr'];

export function properties(path) {
  const result = Object.create(null);
  for (const source of read(path).split(/\r?\n/)) {
    const line = source.trim();
    if (!line || line.startsWith('#')) continue;
    const at = line.indexOf('=');
    const key = line.slice(0, at).trim();
    if (at < 1 || Object.hasOwn(result, key)) throw new Error('法律配置格式无效或有重复字段：' + path);
    result[key] = line.slice(at + 1).trim();
  }
  return result;
}

function filesUnder(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = resolve(directory, entry.name);
    if (entry.isSymbolicLink()) throw new Error('批准输入不可使用符号链接：' + path);
    return entry.isDirectory() ? filesUnder(path) : entry.isFile() ? [path] : [];
  });
}

export function loadLegal(project, mode) {
  const app = resolve(project, 'app');
  const legal = resolve(app, 'src/main/assets/legal');
  const metadata = properties(resolve(legal, 'metadata.properties'));
  const approval = properties(resolve(project, 'release/legal-approval.properties'));
  const config = JSON.parse(read(resolve(project, 'website/site.config.json')));
  const version = /CURRENT_VERSION\s*=\s*"([^"]+)"/.exec(read(resolve(app,
    'src/main/java/com/yangsong/lizhang/domain/legal/LegalDocument.kt')))?.[1];
  if (!version || metadata.policy_version !== version || approval.policy_version !== version) {
    throw new Error('Android 常量、法律元数据与发布批准资料版本不一致。');
  }
  for (const key of ['app_name', 'english_name', 'operator_type', 'operator_name', 'operator_address',
    'contact_email', 'effective_date', 'formal_policy_version', 'formal_terms_version',
    'minor_service_arrangement', 'filing_record', 'website_domain', 'approval_status',
    'website_log_scope', 'website_log_retention', 'website_log_review_status']) {
    if (!metadata[key]) throw new Error('缺少法律资料字段：' + key);
  }
  if (config.domain !== 'lizhang.songisle.xyz' || config.domain !== metadata.website_domain ||
    config.icp?.approved !== true || config.icp.number !== '渝ICP备2026025309号-1' ||
    config.icp.number !== metadata.filing_record) throw new Error('域名或真实 ICP 备案配置不一致。');
  if (metadata.operator_details_status !== 'confirmed' || approval.operator_details_status !== 'confirmed' ||
    metadata.operator_name !== approval.operator_name || metadata.contact_email !== approval.contact_email ||
    metadata.filing_record !== approval.filing_record ||
    !/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(metadata.contact_email)) {
    throw new Error('运营者公开资料与批准配置不一致。');
  }
  const ps = config.publicSecurity;
  if (!ps || !((ps.number === null && ps.url === null) ||
    (typeof ps.number === 'string' && /^[\u4e00-\u9fff]+公网安备\d+号$/.test(ps.number) &&
    typeof ps.url === 'string' && /^https:\/\/(?:www\.)?beian\.gov\.cn\//.test(ps.url)))) {
    throw new Error('公安联网备案仅可配置经核实的真实编号及官方 HTTPS 链接，未取得时保留 null。');
  }

  // 与既有 Gradle / PowerShell 摘要完全同一组路径、字节及排序；不将网站批准混入法律正文摘要。
  const inputs = [...filesUnder(legal), ...filesUnder(resolve(app, 'src/main/res')).filter(path => {
    const parts = relative(app, path).replaceAll('\\', '/').split('/');
    return parts.at(-2).startsWith('values') && /^(legal|privacy).*\.xml$/.test(parts.at(-1));
  })].sort((a, b) => {
    const x = relative(app, a).replaceAll('\\', '/'), y = relative(app, b).replaceAll('\\', '/');
    return x < y ? -1 : x > y ? 1 : 0;
  });
  const legalDigest = hash(inputs.map(path => relative(app, path).replaceAll('\\', '/') + '\n' +
    hash(readFileSync(path)) + '\n').join(''));
  const { productionApproval, ...siteInputs } = config;
  const siteSources = ['scripts/build.mjs', 'scripts/legal.mjs', 'scripts/markdown.mjs', 'src/style.css',
    'deploy/nginx-loopback.conf', 'deploy/nginx-http.conf', 'deploy/nginx-https.conf'];
  const siteDigest = hash(JSON.stringify(siteInputs) + '\n' + legalDigest + '\n' +
    hash(readFileSync(resolve(project, 'release/unapproved-content-pattern.txt'))) + '\n' +
    siteSources.map(path => path + '\n' + hash(readFileSync(resolve(project, 'website', path))) + '\n').join('') +
    ['launcher_cat.png', 'home_hero_cat.png'].map(name => name + '\n' +
      hash(readFileSync(resolve(app, 'src/main/res/drawable-nodpi', name))) + '\n').join(''));
  const unapproved = new RegExp(read(resolve(project, 'release/unapproved-content-pattern.txt')).trim(), 'iu');
  const validDate = value => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
  const ready = (value, field) => {
    if (typeof value !== 'string' || !value.trim() || unapproved.test(value)) {
      throw new Error('生产构建被阻止：尚未确认 ' + field);
    }
    return value;
  };
  if (mode === 'production') {
    if (approval.approval_status !== 'approved' || metadata.approval_status !== 'approved') {
      throw new Error('生产构建被阻止：法律正文必须由用户本人明确批准，运营资料确认不能代替批准。');
    }
    for (const key of ['operator_name', 'contact_email', 'effective_date', 'policy_version', 'filing_record', 'reviewed_by']) {
      if (ready(approval[key], key) !== metadata[key]) throw new Error('生产构建被阻止：批准资料不一致 ' + key);
    }
    if (!validDate(metadata.effective_date) || !validDate(approval.approved_at)) {
      throw new Error('生产构建被阻止：生效日期和批准日期必须为真实 YYYY-MM-DD 日期。');
    }
    if (metadata.website_log_review_status !== 'confirmed') throw new Error('生产构建被阻止：实际网站日志未确认。');
    for (const [key, value] of Object.entries(metadata)) ready(value, key);
    for (const locale of locales) for (const name of ['legal_strings.xml', 'privacy_notice.xml']) {
      read(resolve(app, 'src/main/res', locale, name));
    }
    for (const path of inputs) if (unapproved.test(read(path))) {
      throw new Error('生产构建被阻止：法律正文、元数据或七语言资源仍含未批准标记：' + relative(app, path));
    }
    if (approval.approved_content_sha256 !== legalDigest) throw new Error('生产构建被阻止：法律正文摘要已变化，须重新审阅。');
    for (const key of ['icpDomainStatus', 'icpOperatorStatus', 'accessProviderStatus', 'serverStatus']) {
      if (config.deploymentReview?.[key] !== 'confirmed') throw new Error('生产构建被阻止：部署前复核缺少 ' + key);
    }
    if (ready(config.deploymentReview.reviewedBy, '部署复核人') !== metadata.reviewed_by ||
      !validDate(config.deploymentReview.reviewedAt)) throw new Error('生产构建被阻止：缺少真实部署复核记录。');
    if (productionApproval?.status !== 'approved' ||
      ready(productionApproval.reviewedBy, '网站批准人') !== metadata.reviewed_by ||
      !validDate(productionApproval.approvedAt) || productionApproval.approvedSiteSha256 !== siteDigest) {
      throw new Error('生产构建被阻止：网站源码、配置和部署模板须另经本人审阅并批准当前摘要。');
    }
  }

  function renderDocument(name) {
    const raw = read(resolve(legal, name));
    if (!raw.includes(version)) throw new Error(name + ' 缺少当前政策版本。');
    // 审核构建永远保留候选提示，即使有人提前填写 approved 也不会生成生产状态。
    const values = { ...metadata, publication_status: mode === 'production'
      ? '正式文本' : '候选定稿 · 尚未正式生效 · 等待用户验收与发布批准' };
    const rendered = raw.replace(/\{\{([a-z_]+)}}/g, (_, key) => {
      if (!Object.hasOwn(values, key) || !values[key]) throw new Error('正文缺少统一配置：' + key);
      return values[key];
    });
    if (rendered.includes('{{') || rendered.includes('}}')) throw new Error('正文有未解析字段：' + name);
    if (mode === 'production' && unapproved.test(rendered)) throw new Error('生产正文仍有未批准说明：' + name);
    return rendered;
  }
  return { metadata, config, policyVersion: version, legalDigest, siteDigest, renderDocument };
}
