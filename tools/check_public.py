#!/usr/bin/env python3
"""Check Git's candidate publication files without printing matched values.

This is a conservative guard, not a replacement for reviewing the complete diff,
history, binary assets and service-specific credentials before publishing.
"""
from pathlib import Path
import re
import hashlib
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
# Public demo clips inherited unchanged from the original project, not device recordings.
PUBLIC_UPSTREAM_MEDIA = {'docs/assets/drawer.mp4': 'c7c0f20b1feba4af6b57ffbd374967ee75a87c6d4af6e22c70bc13f2cca969a5', 'docs/assets/film.mp4': '71f5772ae11f2ee45d3027c922aadcf787c905e6eb4ef04837390d4147b46939', 'docs/assets/nav.mp4': '31ec76bb52d031119f28706574b9f6518209b9c058e0c20b601bd2bba8f1cf24', 'docs/assets/tabs.mp4': '159a30f56ef94b518a5a859f45ddf75a2e9a44e83358b1dfe2e24f2b43db181b'}
FORBIDDEN = {'.apk', '.idsig', '.keystore', '.jks', '.p12', '.pfx', '.pem', '.key', '.har', '.mp4', '.webm', '.mp3'}
RULES = {
    'private-key material': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----'),
    'JWT-like credential': re.compile(r'\beyJ[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}'),
    'provider credential': re.compile(r'\b(?:sk_[A-Za-z0-9]{24,}|gh[pousr]_[A-Za-z0-9]{24,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[A-Z0-9]{16})\b'),
    'literal credential assignment': re.compile(r'''(?i)(?:(?:[a-z_]*_)?(?:key|token|password|secret)|authorization)\s*["']?\s*[:=]\s*["']([A-Za-z0-9_./+=-]{24,})["']'''),
    'local user path': re.compile(r'[Cc]:[\\/]+Users[\\/]+[^\\/\s"<>]+'),
}
PRIVATE_LAN = re.compile(r'(?<!\d)(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})(?!\d)')

def main():
    result = subprocess.run(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=ROOT, capture_output=True, check=True)
    paths = sorted(set(p for p in result.stdout.decode('utf-8').split('\0') if p))
    problems = []
    scanned = 0
    for relative in paths:
        path = ROOT / relative
        if not path.exists():
            continue  # A deleted tracked file is not published by the working tree.
        if path.is_symlink():
            problems.append((relative, 0, 'review symlink target'))
            continue
        if relative in PUBLIC_UPSTREAM_MEDIA and hashlib.sha256(path.read_bytes()).hexdigest() == PUBLIC_UPSTREAM_MEDIA[relative]:
            continue
        if path.name == 'config.properties' or path.suffix.lower() in FORBIDDEN:
            problems.append((relative, 0, 'private/generated file in publication candidates'))
            continue
        data = path.read_bytes()
        if b'\0' in data[:4096]:
            continue  # Binary resource permissions/contents need separate review.
        try:
            text = data.decode('utf-8-sig')
        except UnicodeDecodeError:
            continue
        scanned += 1
        for number, line in enumerate(text.splitlines(), 1):
            for kind, rule in RULES.items():
                if rule.search(line):
                    problems.append((relative, number, kind))
            if relative.startswith(('app/src/', 'app/res/')) and PRIVATE_LAN.search(line):
                problems.append((relative, number, 'LAN address embedded in production code/resources'))
        if relative == 'config.example.properties':
            for number, line in enumerate(text.splitlines(), 1):
                match = re.match(r'\s*([^#!\s=]+)\s*=\s*(.*?)\s*$', line)
                if match and re.search(r'(?:^|_)(?:key|token|password|secret)$', match[1], re.I) and match[2] and not re.fullmatch(r'X{5,}', match[2]):
                    problems.append((relative, number, 'credential in example configuration'))
    build = (ROOT / 'build.sh').read_text(encoding='utf-8')
    if 'case "$v" in *XXXXX*) v="" ;; esac' not in build:
        problems.append(('build.sh', 0, 'reference placeholders must remain disconnected'))
    if 'static final String ELEVENLABS_KEY' in build:
        problems.append(('build.sh', 0, 'ElevenLabs key must remain runtime-only'))
    for path, line, reason in problems:
        print(f'{path}:{line}: {reason}; matched value withheld')
    print(f'Publication guard: {scanned} text files checked, {len(problems)} findings.')
    return 1 if problems else 0

if __name__ == '__main__':
    sys.exit(main())
