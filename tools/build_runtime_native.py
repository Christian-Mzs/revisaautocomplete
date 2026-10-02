#!/usr/bin/env python3
"""Build the Termux PRoot family used by Acode, from pinned corresponding sources.

Only ARM64 guest execution is required. Drop the optional 32-bit loader from
upstream arch.h and add a missing string.h include for Clang; retain the ARM64
loader and ptrace/statx compatibility code.
No Acode editor, PTY server, Cordova, Node or storage access is included.
"""
import io
import os
import pathlib
import shutil
import subprocess
import tarfile

PROOT_REV = 'a179d3e8a4e045aaa1fb8cc3284f23509d96d353'
PROOT_SHA = 'f25791d84702daedcdfd5fb0d9d5247892463b225266df36611e1fa0c90ba272'
TALLOC_SHA = 'b185602756a628bac507fa8af8b9df92ace69d27c0add5dab93190ad7c3367ce'
NDK_VERSION = '27.2.12479018'
ROOT = pathlib.Path(__file__).resolve().parents[1]

def verify_bionic_tls(path, required=False):
    headers = subprocess.check_output(['readelf', '-W', '-l', str(path)], text=True)
    segments = [line.split() for line in headers.splitlines() if line.lstrip().startswith('TLS ')]
    if required and not segments:
        raise RuntimeError(f'{path.name}: expected a TLS segment in the static Bionic probe')
    for fields in segments:
        alignment = int(fields[-1], 0)
        if alignment < 64:
            raise RuntimeError(f'{path.name}: ARM64 Bionic TLS alignment {alignment} is below 64')
        print(f'{path.name}: ARM64 Bionic TLS alignment verified: {alignment} bytes')

def build_native(proot_source, talloc_source, destination, host=False):
    build = ROOT / '.runtime-cache' / ('native-host' if host else 'native-arm64')
    shutil.rmtree(build, ignore_errors=True)
    build.mkdir(parents=True)
    for archive in [proot_source, talloc_source]:
        with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
            tar.extractall(build, filter='data')
    source = build / ('proot-' + PROOT_REV) / 'src'
    talloc = build / 'talloc-2.1.14'
    include = build / 'include'
    include.mkdir()
    shutil.copyfile(ROOT / 'tools/native/talloc-replace.h', include / 'replace.h')
    shutil.copyfile(talloc / 'talloc.h', include / 'talloc.h')

    if host:
        cc, ar, strip, objcopy, objdump = 'gcc', 'ar', 'strip', 'objcopy', 'objdump'
    else:
        sdk = os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', ''))
        ndk = pathlib.Path(os.environ.get('ANDROID_NDK_HOME', str(pathlib.Path(sdk) / 'ndk' / NDK_VERSION)))
        binaries = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
        cc = str(binaries / 'aarch64-linux-android26-clang')
        ar, strip, objcopy, objdump = [str(binaries / name) for name in ['llvm-ar', 'llvm-strip', 'llvm-objcopy', 'llvm-objdump']]
        if not pathlib.Path(cc).is_file():
            raise RuntimeError(f'Install Android NDK {NDK_VERSION}, or set ANDROID_NDK_HOME. Missing: {cc}')

    subprocess.run([cc, '-D_GNU_SOURCE', '-fPIC', '-O2', '-I'+str(include), '-I'+str(talloc),
                    '-c', str(talloc/'talloc.c'), '-o', str(build/'talloc.o')], check=True)
    subprocess.run([ar, 'rcs', str(build/'libtalloc.a'), str(build/'talloc.o')], check=True)
    arch = source/'arch.h'
    arch.write_text(arch.read_text().replace('#define HAS_LOADER_32BIT true', '/* Revisa: ARM64 guest only; no 32-bit loader. */'))
    ashmem = source/'extension/ashmem_memfd/ashmem_memfd.c'
    ashmem.write_text('#include <string.h> /* Revisa: explicit strcmp/memset declarations for Clang. */\n'+ashmem.read_text())
    environment = os.environ.copy()
    environment['PROOT_UNBUNDLE_LOADER'] = '.'
    environment['CFLAGS'] = '-O2 -fPIE -I'+str(include)
    environment['LDFLAGS'] = '-L'+str(build)+' -ltalloc -pie -Wl,-z,noexecstack,-z,max-page-size=16384'
    subprocess.run(['make', '-C', str(source), '-j2', 'CC='+cc, 'AR='+ar,
        'STRIP='+strip, 'OBJCOPY='+objcopy, 'OBJDUMP='+objdump,
        'GIT=true', 'proot'], check=True, env=environment)
    destination.mkdir(parents=True, exist_ok=True)
    for src, target in [(source/'proot', 'libproot.so'), (source/'loader/loader', 'libproot-loader.so')]:
        shutil.copyfile(src, destination/target)
        (destination/target).chmod(0o755)
        subprocess.run([strip, str(destination/target)], check=True)
    # API 26 defaults to emulated TLS in Clang. The static executable must use
    # real ELF TLS for the aligned object to affect the Bionic TLS segment.
    probe_flags = [] if host else ['-fno-emulated-tls']
    subprocess.run([cc, '-D_GNU_SOURCE', '-O2'] + probe_flags + ['-static', '-Wl,-z,max-page-size=16384',
        str(ROOT/'tools/native/runtime_probe.c'), '-o', str(destination/'libruntime-probe.so')], check=True)
    subprocess.run([strip, str(destination/'libruntime-probe.so')], check=True)
    if not host:
        verify_bionic_tls(destination/'libproot.so')
        verify_bionic_tls(destination/'libruntime-probe.so', required=True)
    print('Built PRoot and syscall probe from pinned sources (' + ('host' if host else 'Android ARM64') + ').')
