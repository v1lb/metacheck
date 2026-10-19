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
for label, ok in checks:
    print(('PASS' if ok else 'FAIL') + '  ' + label)
if not all(ok for _, ok in checks):
    sys.exit(1)
print('Static preflight passed; emulator, release signing, billing verification and Play review remain separate.')
