#!/usr/bin/env python3
"""Local static release preflight. Does NOT replace Play Console review or Android tests."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'app/build.gradle.kts'
MANIFEST = ROOT / 'app/src/main/AndroidManifest.xml'
CI = ROOT / '.github/workflows/ci.yml'
checks = []

def check(label, passed):
    checks.append((label, bool(passed)))

cfg = APP.read_text(encoding='utf-8')
manifest = ET.parse(MANIFEST).getroot()
ns = '{http://schemas.android.com/apk/res/android}'
permissions = {p.get(ns+'name') for p in manifest.findall('uses-permission')}
check('targetSdk >= 36 (2026 Play requirement)', bool(re.search(r'\btargetSdk\s*=\s*(3[6-9]|[4-9][0-9])\b', cfg)))
app_id = re.search(r'\bapplicationId\s*=\s*"([^"]+)"', cfg)
check('Non-example applicationId', bool(app_id and app_id.group(1).count('.') >= 2 and not app_id.group(1).startswith(('com.example', 'org.example'))))
check('No broad media/storage permissions', not permissions.intersection({
    'android.permission.MANAGE_EXTERNAL_STORAGE',
    'android.permission.READ_EXTERNAL_STORAGE',
    'android.permission.WRITE_EXTERNAL_STORAGE',
    'android.permission.READ_MEDIA_IMAGES',
    'android.permission.READ_MEDIA_VIDEO',
}))
application = manifest.find('application')
check('Backup disabled', application is not None and application.get(ns+'allowBackup') == 'false')
check('Cleartext disabled', application is not None and application.get(ns+'usesCleartextTraffic') == 'false')
check('Policy document exists', (ROOT / 'PRIVACY_POLICY.md').is_file())
check('Release checklist exists', (ROOT / 'PLAY_RELEASE_CHECKLIST.md').is_file())
check('CI runs tests and lint', all(token in CI.read_text(encoding='utf-8') for token in ['testDebugUnitTest','lintDebug','assembleDebug','assembleDebugAndroidTest']))
check('Emulator workflow present', (ROOT / '.github/workflows/android-instrumentation.yml').is_file())
policy_text = (ROOT / 'PRIVACY_POLICY.md').read_text(encoding='utf-8') if (ROOT / 'PRIVACY_POLICY.md').is_file() else ''
check('Privacy policy has no unresolved placeholders', not re.search(r'\[(дата|имя|email|name|date)[^\]]*\]', policy_text, re.I))
check('Legal documents folder exists', all((ROOT / 'legal' / n).is_file() for n in ['PRIVACY_POLICY_RU.md', 'PRIVACY_POLICY_EN.md', 'TERMS_RU.md', 'TERMS_EN.md']))
check('AAB cannot be built without own signing key', 'bundleRelease' in cfg and 'GradleException' in cfg)
check('Third-party licenses listed', (ROOT / 'THIRD_PARTY_LICENSES.md').is_file())
# Аудит, п. 19: каждый поставщик карты из MapTiles.kt назван в политике и условиях на обоих языках.
tiles_src = (ROOT / 'app/src/main/java/com/example/metacheck/ui/map/MapTiles.kt').read_text(encoding='utf-8')
providers = re.findall(r'^\s*[A-Z_]+\("([^"]+)",', tiles_src, re.M)
legal_texts = [(ROOT / 'legal' / n).read_text(encoding='utf-8') for n in
               ['PRIVACY_POLICY_RU.md', 'PRIVACY_POLICY_EN.md', 'TERMS_RU.md', 'TERMS_EN.md']]
check('Every map provider is named in privacy policy and terms (RU/EN)',
      providers and all(p in t for p in providers for t in legal_texts))
workflows = ROOT / '.github/workflows'
release_wf = [(workflows / n).read_text(encoding='utf-8') for n in ('release-aab.yml', 'build-apk.yml') if (workflows / n).is_file()]
def code_lines(text):
    return [l for l in text.splitlines() if not l.lstrip().startswith('#')]
check('Release workflows do not skip lint (-x lint*)',
      release_wf and not any(re.search(r'-x\s+lint', l) for t in release_wf for l in code_lines(t)))
check('Release AAB workflow runs tests and lintRelease before bundleRelease',
      any('lintRelease' in t and 'testReleaseUnitTest' in t and t.index('lintRelease') < t.index('bundleRelease')
          for t in release_wf if 'bundleRelease' in t))
for label, ok in checks:
    print(('PASS' if ok else 'FAIL') + '  ' + label)
if not all(ok for _, ok in checks):
    sys.exit(1)
print('Static preflight passed; emulator, release signing, billing verification and Play review remain separate.')
