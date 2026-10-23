#!/usr/bin/env python3
"""Read-only publication preflight. Does not initialize, commit, push or tag."""
from pathlib import Path
import json
import os
import subprocess
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
def run(args, **kwargs):
    try:
        return subprocess.run(args, cwd=ROOT, capture_output=True, text=True, timeout=25, **kwargs)
    except (OSError, subprocess.TimeoutExpired): return None

def main():
    repo = run(['git', 'rev-parse', '--show-toplevel'])
    result = {'gitRepository': bool(repo and repo.returncode == 0),
              'remoteMatches': False, 'remoteReadable': False, 'ghRepositoryAccess': False}
    if result['gitRepository']:
        remote = run(['git', 'remote', 'get-url', 'origin'])
        url = remote.stdout.strip() if remote and remote.returncode == 0 else ''
        if url.startswith('git@github.com:'): path = url.split(':', 1)[1]
        else:
            parsed = urlsplit(url)
            path = parsed.path.lstrip('/') if parsed.hostname == 'github.com' else ''
        result['remoteMatches'] = path.removesuffix('.git').lower() == 'staaaaaaaaar/seen'
        for name, args in [('branch', ['git','branch','--show-current']), ('head',['git','rev-parse','HEAD'])]:
            response=run(args)
            result[name]=response.stdout.strip() if response and response.returncode == 0 else None
        if result['remoteMatches']:
            env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GIT_SSH_COMMAND':'ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=10'}
            response=run(['git','ls-remote','origin'],env=env)
            result['remoteReadable']=bool(response and response.returncode == 0)
        response=run(['gh','api','repos/Staaaaaaaaar/seen','--jq','.permissions.push'])
        result['ghRepositoryAccess']=bool(response and response.returncode == 0 and response.stdout.strip() == 'true')
    destination=ROOT/'.tools/git-preflight.json'
    destination.parent.mkdir(exist_ok=True)
    destination.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print('Git publication preflight:',json.dumps(result,ensure_ascii=False))
    print('Read-only check; no repository changes or publication performed.')

if __name__ == '__main__': main()
