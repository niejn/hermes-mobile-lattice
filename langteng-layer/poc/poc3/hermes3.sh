#!/system/bin/sh
# PoC3: run arm64 hermes serve inside emulator via ndk_translation
H=/data/data/com.termux/files/usr/lib/hermes-agent
P=$H/tools/python/data/data/com.termux/files/usr/bin/python3.14
export PYTHONPATH="$H/app:$H/venv/lib/python3.14/site-packages"
export HERMES_SITE="$H/venv/lib/python3.14/site-packages"
export PYTHONPYCACHEPREFIX=/data/local/tmp/hermes-pycache
export PREFIX=/data/data/com.termux/files/usr
export HERMES_PYTHON_SRC_ROOT="$H/app"
export HERMES_PYTHON="$P"
export HERMES_NODE="$H/tools/node/data/data/com.termux/files/usr/bin/node"
export HERMES_RUNTIME_DIR="$H/tools"
export HOME=/data/local/tmp/lt-home-poc3
export HERMES_HOME=/data/local/tmp/lt-home-poc3
export HERMES_DESKTOP=1
export TMPDIR=/data/local/tmp
mkdir -p "$HERMES_HOME" "$TMPDIR"
exec "$P" -P -c 'import os, site, sys; sys.argv[0]="hermes"; site.addsitedir(os.environ["HERMES_SITE"]); from hermes_cli.main import main; sys.exit(main())' "$@"
