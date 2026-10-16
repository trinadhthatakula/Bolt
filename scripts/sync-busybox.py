#!/usr/bin/env python3
"""Verify or reproduce Bolt's unmodified pinned BusyBox runtime assets."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'app/src/main/assets/busybox-manifest.json'

def digest(data):
    return hashlib.sha256(data).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--download', action='store_true', help='download pinned archive and replace assets after verification')
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text())
    binaries = {}
    if args.download:
        with urllib.request.urlopen(manifest['binaryUrl'], timeout=60) as response:
            archive = response.read(16 * 1024 * 1024 + 1)
        if digest(archive) != manifest['archiveSha256']:
            raise SystemExit('Archive digest mismatch; no assets changed')
        with zipfile.ZipFile(io.BytesIO(archive)) as source:
            for abi, expected in manifest['binarySha256'].items():
                data = source.read(f'{abi}/libbusybox.so')
                if digest(data) != expected:
                    raise SystemExit(f'{abi}: binary digest mismatch; no assets changed')
                binaries[abi] = data
        for abi, data in binaries.items():
            destination = ROOT / f'app/src/main/jniLibs/{abi}/libbolt_busybox.so'
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
    for abi, expected in manifest['binarySha256'].items():
        binary = ROOT / f'app/src/main/jniLibs/{abi}/libbolt_busybox.so'
        if digest(binary.read_bytes()) != expected:
            raise SystemExit(f'{abi}: packaged binary digest mismatch')
        print(f'{abi}: verified')

if __name__ == '__main__':
    main()
