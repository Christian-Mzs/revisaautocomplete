import io
import json
import pathlib
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import prepare_runtime as runtime


def archive(entries):
    out = io.BytesIO()
    with tarfile.open(fileobj=out, mode='w:gz') as tar:
        for name, data, kind in entries:
            info = tarfile.TarInfo(name)
            info.mode = 0o755
            if kind == 'symlink':
                info.type = tarfile.SYMTYPE; info.linkname = data
                tar.addfile(info)
            elif kind == 'hardlink':
                info.type = tarfile.LNKTYPE; info.linkname = data
                tar.addfile(info)
            else:
                data = data.encode(); info.size = len(data)
                tar.addfile(info,io.BytesIO(data))
    return out.getvalue()


class RuntimePackagingTest(unittest.TestCase):
    def test_rejects_path_traversal(self):
        for path in ['/etc/passwd','../escape','x/../../escape']:
            with self.assertRaises(ValueError): runtime.safe_path(path)
        self.assertEqual('bin/busybox',runtime.safe_path('./bin/busybox'))

    def test_cached_checksum_mismatch_removes_corrupt_download(self):
        import hashlib
        with tempfile.TemporaryDirectory() as tmp, patch.object(runtime,'CACHE',pathlib.Path(tmp)):
            url = 'https://example.invalid/a'
            file = runtime.CACHE / hashlib.sha256(url.encode()).hexdigest()
            file.write_bytes(b'bad')
            with self.assertRaises(ValueError): runtime.fetch(url,'0'*64)
            self.assertFalse(file.exists())

    def test_packages_modes_relative_links_sources_and_provenance(self):
        codex = archive([('codex-aarch64-unknown-linux-musl','ELF','file')])
        alpine = archive([('bin/busybox','ELF','file'),('bin/sh','/bin/busybox','symlink'),
                          ('bin/copy','bin/busybox','hardlink'),
                          ('lib/apk/db/installed','P:busybox\nV:1.37.0-r1\nL:GPL-2.0-only\n','file')])
        talloc = archive([('talloc-2.1.14/talloc.h','LGPL notice','file')])
        def fake_fetch(url,sha=None):
            if 'codex-aarch64' in url: return codex
            if url.endswith('.sha256'): return b'0'*64
            if 'alpine-minirootfs' in url: return alpine
            if 'talloc-2.1.14.tar.gz' in url: return talloc
            return b'upstream source/license'
        def fake_build(proot_source, talloc_source, native):
            for name in ['libproot.so','libproot-loader.so','libruntime-probe.so']:
                (native/name).write_bytes(b'ELF')
        with tempfile.TemporaryDirectory() as tmp:
            base = pathlib.Path(tmp)
            with patch.object(runtime,'ASSETS',base/'assets'), patch.object(runtime,'NATIVE',base/'native'), patch.object(runtime,'fetch',fake_fetch), patch.object(runtime,'build_native',fake_build):
                runtime.prepare()
                entries = json.loads((runtime.ASSETS/'runtime/manifest.json').read_text())
                sh = next(e for e in entries if e['path']=='bin/sh')
                self.assertEqual('busybox',sh['target'])
                self.assertEqual(0o755,next(e for e in entries if e['path']=='bin/busybox')['mode'])
                self.assertTrue((runtime.NATIVE/'libcodex.so').is_file())
                self.assertTrue((runtime.ASSETS/'licenses/BusyBox-source.tar.bz2').is_file())
                self.assertTrue((runtime.ASSETS/'licenses/talloc-LICENSE.txt').is_file())
                self.assertTrue((runtime.ASSETS/'licenses/Runtime-build-scripts.zip').is_file())
                self.assertTrue((runtime.ASSETS/'runtime/provenance.json').is_file())

if __name__ == '__main__': unittest.main()
