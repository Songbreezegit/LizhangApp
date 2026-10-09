#!/usr/bin/env python3
"""以独立部署用户发布静态产物；不修改 Nginx、DNS、防火墙或证书。"""

import hashlib
import gzip
import json
import os
from pathlib import Path, PurePosixPath
try:
    import pwd
except ImportError:
    pwd = None  # 允许 Windows 导入归档验证函数；正式发布仍仅支持 Linux。
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

SITE = Path('/srv/lizhang-docs/site')
RELEASES = SITE / 'releases'
MAX_BYTES = 50 * 1024 * 1024
EXTENSIONS = {'.html', '.css', '.svg', '.png', '.jpg', '.jpeg', '.webp', '.ico', '.txt', '.json', '.xml', '.woff2'}
FORBIDDEN = {'deploy', 'scripts', 'content', 'docs', 'node_modules'}


def fail(message):
    raise RuntimeError(message)


def check_path(path):
    """固定管理路径的任一父级都不能是符号链接。"""
    for part in [path, *path.parents]:
        if part.is_symlink():
            fail(f'拒绝符号链接管理目录：{part}')


def normalize(name):
    if name.startswith('./'):
        name = name[2:]
    name = name.rstrip('/')
    if name in ('', '.'):
        return None
    parts = name.split('/')
    if name.startswith('/') or '\\' in name or any(p in ('', '.', '..') or p.startswith('.') or p.lower() in FORBIDDEN for p in parts):
        fail(f'压缩包包含禁止路径：{name!r}')
    if any(ord(c) < 32 or ord(c) == 127 for c in name) or len(name.encode('utf-8')) > 512:
        fail('压缩包包含过长路径或控制字符')
    return str(PurePosixPath(name))


def digest_file(path):
    digest = hashlib.sha256()
    with path.open('rb') as handle:
        for block in iter(lambda: handle.read(65536), b''):
            digest.update(block)
    return digest.hexdigest()


def tree_manifest(public):
    check_path(public)
    files = {}
    total = 0
    for folder, dirs, names in os.walk(public, followlinks=False):
        for name in dirs + names:
            path = Path(folder) / name
            mode = path.lstat().st_mode
            if stat.S_ISLNK(mode) or not (stat.S_ISDIR(mode) or stat.S_ISREG(mode)):
                fail('发布目录存在链接或特殊文件')
            relative = path.relative_to(public).as_posix()
            normalize(relative)
            if stat.S_ISREG(mode):
                if path.suffix.lower() not in EXTENSIONS:
                    fail(f'发布目录存在非静态文件：{relative}')
                size = path.stat().st_size
                total += size
                if total > MAX_BYTES or len(files) >= 5000:
                    fail('发布内容超出 50 MiB 或 5000 个文件限制')
                files[relative] = {'size': size, 'sha256': digest_file(path)}
    for required in ('index.html', '404.html', 'robots.txt'):
        if required not in files:
            fail(f'缺少必需静态文件：{required}')
    return dict(sorted(files.items()))


def verify_release(release, expected_sha):
    check_path(release)
    metadata = release / 'manifest.json'
    if not metadata.is_file() or metadata.is_symlink():
        fail('发布版本缺少可信路径中的校验清单')
    if metadata.stat().st_size > 2 * 1024 * 1024:
        fail('校验清单过大')
    manifest = json.loads(metadata.read_text(encoding='utf-8'))
    if manifest.get('archive_sha256') != expected_sha:
        fail('发布版本摘要不一致')
    if tree_manifest(release / 'public') != manifest.get('files'):
        fail('发布版本内容已变化，拒绝复用或回滚')
    return manifest


def extract(archive, expected_sha, stage):
    if archive.is_symlink() or not archive.is_file() or archive.stat().st_size > 20 * 1024 * 1024:
        fail('发布包必须是普通文件，且不超过 20 MiB')
    if digest_file(archive) != expected_sha:
        fail('发布包 SHA-256 校验失败')
    public = stage / 'public'
    public.mkdir(mode=0o755)
    unpacked = stage / 'archive.tar'
    with gzip.open(archive, 'rb') as source, unpacked.open('xb') as target:
        expanded_bytes = 0
        for block in iter(lambda: source.read(65536), b''):
            expanded_bytes += len(block)
            if expanded_bytes > MAX_BYTES + 10 * 1024 * 1024:
                fail('归档解压流超过 60 MiB，拒绝解析可能的压缩炸弹')
            target.write(block)
    with tarfile.open(unpacked, mode='r:') as package:
        members = []
        seen = set()
        total = 0
        entry_count = 0
        # 逐项检查，不使用 extractall；拒绝链接、设备、重复路径和路径逃逸。
        for member in package:
            entry_count += 1
            if entry_count > 5000:
                fail('压缩包项超过 5000 个')
            name = normalize(member.name)
            if name is None:
                if not member.isdir():
                    fail('压缩包根目录项类型错误')
                continue
            if len(members) >= 5000 or name in seen:
                fail('压缩包项过多或路径重复')
            seen.add(name)
            if not (member.isdir() or member.isfile()):
                fail('压缩包不允许链接或特殊文件')
            if member.isfile():
                if PurePosixPath(name).suffix.lower() not in EXTENSIONS or member.size < 0:
                    fail(f'发布包包含非静态文件：{name}')
                total += member.size
                if total > MAX_BYTES:
                    fail('发布包解压总大小超过 50 MiB')
            members.append((member, name))
        if shutil.disk_usage(stage).free < MAX_BYTES * 2:
            fail('剩余磁盘空间不足 100 MiB')
        for member, name in members:
            destination = public / name
            if member.isdir():
                destination.mkdir(mode=0o755, parents=True, exist_ok=True)
                continue
            destination.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
            source = package.extractfile(member)
            if source is None:
                fail('无法读取压缩包文件')
            with source, destination.open('xb') as target:
                shutil.copyfileobj(source, target)
            if destination.stat().st_size != member.size:
                fail('解压文件大小不符')
    unpacked.unlink()
    manifest = {'archive_sha256': expected_sha, 'files': tree_manifest(public)}
    (stage / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return manifest


def atomic_link(name, target):
    link = SITE / name
    if link.exists() and not link.is_symlink():
        fail(f'{name} 必须为管理符号链接')
    temporary = SITE / f'.{name}-{os.getpid()}'
    if temporary.exists() or temporary.is_symlink():
        fail('原子切换临时路径已存在')
    try:
        temporary.symlink_to(target)
        os.replace(temporary, link)
    finally:
        if temporary.is_symlink():
            temporary.unlink()


def current_target():
    current = SITE / 'current'
    if not current.is_symlink():
        fail('当前版本链接未由初始化脚本建立')
    target = os.readlink(current)
    if not re.fullmatch(r'releases/(initial|[a-f0-9]{64})/public', target):
        fail('当前版本链接指向非管理目录')
    check_path(SITE / target)
    return target


def health_check(manifest):
    with urllib.request.urlopen('http://127.0.0.1:8080/index.html', timeout=10) as response:
        body = response.read(MAX_BYTES + 1)
        if response.status != 200 or hashlib.sha256(body).hexdigest() != manifest['files']['index.html']['sha256']:
            fail('Nginx 首页内容与本次发布不符')


def switch_release(sha, manifest):
    old = current_target()
    new = f'releases/{sha}/public'
    subprocess.run(['/usr/sbin/nginx', '-t', '-c', '/etc/lizhang-docs/validate.conf'], check=True)
    subprocess.run(['systemctl', 'is-active', '--quiet', 'lizhang-docs.service'], check=True)
    atomic_link('current', new)
    try:
        health_check(manifest)
    except Exception:
        atomic_link('current', old)
        print('新版本健康检查失败，已原子恢复原链接。', file=sys.stderr)
        raise
    if old != new:
        atomic_link('previous', old)
    print(f'发布成功：{sha}；Nginx 配置测试、首页 HTTP 内容校验通过。')


def main():
    if len(sys.argv) not in (3, 4) or sys.argv[1] not in ('install', 'rollback'):
        fail('用法：release.py install 压缩包 SHA256，或 release.py rollback SHA256')
    mode = sys.argv[1]
    if len(sys.argv) != (4 if mode == 'install' else 3):
        fail('参数数量不正确')
    sha = sys.argv[-1]
    if not re.fullmatch(r'[a-f0-9]{64}', sha):
        fail('版本必须为小写 SHA-256')
    expected_user = Path('/etc/lizhang-docs/deploy-user').read_text(encoding='utf-8').strip()
    if pwd is None or not hasattr(os, 'geteuid'):
        fail('正式发布仅支持 Ubuntu/Linux；Windows 仅可导入静态归档验证函数')
    if os.geteuid() == 0 or pwd.getpwuid(os.geteuid()).pw_name != expected_user:
        fail('必须以已配置的独立部署用户运行，不能使用 root')
    check_path(RELEASES)
    if not RELEASES.is_dir() or RELEASES.stat().st_uid != os.geteuid():
        fail('部署目录不存在或所有权不正确')
    release = RELEASES / sha
    if mode == 'rollback':
        manifest = verify_release(release, sha)
        switch_release(sha, manifest)
        return
    archive = Path(sys.argv[2])
    uploads = Path('/srv/lizhang-docs/uploads')
    check_path(uploads)
    if archive != uploads / f'{sha}.tar.gz':
        fail('发布包必须位于专用 uploads 目录且名称与摘要对应')
    stage = Path(tempfile.mkdtemp(prefix='.incoming-', dir=RELEASES))
    try:
        manifest = extract(archive, sha, stage)
        if release.exists() or release.is_symlink():
            existing = verify_release(release, sha)
            if existing != manifest:
                fail('同摘要版本存在内容冲突')
        else:
            # 固定为只读，禁止可执行位；不继承归档所有者、组或权限。
            for folder, dirs, files in os.walk(stage):
                for name in files:
                    (Path(folder) / name).chmod(0o444)
                for name in dirs:
                    (Path(folder) / name).chmod(0o555)
            stage.chmod(0o555)
            os.rename(stage, release)
        switch_release(sha, manifest)
    finally:
        if stage.exists() and stage.parent == RELEASES and not stage.is_symlink():
            for folder, dirs, files in os.walk(stage):
                Path(folder).chmod(0o700)
            shutil.rmtree(stage)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(f'部署已停止：{error}', file=sys.stderr)
        sys.exit(1)
