import { readFile, writeFile, mkdir, readdir, copyFile, access } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const websiteRoot = fileURLToPath(new URL('../', import.meta.url));
export const legalDocuments = ['privacy.md', 'terms.md', 'help.md'];
export const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');
const canonicalDigest = (inputs) => sha256(inputs.map(({ path: name, sha256: hash }) => `${name}\n${hash}\n`).join(''));
const fail = (message) => { throw new Error(`法律内容一致性检查失败：${message}`); };
// 与 Android 发布门禁的七语言草稿规则一致，并保留中文未确认、尚未状态的拒绝。
const unapprovedMarkers = /待.{0,8}(?:确认|確認|填写|填寫|批准|审核|審核)|未.{0,4}(?:批准|审核|審核|生效)|草稿|审核稿|審核稿|候选|候選|草案|審査用|審査待ち|未発効|候補|(?:承認|確認)待ち|검토용|초안|미시행|후보|(?:검토|확인|승인)\s*대기|승인이\s*필요|\b(?:pending|todo|tbd|placeholder|draft|candidate|candidato|candidat|borrador)\b|\bnot\s+yet\s+effective\b|\bawait(?:s)?\s+(?:review|approval|(?:developer\s+)?confirmation)\b|\brequire(?:s)?\s+(?:developer\s+)?approval\b|\b(?:pendientes?|espera(?:n)?)\s+(?:de\s+)?(?:aprobación|revisión|confirmación)\b|\bno\s+vigente\b|\bprojet\s+(?:RC[0-9]+|intégral)\b|\bnon\s+encore\s+en\s+vigueur\b|\battendent\s+(?:encore\s+|une\s+|l[’'])?(?:approbation|validation)\b|à\s+confirmer|待|未确认|尚未/iu;
export function properties(source) {
  return Object.fromEntries(source.split(/\r?\n/).filter(line => line.trim() && !line.trimStart().startsWith('#')).map(line => {
    const separator = line.indexOf('=');
    if (separator < 1) fail('批准资料或元数据格式无效。');
    return [line.slice(0, separator).trim(), line.slice(separator + 1).trim()];
  }));
}
async function exists(file) {
  try { await access(file); return true; } catch (error) { if (error.code === 'ENOENT') return false; throw error; }
}
export async function androidLegalInputs(repositoryRoot) {
  const appRoot = path.join(repositoryRoot, 'app');
  const files = [];
  async function walk(dir) {
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      const file = path.join(dir, entry.name);
      if (entry.isDirectory()) await walk(file);
      else if (entry.isFile()) files.push(file);
    }
  }
  await walk(path.join(appRoot, 'src/main/assets/legal'));
  const resourceRoot = path.join(appRoot, 'src/main/res');
  for (const directory of await readdir(resourceRoot, { withFileTypes: true })) {
    if (!directory.isDirectory() || !directory.name.startsWith('values')) continue;
    for (const entry of await readdir(path.join(resourceRoot, directory.name), { withFileTypes: true })) {
      if (entry.isFile() && entry.name.endsWith('.xml') &&
          (entry.name.startsWith('legal') || entry.name.startsWith('privacy') || entry.name === 'candidate_release_strings.xml')) {
        files.push(path.join(resourceRoot, directory.name, entry.name));
      }
    }
  }
  const names = files.map(file => path.relative(appRoot, file).replaceAll('\\', '/')).sort();
  return Promise.all(names.map(async name => ({ path: name, sha256: sha256(await readFile(path.join(appRoot, name))) })));
}
export async function websiteContentDigest(projectRoot = websiteRoot) {
  const names = ['website/site.config.json'];
  async function walk(directory) {
    for (const entry of await readdir(directory, { withFileTypes: true })) {
      const file = path.join(directory, entry.name);
      if (entry.isDirectory()) await walk(file);
      else if (entry.isFile() && entry.name.endsWith('.md')) names.push(`website/${path.relative(projectRoot, file).replaceAll('\\', '/')}`);
    }
  }
  await walk(path.join(projectRoot, 'content'));
  names.sort();
  return canonicalDigest(await Promise.all(names.map(async name => ({ path: name, sha256: sha256(await readFile(path.join(projectRoot, name.slice('website/'.length)))) }))));
}
// 只镜像已存在的真实正文和审批状态；不删除草稿，不创建批准。
export async function syncLegal({ projectRoot = websiteRoot, repositoryRoot = path.dirname(projectRoot) } = {}) {
  const mirror = path.join(projectRoot, 'legal');
  await mkdir(mirror, { recursive: true });
  for (const name of legalDocuments) await copyFile(path.join(repositoryRoot, 'app/src/main/assets/legal', name), path.join(projectRoot, 'content', name));
  await copyFile(path.join(repositoryRoot, 'app/src/main/assets/legal/metadata.properties'), path.join(mirror, 'metadata.properties'));
  await copyFile(path.join(repositoryRoot, 'release/legal-approval.properties'), path.join(mirror, 'legal-approval.properties'));
  const inputs = await androidLegalInputs(repositoryRoot);
  await writeFile(path.join(mirror, 'android-content-manifest.json'), JSON.stringify({
    schemaVersion: 1, algorithm: 'sha256', androidLegalContentSha256: canonicalDigest(inputs), inputs,
  }, null, 2) + '\n');
  return verifyLegalMirror({ projectRoot });
}
// 网站自带镜像和输入摘要，单独复制 website/ 后仍可构建和核验批准。
export async function verifyLegalMirror({ projectRoot = websiteRoot } = {}) {
  const manifest = JSON.parse(await readFile(path.join(projectRoot, 'legal/android-content-manifest.json'), 'utf8'));
  const names = manifest.inputs?.map(input => input.path);
  if (manifest.schemaVersion !== 1 || manifest.algorithm !== 'sha256' || !Array.isArray(names) ||
      names.some((name, index) => typeof name !== 'string' || !name.startsWith('src/main/') || name.includes('..') || name.includes('\\') || index && names[index - 1] >= name) ||
      manifest.inputs.some(input => !/^[a-f0-9]{64}$/.test(input.sha256)) || canonicalDigest(manifest.inputs) !== manifest.androidLegalContentSha256) {
    fail('Android 内容输入清单无效或摘要不一致。');
  }
  for (const name of [...legalDocuments, 'metadata.properties']) {
    const input = manifest.inputs.find(input => input.path === `src/main/assets/legal/${name}`);
    const file = path.join(projectRoot, name.endsWith('.md') ? 'content' : 'legal', name);
    if (!input || sha256(await readFile(file)) !== input.sha256) fail(`${name} 与 Android 内容摘要不一致，请审查后运行 npm run legal:sync。`);
  }
  const metadata = properties(await readFile(path.join(projectRoot, 'legal/metadata.properties'), 'utf8'));
  const approval = properties(await readFile(path.join(projectRoot, 'legal/legal-approval.properties'), 'utf8'));
  return { manifest, metadata, approval, websiteContentSha256: await websiteContentDigest(projectRoot) };
}
export async function verifyRepositoryConsistency({ projectRoot = websiteRoot, repositoryRoot = path.dirname(projectRoot) } = {}) {
  const mirrored = await verifyLegalMirror({ projectRoot });
  const actual = await androidLegalInputs(repositoryRoot);
  if (JSON.stringify(actual) !== JSON.stringify(mirrored.manifest.inputs)) fail('Android 法律正文或告知资源已变化，网站输入清单尚未同步。');
  const actualApproval = await readFile(path.join(repositoryRoot, 'release/legal-approval.properties'));
  if (!actualApproval.equals(await readFile(path.join(projectRoot, 'legal/legal-approval.properties')))) fail('网站与 Android 的开发者批准资料不一致。');
  return mirrored;
}
export async function verifyRepositoryIfAvailable({ projectRoot = websiteRoot } = {}) {
  const repositoryRoot = path.dirname(projectRoot);
  if (await exists(path.join(repositoryRoot, 'app/src/main/assets/legal'))) {
    return verifyRepositoryConsistency({ projectRoot, repositoryRoot });
  }
  return verifyLegalMirror({ projectRoot });
}
export function assertApprovedLegal({ metadata, approval, manifest, websiteContentSha256 }, config) {
  const reject = (message) => { throw new Error(`正式构建已停止：${message}`); };
  if (approval.approval_status !== 'approved' || metadata.approval_status !== 'approved') reject('App 与网站共享的法律内容仍未获开发者批准。');
  const pending = unapprovedMarkers;
  const required = ['operator_name', 'contact_email', 'effective_date', 'policy_version', 'filing_record', 'reviewed_by', 'minors_arrangement'];
  for (const key of required) {
    if (!approval[key] || !metadata[key] || pending.test(approval[key]) || pending.test(metadata[key]) || approval[key] !== metadata[key]) reject(`App 与网站的 ${key} 必须由开发者确认且完全一致。`);
  }
  for (const key of ['approved_at', 'filing_status']) if (!approval[key] || pending.test(approval[key])) reject(`${key} 尚未确认。`);
  if (approval.filing_status !== 'confirmed') reject('网站备案资料尚未由本人明确确认。');
  for (const key of ['effective_date', 'approved_at']) {
    const value = approval[key];
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(Date.parse(value)) || new Date(value).toISOString().slice(0, 10) !== value) reject(`${key} 必须为有效的 YYYY-MM-DD 日期。`);
  }
  if (!/^[A-Za-z0-9._-]+$/.test(approval.policy_version) || approval.policy_version.toLowerCase().includes('rc')) reject('正式政策版本格式无效或仍使用候选编号。');
  if (approval.approved_content_sha256 !== manifest.androidLegalContentSha256) reject('批准摘要未覆盖当前 Android 法律内容与七语言告知，修改配置不能批准新正文。');
  if (approval.website_content_sha256 !== websiteContentSha256) reject('网站正文、帮助补充、常见问题、联系说明或网站配置已变化，须本人重新批准网站内容摘要。');
  if (config.operator !== metadata.operator_name || config.contactEmail !== metadata.contact_email || config.effectiveDate !== metadata.effective_date || config.icpNumber !== metadata.filing_record || config.policyVersion !== metadata.policy_version) reject('网站主体、邮箱、生效日期、政策版本或备案号与 App 批准内容不一致。');
  if (config.appVersion !== '1.0.0' || !/^[1-9][0-9]*$/.test(approval.version_code ?? '') || !Number.isInteger(config.appVersionCode) || config.appVersionCode < 23 || config.appVersionCode > 2100000000 || Number(approval.version_code) !== config.appVersionCode) reject('正式网站须使用获批的 1.0.0 版本名称与 23 至 2100000000 的整数版本代码。');
  if (approval.distribution_history_status !== 'confirmed' || !/^\d+$/.test(approval.highest_distributed_version_code ?? '') || BigInt(approval.version_code) <= BigInt(approval.highest_distributed_version_code)) reject('正式版本代码须根据已确认的实际分发历史递增。');
  if (approval.app_filing_status !== 'confirmed' || !approval.app_filing_material || pending.test(approval.app_filing_material)) reject('APP 备案或适用材料尚未获本人确认；网站ICP备案不能替代此项。');
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = process.argv.includes('--sync') ? await syncLegal() : await verifyRepositoryConsistency();
  console.log(`法律正文与批准资料一致：${result.manifest.inputs.length} 个 Android 输入，SHA-256 ${result.manifest.androidLegalContentSha256}`);
  console.log(`website_content_sha256=${result.websiteContentSha256}（仅计算；不修改批准状态）`);
}
