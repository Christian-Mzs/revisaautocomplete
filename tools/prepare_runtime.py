#!/usr/bin/env python3
"""Prepare pinned APK assets, on the build machine (not on the phone). No npm."""
import hashlib, io, json, pathlib, posixpath, tarfile, urllib.request, urllib.error, zipfile, time
from build_runtime_native import build_native, PROOT_REV, PROOT_SHA, TALLOC_SHA, NDK_VERSION
ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
NATIVE = ROOT / 'app/src/main/jniLibs/arm64-v8a'
CACHE = ROOT / '.runtime-cache'
ALPINE = '3.23.6'
CODEX = '0.160.0'
CODEX_SHA = '883620139925f677e5a12c95ba87a1d7f421388ebb797b1c10aba42a04ede9d7'
def fetch(url, sha=None):
    CACHE.mkdir(exist_ok=True)
    p = CACHE / hashlib.sha256(url.encode()).hexdigest()
    if not p.exists():
        req = urllib.request.Request(url, headers={'User-Agent':'Revisa-runtime-builder/1'})
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=90) as r:
                    data = r.read()
                break
            except (urllib.error.URLError, OSError):
                if attempt == 2: raise
                time.sleep(2 ** attempt)
        p.write_bytes(data)
    data = p.read_bytes()
    if sha and hashlib.sha256(data).hexdigest() != sha:
        p.unlink()
        raise ValueError('SHA256 mismatch: ' + url)
    return data

def safe_path(name):
    name = posixpath.normpath(name)
    if name == '.': return ''
    if name.startswith('/') or name == '..' or name.startswith('../'):
        raise ValueError('Unsafe archive path: ' + name)
    return name

def prepare():
    NATIVE.mkdir(parents=True, exist_ok=True)
    (ASSETS / 'runtime').mkdir(parents=True, exist_ok=True)
    (ASSETS / 'licenses').mkdir(parents=True, exist_ok=True)
    proot = fetch(f'https://github.com/termux/proot/archive/{PROOT_REV}.tar.gz', PROOT_SHA)
    talloc = fetch('https://download.samba.org/pub/talloc/talloc-2.1.14.tar.gz', TALLOC_SHA)
    build_native(proot, talloc, NATIVE)
    codex = fetch(f'https://github.com/openai/codex/releases/download/rust-v{CODEX}/codex-aarch64-unknown-linux-musl.tar.gz', CODEX_SHA)
    with tarfile.open(fileobj=io.BytesIO(codex)) as t:
        candidates = [m for m in t.getmembers() if m.isfile() and pathlib.PurePosixPath(m.name).name == 'codex-aarch64-unknown-linux-musl']
        if len(candidates) != 1: raise ValueError('Unexpected Codex release layout')
        (NATIVE / 'libcodex.so').write_bytes(t.extractfile(candidates[0]).read())
    base = f'https://dl-cdn.alpinelinux.org/alpine/v3.23/releases/aarch64/alpine-minirootfs-{ALPINE}-aarch64.tar.gz'
    checksum = fetch(base + '.sha256').decode().split()[0]
    alpine = fetch(base, checksum)
    manifest = []
    hardlinks = []
    regular = {}
    with tarfile.open(fileobj=io.BytesIO(alpine)) as t, zipfile.ZipFile(ASSETS/'runtime/rootfs.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        for m in t.getmembers():
            name = safe_path(m.name)
            if not name: continue
            if m.isdir():
                manifest.append(dict(path=name, kind='directory', mode=m.mode & 0o777))
            elif m.isfile():
                data = t.extractfile(m).read()
                regular[name] = data
                z.writestr(name, data)
                manifest.append(dict(path=name, kind='file', mode=m.mode & 0o777))
            elif m.issym():
                target = posixpath.normpath(m.linkname.lstrip('/')) if m.linkname.startswith('/') else posixpath.normpath(posixpath.join(posixpath.dirname(name),m.linkname))
                safe_path(target)
                # Convert absolute Linux symlinks to relative links confined to the rootfs.
                link = posixpath.relpath(target, posixpath.dirname(name) or '.')
                manifest.append(dict(path=name, kind='symlink', target=link, mode=0))
            elif m.islnk(): hardlinks.append((name, safe_path(m.linkname),m.mode & 0o777))
            # Do not ship device nodes or sockets.
        for name, target, mode in hardlinks:
            z.writestr(name,regular[target])
            manifest.append(dict(path=name,kind='file',mode=mode))
    database = regular.get('lib/apk/db/installed', b'').decode()
    (ASSETS/'licenses/Alpine-packages.txt').write_text(database.rstrip()+'\n')
    # Retain matching sources for the GPL/LGPL executable components shipped in the APK.
    sources = {
    }
    (ASSETS/'licenses/PRoot-source.tar.gz').write_bytes(proot)
    (ASSETS/'licenses/talloc-source.tar.gz').write_bytes(talloc)
    # Include the build recipe/configuration alongside exact upstream sources.
    with zipfile.ZipFile(ASSETS/'licenses/Runtime-build-scripts.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        for path in ['tools/prepare_runtime.py','tools/build_runtime_native.py',
                     'tools/native/talloc-replace.h','tools/native/runtime_probe.c']:
            z.write(ROOT/path,path)
    for obsolete in ['PRoot-build-scripts.tar.gz','build-proot-android-LICENSE.txt']:
        (ASSETS/'licenses'/obsolete).unlink(missing_ok=True)
    for record in database.split('\n\n'):
        fields = dict(line.split(':',1) for line in record.splitlines() if ':' in line)
        if fields.get('P') == 'busybox':
            version = fields['V'].split('-r')[0]
            sources['BusyBox-source.tar.bz2'] = f'https://busybox.net/downloads/busybox-{version}.tar.bz2'
    for name,url in sources.items(): (ASSETS/'licenses'/name).write_bytes(fetch(url))
    (ASSETS/'runtime/manifest.json').write_text(json.dumps(manifest))
    (ASSETS/'runtime/version.txt').write_text(f'alpine-{ALPINE}_codex-{CODEX}_proot-{PROOT_REV[:12]}_v5\n')
    licenses = {
        'Codex-LICENSE.txt':f'https://raw.githubusercontent.com/openai/codex/rust-v{CODEX}/LICENSE',
        'Codex-NOTICE.txt':f'https://raw.githubusercontent.com/openai/codex/rust-v{CODEX}/NOTICE',
        'PRoot-COPYING.txt':f'https://raw.githubusercontent.com/termux/proot/{PROOT_REV}/COPYING',
        'talloc-LICENSE.txt':'https://raw.githubusercontent.com/samba-team/samba/master/lib/talloc/talloc.h',
        'musl-COPYRIGHT.txt':'https://git.musl-libc.org/cgit/musl/plain/COPYRIGHT',
    }
    licenses.pop('talloc-LICENSE.txt')
    with tarfile.open(fileobj=io.BytesIO((ASSETS/'licenses/talloc-source.tar.gz').read_bytes())) as t:
        members = [m for m in t.getmembers() if m.name.endswith('/talloc.h')]
        (ASSETS/'licenses/talloc-LICENSE.txt').write_bytes(t.extractfile(members[0]).read())
    for name,url in licenses.items():
        target = ASSETS/'licenses'/name
        if not target.is_file(): target.write_bytes(fetch(url))
    (ASSETS/'runtime/provenance.json').write_text(json.dumps(dict(alpine_version=ALPINE,alpine_sha256=checksum,codex_version=CODEX,codex_sha256=CODEX_SHA,proot_repository='termux/proot',proot_revision=PROOT_REV,proot_source_sha256=PROOT_SHA,talloc_sha256=TALLOC_SHA,ndk_version=NDK_VERSION,guest_abi='arm64-only',proot_binary_sha256=hashlib.sha256((NATIVE/'libproot.so').read_bytes()).hexdigest()),indent=2))
    print('Runtime assets ready. ARM64 only.')
if __name__ == '__main__': prepare()
