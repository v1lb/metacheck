#!/usr/bin/env python3
"""Проверка разрешений собранного APK (аудит, п. 20).

Исходный AndroidManifest.xml показывает не всё: библиотеки добавляют свои разрешения при слиянии
манифестов. Скрипт читает итоговый список из APK через aapt2 и сравнивает с разрешённым набором.

Запуск:
  python3 tools/check_apk_permissions.py app/build/outputs/apk/release/app-release.apk
  aapt2 dump permissions app.apk | python3 tools/check_apk_permissions.py -     (вывод aapt2 из stdin)
  python3 tools/check_apk_permissions.py path/to/merged/AndroidManifest.xml     (для AAB: итоговый манифест)
"""
import glob
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

# Каждое разрешение объяснено в политике конфиденциальности (раздел 4).
ALLOWED = {
    'android.permission.INTERNET',               # карта по кнопке, необязательный API
    'android.permission.ACCESS_NETWORK_STATE',   # osmdroid проверяет сеть перед загрузкой тайлов
    'android.permission.ACCESS_MEDIA_LOCATION',  # GPS из фото на Android 10+
    'com.android.vending.BILLING',               # покупка Pro
}
# androidx.core объявляет и использует собственное signature-разрешение для незаэкспортированных receiver.
OWN_SUFFIXES = ('.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION',)


def find_aapt2():
    home = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or ''
    found = sorted(glob.glob(os.path.join(home, 'build-tools', '*', 'aapt2')))
    if not found:
        sys.exit('aapt2 не найден: задайте ANDROID_HOME')
    return found[-1]


def read_dump(arg):
    if arg == '-':
        return sys.stdin.read()
    return subprocess.run([find_aapt2(), 'dump', 'permissions', arg],
                          check=True, capture_output=True, text=True).stdout


def parse(dump):
    package = re.search(r'^package:\s*(\S+)', dump, re.M)
    used = set()
    for line in dump.splitlines():
        if line.startswith('uses-permission'):
            m = re.search(r"name='([^']+)'", line)
            if m:
                used.add(m.group(1))
    return (package.group(1) if package else ''), used


def unexpected(package, used):
    def own(p):
        # В итоговом манифесте атрибута package может не быть: тогда достаточно суффикса.
        return p.endswith(OWN_SUFFIXES) and (not package or p.startswith(package + '.'))
    return sorted(p for p in used if p not in ALLOWED and not own(p))


def parse_manifest(path):
    ns = '{http://schemas.android.com/apk/res/android}'
    root = ET.parse(path).getroot()
    used = {e.get(ns + 'name') for tag in ('uses-permission', 'uses-permission-sdk-23') for e in root.iter(tag)}
    return root.get('package', ''), used


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    package, used = parse_manifest(argv[0]) if argv[0].endswith('.xml') else parse(read_dump(argv[0]))
    extra = unexpected(package, used)
    for p in sorted(used):
        print(('FAIL ' if p in extra else 'OK   ') + p)
    if extra:
        print('Лишние разрешения в APK: ' + ', '.join(extra) +
              '. Уберите их в AndroidManifest.xml через tools:node="remove" или, если они нужны, '
              'добавьте в ALLOWED и объясните в политике конфиденциальности.')
        return 1
    print(f'Разрешения APK {package}: всё в разрешённом списке.')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
