#!/bin/bash
set -euo pipefail

# Only run in remote (Claude Code on the web / mobile) sessions — leave local
# Android Studio setups untouched.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

echo '{"async": true, "asyncTimeout": 300000}'

# Installs the "caveman" terse-output skill/hooks (github:JuliusBrussee/caveman)
# for Claude Code. Idempotent and safe to re-run every session start. The
# git-based marketplace path isn't reachable through this environment's proxy,
# so it falls back to standalone hook wiring in ~/.claude/settings.json — fine
# for our purposes since only the hooks matter here.
npx -y github:JuliusBrussee/caveman -- --only claude --non-interactive || true
