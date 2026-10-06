// Filer som git spårar trots att .gitignore ignorerar dem (t.ex. .idea/ från 3.x).
// Körs av dokumentkontrollerna: node --test '.github/scripts/*.test.mjs'
import { execFileSync } from 'node:child_process';

export function trackedIgnored(repoRoot) {
  const out = execFileSync('git', ['ls-files', '-ci', '--exclude-standard'], { cwd: repoRoot, encoding: 'utf8' });
  return out.split('\n').filter(Boolean);
}
