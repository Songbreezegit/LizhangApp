#!/usr/bin/env python3
"""本地归档防护测试；不连接服务器，不运行系统管理命令。"""

import gzip
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('lizhang_release', Path(__file__).with_name('release.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ArchiveSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='.archive-test-', dir=Path(__file__).parent)
        self.root = Path(self.temporary.name).resolve()
        self.package = self.root / 'site.tar.gz'
        self.stage = self.root / 'stage'
        self.stage.mkdir()

    def tearDown(self):
        self.temporary.cleanup()

    def make_archive(self, extra=()):
        with tarfile.open(self.package, 'w:gz') as handle:
            root = tarfile.TarInfo('.')
            root.type = tarfile.DIRTYPE
            handle.addfile(root)
            for name, data in [('index.html', b'<!doctype html><p>test</p>'), ('404.html', b'404'), ('robots.txt', b'Disallow: /')]:
                entry = tarfile.TarInfo('./' + name)
                entry.size = len(data)
                handle.addfile(entry, io.BytesIO(data))
            for entry, data in extra:
                handle.addfile(entry, io.BytesIO(data) if data is not None else None)
        return hashlib.sha256(self.package.read_bytes()).hexdigest()

    def expect_invalid(self, entry, data=None):
        digest = self.make_archive([(entry, data)])
        with self.assertRaises((RuntimeError, FileExistsError)):
            release.extract(self.package, digest, self.stage)

    def test_valid_archive_and_manifest(self):
        digest = self.make_archive()
        manifest = release.extract(self.package, digest, self.stage)
        self.assertEqual(set(manifest['files']), {'index.html', '404.html', 'robots.txt'})
        self.assertEqual(release.verify_release(self.stage, digest), manifest)
        self.assertFalse((self.stage / 'archive.tar').exists())

    def test_checksum_rejection(self):
        self.make_archive()
        with self.assertRaisesRegex(RuntimeError, 'SHA-256'):
            release.extract(self.package, '0' * 64, self.stage)

    def test_path_escape_rejection(self):
        entry = tarfile.TarInfo('../escape.html')
        entry.size = 1
        self.expect_invalid(entry, b'x')
        self.assertFalse((self.root / 'escape.html').exists())

    def test_absolute_path_rejection(self):
        entry = tarfile.TarInfo('/escape.html')
        entry.size = 1
        self.expect_invalid(entry, b'x')

    def test_symlink_rejection(self):
        entry = tarfile.TarInfo('assets/link.css')
        entry.type = tarfile.SYMTYPE
        entry.linkname = '/etc/passwd'
        self.expect_invalid(entry)

    def test_hardlink_rejection(self):
        entry = tarfile.TarInfo('link.html')
        entry.type = tarfile.LNKTYPE
        entry.linkname = 'index.html'
        self.expect_invalid(entry)

    def test_duplicate_path_rejection(self):
        entry = tarfile.TarInfo('index.html')
        entry.size = 1
        self.expect_invalid(entry, b'x')

    def test_source_document_rejection(self):
        entry = tarfile.TarInfo('privacy.md')
        entry.size = 1
        self.expect_invalid(entry, b'x')

    def test_source_directory_rejection(self):
        entry = tarfile.TarInfo('deploy/index.html')
        entry.size = 1
        self.expect_invalid(entry, b'x')

    def test_javascript_rejection(self):
        entry = tarfile.TarInfo('assets/script.js')
        entry.size = 1
        self.expect_invalid(entry, b'x')

    def test_hidden_file_rejection(self):
        entry = tarfile.TarInfo('.env')
        entry.size = 1
        self.expect_invalid(entry, b'x')

    def test_device_rejection(self):
        entry = tarfile.TarInfo('device.txt')
        entry.type = tarfile.CHRTYPE
        self.expect_invalid(entry)

    def test_content_tamper_rejection(self):
        digest = self.make_archive()
        release.extract(self.package, digest, self.stage)
        (self.stage / 'public' / 'index.html').write_bytes(b'changed')
        with self.assertRaisesRegex(RuntimeError, '内容已变化'):
            release.verify_release(self.stage, digest)

    def test_decompression_bomb_rejection(self):
        with gzip.open(self.package, 'wb') as handle:
            for _ in range(61 * 16):
                handle.write(b'0' * 65536)
        digest = hashlib.sha256(self.package.read_bytes()).hexdigest()
        with self.assertRaisesRegex(RuntimeError, '60 MiB'):
            release.extract(self.package, digest, self.stage)

    def test_archive_entry_limit(self):
        with tarfile.open(self.package, 'w:gz') as handle:
            for _ in range(5001):
                entry = tarfile.TarInfo('.')
                entry.type = tarfile.DIRTYPE
                handle.addfile(entry)
        digest = hashlib.sha256(self.package.read_bytes()).hexdigest()
        with self.assertRaisesRegex(RuntimeError, '5000'):
            release.extract(self.package, digest, self.stage)


if __name__ == '__main__':
    unittest.main(verbosity=2)
