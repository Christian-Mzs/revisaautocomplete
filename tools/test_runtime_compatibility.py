#!/usr/bin/env python3
"""Regression: PRoot must emulate statx when an inherited filter returns ENOSYS.

This Linux test deliberately reproduces one Android compatibility condition.
It does not claim to test Android's SELinux, ARM64 or the complete Codex login.
"""
import os
import pathlib
import subprocess
import tempfile
import io
import tarfile
import unittest
from prepare_runtime import fetch, CODEX
from build_runtime_native import build_native, PROOT_REV, PROOT_SHA, TALLOC_SHA, ROOT

class CompatibilityTest(unittest.TestCase):
    def test_inherited_statx_enosys_is_emulated(self):
        output = ROOT / '.runtime-cache' / 'host-bin'
        build_native(fetch(f'https://github.com/termux/proot/archive/{PROOT_REV}.tar.gz', PROOT_SHA),
                     fetch('https://download.samba.org/pub/talloc/talloc-2.1.14.tar.gz', TALLOC_SHA), output, host=True)
        deny = output / 'deny-statx'
        subprocess.run(['gcc', str(ROOT/'tools/native/deny_statx.c'), '-o', str(deny)], check=True)
        with tempfile.TemporaryDirectory() as tmp:
            probe = str(output/'libruntime-probe.so')
            control = subprocess.run([str(deny), probe, tmp], capture_output=True, text=True, timeout=30)
            self.assertEqual(1, control.returncode, control.stderr)
            self.assertIn('statx(path)', control.stderr)
            self.assertIn('errno=38', control.stderr)
            env = os.environ.copy()
            env.update(PROOT_LOADER=str(output/'libproot-loader.so'), PROOT_TMP_DIR=tmp, PROOT_NO_SECCOMP='1')
            result = subprocess.run([str(deny), str(output/'libproot.so'), '--kill-on-exit',
                '-0','--link2symlink','-L','--sysvipc','-r','/','-w',tmp, probe,tmp],
                env=env, capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn('statx(path): OK', result.stdout)
            self.assertIn('statx(fd): OK', result.stdout)
            self.assertIn('flock: OK', result.stdout)
            print(result.stdout)

            # Exercise the real Rust CLI initialization as well, with no account
            # or inherited API credentials. This x86 binary is CI-only, not in APK.
            codex_archive = fetch(f'https://github.com/openai/codex/releases/download/rust-v{CODEX}/codex-x86_64-unknown-linux-musl.tar.gz',
                '306865417d4ee7a927785852910a527f41e1e159add390ac5ae3accb67d44a13')
            codex = output/'codex-host'
            with tarfile.open(fileobj=io.BytesIO(codex_archive)) as tar:
                members = [m for m in tar.getmembers() if m.isfile() and pathlib.PurePosixPath(m.name).name == 'codex-x86_64-unknown-linux-musl']
                self.assertEqual(1,len(members))
                codex.write_bytes(tar.extractfile(members[0]).read())
            codex.chmod(0o755)
            private = pathlib.Path(tmp)/'codex-home'
            private.mkdir(mode=0o700)
            (private/'config.toml').write_text('cli_auth_credentials_store="file"\nmodel="gpt-6-luna"\nmodel_reasoning_effort="low"\napproval_policy="never"\nsandbox_mode="read-only"\n[features]\nshell_tool=false\n')
            cli_env = dict(PATH='/usr/bin:/bin',HOME=tmp,CODEX_HOME=str(private),TMPDIR=tmp,
                PROOT_LOADER=env['PROOT_LOADER'],PROOT_TMP_DIR=tmp,PROOT_NO_SECCOMP='1')
            cli = subprocess.run([str(deny), str(output/'libproot.so'), '--kill-on-exit',
                '-0','--link2symlink','-L','--sysvipc','-r','/','-w',tmp,
                str(codex),'login','status'],
                env=cli_env,capture_output=True,text=True,timeout=30)
            diagnostics = cli.stdout + cli.stderr
            self.assertEqual(1,cli.returncode,diagnostics)
            self.assertIn('Not logged in',diagnostics)
            self.assertNotIn('Error loading configuration',diagnostics)
            self.assertNotIn('Function not implemented',diagnostics)
            print('Codex CLI initialization with statx returning ENOSYS: OK (not logged in, as expected).')

            # Positive and negative controls prove these are typed, recognized
            # configuration keys, rather than silently ignored unknown keys.
            base = [str(codex)]
            overrides = ['-c', 'model_reasoning_summary="none"',
                         '-c', 'model_verbosity="low"']
            accepted = subprocess.run(base + overrides + ['login', 'status'],
                env=cli_env, capture_output=True, text=True, timeout=30)
            self.assertEqual(1, accepted.returncode, accepted.stdout + accepted.stderr)
            self.assertIn('Not logged in', accepted.stdout + accepted.stderr)
            self.assertNotIn('Error loading configuration', accepted.stdout + accepted.stderr)
            for key in ('model_reasoning_summary', 'model_verbosity'):
                with self.subTest(key=key):
                    rejected = subprocess.run(base + ['-c', f'{key}="invalid-revisa-test"', 'login', 'status'],
                        env=cli_env, capture_output=True, text=True, timeout=30)
                    self.assertNotEqual(0, rejected.returncode)
                    self.assertIn('invalid-revisa-test', rejected.stdout + rejected.stderr)
                    self.assertNotIn('Not logged in', rejected.stdout + rejected.stderr)
            print(f'Codex {CODEX}: reasoning_summary=none and verbosity=low accepted; invalid enum controls rejected.')


if __name__ == '__main__': unittest.main()
