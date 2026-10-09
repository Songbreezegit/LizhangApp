import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir, mkdtemp, cp, rm, access } from 'node:fs/promises';
import path from 'node:path';
import { build } from './build.mjs';
import { websiteRoot, verifyLegalMirror, verifyRepositoryConsistency, sha256 } from './legal-consistency.mjs';

async function fixture(t, { repository = false } = {}) {
  const parent = path.join(websiteRoot, '.preview');
  await mkdir(parent, { recursive: true });
  const directory = await mkdtemp(path.join(parent, 'legal-test-'));
  t.after(async () => {
    if (path.dirname(path.resolve(directory)) !== parent) throw new Error('一致性测试清理路径超出专用目录');
    await rm(directory, { recursive: true, force: true });
  });
  const projectRoot = path.join(directory, 'website');
  await mkdir(projectRoot);
  for (const name of ['content', 'legal', 'assets']) await cp(path.join(websiteRoot, name), path.join(projectRoot, name), { recursive: true });
  await cp(path.join(websiteRoot, 'site.config.json'), path.join(projectRoot, 'site.config.json'));
  if (repository) {
    const { manifest } = await verifyLegalMirror({ projectRoot });
    for (const input of manifest.inputs) {
      const destination = path.join(directory, 'app', input.path);
      await mkdir(path.dirname(destination), { recursive: true });
      if (input.path.startsWith('src/main/assets/legal/')) {
        const name = path.basename(input.path);
        await cp(path.join(projectRoot, name.endsWith('.md') ? 'content' : 'legal', name), destination);
      } else {
        // 临时资源为合成样例，独立网站副本也可运行偏移测试，不复制真实 Android 私有状态。
        await writeFile(destination, `<resources><string name="automatic_test">自动测试：${input.path}</string></resources>\n`);
      }
      input.sha256 = sha256(await readFile(destination));
    }
    manifest.androidLegalContentSha256 = sha256(manifest.inputs.map(input => `${input.path}\n${input.sha256}\n`).join(''));
    await writeFile(path.join(projectRoot, 'legal/android-content-manifest.json'), JSON.stringify(manifest));
    await mkdir(path.join(directory, 'release'));
    await cp(path.join(websiteRoot, 'legal/legal-approval.properties'), path.join(directory, 'release/legal-approval.properties'));
  }
  return { projectRoot, repositoryRoot: directory };
}

test('仓库中的法律全文、元数据、七语言输入清单与批准镜像一致', async (t) => {
  try { await access(path.join(websiteRoot, '../app/src/main/assets/legal')); }
  catch (error) {
    if (error.code !== 'ENOENT') throw error;
    t.skip('单独 website 副本没有 Android 工程；独立镜像校验由下一项覆盖');
    return;
  }
  const { metadata, approval } = await verifyRepositoryConsistency();
  assert.equal(metadata.policy_version, approval.policy_version);
});

test('单独复制 website 后审核构建无需 Android、Gradle 或联网', async (t) => {
  const { projectRoot } = await fixture(t);
  const { dist } = await build({ projectRoot });
  const manifest = JSON.parse(await readFile(path.join(dist, 'release-manifest.json'), 'utf8'));
  assert.equal(manifest.mode, 'review');
  assert.match(manifest.androidLegalContentSha256, /^[a-f0-9]{64}$/);
});

test('任一网站共享正文或元数据偏移会拒绝构建并保留旧产物', async (t) => {
  for (const relative of ['content/privacy.md', 'content/terms.md', 'content/help.md', 'legal/metadata.properties']) {
    await t.test(relative, async (subtest) => {
      const { projectRoot } = await fixture(subtest);
      const { dist } = await build({ projectRoot });
      const previous = await readFile(path.join(dist, 'release-manifest.json'), 'utf8');
      await writeFile(path.join(projectRoot, relative), `${await readFile(path.join(projectRoot, relative), 'utf8')}\n自动测试：模拟未同步改动。\n`);
      await assert.rejects(build({ projectRoot }), /法律内容一致性检查失败/);
      assert.equal(await readFile(path.join(dist, 'release-manifest.json'), 'utf8'), previous);
    });
  }
});

test('Android 正文、资源或批准资料偏移均会阻止集成构建', async (t) => {
  for (const relative of ['app/src/main/assets/legal/privacy.md', 'app/src/main/assets/legal/terms.md', 'app/src/main/assets/legal/help.md', 'app/src/main/assets/legal/metadata.properties', 'app/src/main/res/values/legal_strings.xml', 'app/src/main/res/values-en/privacy_notice.xml', 'release/legal-approval.properties']) {
    await t.test(relative, async (subtest) => {
      const { projectRoot, repositoryRoot } = await fixture(subtest, { repository: true });
      await verifyRepositoryConsistency({ projectRoot, repositoryRoot });
      const target = path.join(repositoryRoot, relative);
      await writeFile(target, `${await readFile(target, 'utf8')}\n自动测试：模拟未同步改动。\n`);
      await assert.rejects(build({ projectRoot }), /法律内容一致性检查失败/);
    });
  }
});

test('伪造或损坏的输入清单不能通过镜像一致性检查', async (t) => {
  for (const mutation of [
    manifest => { manifest.inputs[0].sha256 = '0'.repeat(64); },
    manifest => { manifest.inputs.reverse(); },
    manifest => { manifest.inputs[0].path = '../私有配置'; },
    manifest => { manifest.schemaVersion = 99; },
    manifest => { manifest.inputs = []; manifest.androidLegalContentSha256 = sha256(''); },
  ]) {
    const { projectRoot } = await fixture(t);
    const target = path.join(projectRoot, 'legal/android-content-manifest.json');
    const manifest = JSON.parse(await readFile(target, 'utf8'));
    mutation(manifest);
    await writeFile(target, JSON.stringify(manifest));
    await assert.rejects(verifyLegalMirror({ projectRoot }), /法律内容一致性检查失败/);
  }
});
