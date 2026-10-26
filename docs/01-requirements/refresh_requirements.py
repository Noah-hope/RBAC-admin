"""Refresh editable sources and reading copies; export images only with --previews."""
from pathlib import Path
import argparse
import json
import subprocess
import sys

from preview_support import annotate_indexes, status

DOCS = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--previews', action='store_true', help='also refresh all PNG/SVG previews')
    args = parser.parse_args()
    scripts = [
        'flows-v1/generate_uc01.py',
        'flows-v1/generate_all.py',
        'activities-v1/generate_all.py',
        'sequences-v1/generate_sequences.py',
        'usecase-tables-v1/generate_tables.py',
        'usecases-v1/generate_diagrams.py',
    ]
    for script in scripts:
        subprocess.run([sys.executable, str(DOCS / script)] +
                       (['--previews'] if args.previews else []), check=True)
    if args.previews:
        s = status()
        s['preview_revision'] = s['source_revision']
        s.pop('folder_previews', None)
        s['note'] = 'PNG/SVG 已按本次图源完整生成；辅助预览仍服从详细用例。'
        (DOCS / 'preview-status.json').write_text(
            json.dumps(s, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    annotate_indexes()
    subprocess.run([sys.executable, str(DOCS / 'sync_usecase_folders.py')], check=True)
    print('Sources and reading copies refreshed; previews ' + ('exported.' if args.previews else 'preserved.'))


if __name__ == '__main__':
    main()
