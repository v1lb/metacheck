#!/usr/bin/env python3
"""Генератор строковых ресурсов MetaCheck.

Источник правды — файлы рядом со скриптом:
  master_*.py  список S из записей (key, en, ru):
               en  → app/src/main/res/values/strings.xml    (язык по умолчанию)
               ru  → app/src/main/res/values-ru/strings.xml
               (key, text, None) — непереводимая строка (самоназвание языка, имя приложения):
               пишется только в values/ с translatable="false".
               Для plurals вместо строки словарь {"one": ..., "few": ..., "other": ...}.
  lang_<tag>.py  дополнительный язык: словарь T = {key: текст или словарь plurals}
               → values-<tag>/strings.xml. Должен переводить все переводимые ключи.
               <tag> — квалификатор Android: es, pt-rBR, zh-rCN, in (индонезийский) и т. п.
               Переводы без проверки носителем языка: правки присылайте в lang_<tag>.py.

Запуск:
  python3 tools/i18n/gen.py            записать XML
  python3 tools/i18n/gen.py --check    только проверить, что XML совпадает с источником (для CI)

Проверки: дубли ключей, одинаковые аргументы (%1$s, %2$d) во всех языках, позиционные аргументы,
формы plurals для языка, отсутствие кириллицы в английском, пустые строки.
"""
import glob
import importlib.util
import os
import re
import sys
from xml.sax.saxutils import escape

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'res'))

# Обязательные формы plurals (CLDR). Для языка вне таблицы требуется хотя бы "other".
PLURAL_FORMS = {
    'en': {'one', 'other'}, 'de': {'one', 'other'}, 'it': {'one', 'other'}, 'nl': {'one', 'other'},
    'es': {'one', 'other'}, 'pt': {'one', 'other'}, 'fr': {'one', 'other'}, 'tr': {'one', 'other'},
    'ru': {'one', 'few', 'many', 'other'}, 'uk': {'one', 'few', 'many', 'other'},
    'pl': {'one', 'few', 'many', 'other'}, 'cs': {'one', 'few', 'many', 'other'},
    'ja': {'other'}, 'ko': {'other'}, 'zh': {'other'}, 'id': {'other'}, 'vi': {'other'},
}
ARG = re.compile(r'%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxX]')
CYRILLIC = re.compile('[А-Яа-яЁё]')
HEADER = '<!-- Сгенерировано tools/i18n/gen.py. Не редактировать вручную: правьте tools/i18n/*.py -->'


def load_module(path):
    spec = importlib.util.spec_from_file_location(os.path.basename(path)[:-3], path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def load():
    entries = []
    for path in sorted(glob.glob(os.path.join(HERE, 'master_*.py'))):
        for e in load_module(path).S:
            entries.append((os.path.basename(path), e))
    extra = {}
    for path in sorted(glob.glob(os.path.join(HERE, 'lang_*.py'))):
        tag = os.path.basename(path)[5:-3]
        extra[tag] = load_module(path).T
    return entries, extra


def args_of(text):
    return sorted(set(m.group(0) for m in ARG.finditer(text)))


def android_escape(text):
    t = escape(text)  # & < >
    t = t.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')
    if t.startswith('@') or t.startswith('?'):
        t = '\\' + t
    return t


def validate(entries, extra):
    errors = []
    keys = [e[0] for _, e in entries]
    dup = sorted(set(k for k in keys if keys.count(k) > 1))
    if dup:
        errors.append(f'duplicate keys: {dup}')
    translations = {'ru': {}}
    base = {}
    for _, (key, en, ru) in entries:
        if not re.fullmatch(r'[a-z][a-z0-9_]*', key):
            errors.append(f'bad key {key}')
        if ru is None:
            if not isinstance(en, str) or not en.strip():
                errors.append(f'bad non-translatable {key}')
            continue
        base[key] = en
        translations['ru'][key] = ru
    for tag, table in extra.items():
        translations[tag] = table
        missing = sorted(set(base) - set(table))
        unknown = sorted(set(table) - set(base))
        if missing:
            errors.append(f'{tag}: missing {len(missing)} keys, e.g. {missing[:5]}')
        if unknown:
            errors.append(f'{tag}: unknown keys {unknown[:5]}')

    def check_text(key, lang, text, expected_args):
        if not isinstance(text, str) or not text.strip():
            errors.append(f'{lang}: empty text {key}')
            return
        if args_of(text) != expected_args:
            errors.append(f'{lang}: arg mismatch {key}: {args_of(text)} vs {expected_args}')
        found = args_of(text)
        if len(found) > 1 and any(not re.match(r'%\d+\$', a) for a in found):
            errors.append(f'{lang}: several args must be positional ({key})')

    for key, en in base.items():
        if isinstance(en, dict):
            expected = args_of(en.get('other', ''))
            langs = [('en', en)] + [(t, tbl.get(key)) for t, tbl in translations.items() if key in tbl]
            for lang, forms in langs:
                if not isinstance(forms, dict):
                    errors.append(f'{lang}: {key} must be plurals')
                    continue
                base_lang = {'in': 'id'}.get(lang.split('-')[0], lang.split('-')[0])
                need = PLURAL_FORMS.get(base_lang, {'other'})
                if need - set(forms):
                    errors.append(f'{lang}: plural {key} missing {sorted(need - set(forms))}')
                for text in forms.values():
                    check_text(key, lang, text, expected)
            for text in en.values():
                if CYRILLIC.search(text):
                    errors.append(f'cyrillic in EN {key}')
            continue
        expected = args_of(en)
        check_text(key, 'en', en, expected)
        if CYRILLIC.search(en):
            errors.append(f'cyrillic in EN {key}: {en}')
        for lang, tbl in translations.items():
            if key in tbl:
                if isinstance(tbl[key], dict):
                    errors.append(f'{lang}: {key} must be a string')
                else:
                    check_text(key, lang, tbl[key], expected)
    return errors, translations


def render(entries, lang, table):
    lines = ['<?xml version="1.0" encoding="utf-8"?>', HEADER, '<resources>']
    current = None
    for src, (key, en, ru) in entries:
        if ru is None and lang != 'en':
            continue
        if src != current:
            lines.append(f'\n    <!-- {src} -->')
            current = src
        if ru is None:
            lines.append(f'    <string name="{key}" translatable="false">{android_escape(en)}</string>')
            continue
        text = en if lang == 'en' else table[key]
        if isinstance(text, dict):
            lines.append(f'    <plurals name="{key}">')
            for q in ('zero', 'one', 'two', 'few', 'many', 'other'):
                if q in text:
                    lines.append(f'        <item quantity="{q}">{android_escape(text[q])}</item>')
            lines.append('    </plurals>')
            continue
        attrs = ' formatted="false"' if '%' in text and not args_of(text) else ''
        lines.append(f'    <string name="{key}"{attrs}>{android_escape(text)}</string>')
    lines.append('</resources>\n')
    return '\n'.join(lines)


def main(argv):
    check = '--check' in argv
    entries, extra = load()
    errors, translations = validate(entries, extra)
    if errors:
        print('\n'.join(errors))
        return 1
    outputs = {os.path.join(RES, 'values', 'strings.xml'): render(entries, 'en', None)}
    for lang, table in translations.items():
        outputs[os.path.join(RES, f'values-{lang}', 'strings.xml')] = render(entries, lang, table)
    stale = []
    for path, content in outputs.items():
        old = open(path, encoding='utf-8').read() if os.path.exists(path) else None
        if old == content:
            continue
        if check:
            stale.append(os.path.relpath(path, os.path.join(HERE, '..', '..')))
        else:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
    if check:
        if stale:
            print('Ресурсы устарели, запустите python3 tools/i18n/gen.py: ' + ', '.join(stale))
            return 1
        print(f'OK: {len(entries)} strings, languages: en, ' + ', '.join(sorted(translations)))
        return 0
    print(f'{len(entries)} strings written for en, ' + ', '.join(sorted(translations)))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
