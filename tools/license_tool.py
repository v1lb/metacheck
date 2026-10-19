#!/usr/bin/env python3
"""
Выпуск лицензионных ключей MetaCheck Pro (подпись ECDSA P-256).

Установка:      pip install cryptography
Один раз:       python3 tools/license_tool.py keygen --out secrets/license_private.pem
                (вставь напечатанный открытый ключ в LicenseKey.PUBLIC_KEY_BASE64 и пересобери приложение)
Выдать ключ:    python3 tools/license_tool.py issue --key secrets/license_private.pem --note "Иван" --days 365
Бессрочный:     python3 tools/license_tool.py issue --key secrets/license_private.pem --note "Партнёр"

Закрытый ключ храни офлайн и никогда не коммить. Его утечка позволяет кому угодно выпускать ключи Pro,
а заменить его можно только выпуском новой версии приложения с новым открытым ключом.
Ключ нельзя отозвать: используй срок действия (--days), если хочешь ограничить время.
"""
import argparse
import base64
import getpass
import json
import os
import secrets
import sys
import time

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec


def b64u(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def cmd_keygen(args):
    if os.path.exists(args.out):
        sys.exit(f"{args.out} уже существует, не перезаписываю")
    password = getpass.getpass("Пароль для закрытого ключа (пусто = без пароля): ")
    encryption = (
        serialization.BestAvailableEncryption(password.encode()) if password else serialization.NoEncryption()
    )
    key = ec.generate_private_key(ec.SECP256R1())
    pem = key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, encryption)
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    fd = os.open(args.out, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "wb") as f:
        f.write(pem)
    public = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    print("Закрытый ключ сохранён:", args.out)
    print("Открытый ключ для LicenseKey.PUBLIC_KEY_BASE64:")
    print(base64.b64encode(public).decode("ascii"))


def cmd_issue(args):
    with open(args.key, "rb") as f:
        pem = f.read()
    password = None
    if b"ENCRYPTED" in pem:
        password = getpass.getpass("Пароль закрытого ключа: ").encode()
    key = serialization.load_pem_private_key(pem, password=password)
    now = int(time.time())
    payload = {"id": secrets.token_hex(8), "iat": now}
    if args.days:
        payload["exp"] = now + args.days * 86400
    if args.note:
        payload["note"] = args.note[:100]
    body = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    head = "MC1." + b64u(body)
    signature = key.sign(head.encode("ascii"), ec.ECDSA(hashes.SHA256()))  # DER, как ждёт Java
    print(head + "." + b64u(signature))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    k = sub.add_parser("keygen")
    k.add_argument("--out", required=True)
    k.set_defaults(func=cmd_keygen)
    i = sub.add_parser("issue")
    i.add_argument("--key", required=True)
    i.add_argument("--note", default="")
    i.add_argument("--days", type=int, default=0, help="срок действия в днях, 0 = бессрочно")
    i.set_defaults(func=cmd_issue)
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
