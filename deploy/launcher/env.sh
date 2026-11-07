# Sourced after ROOT is set to the physical checkout path.
tools_dir="$ROOT/.local/tools"
export PATH="$tools_dir/jdk/bin:$tools_dir/node/bin:$tools_dir/go/bin:$PATH"
if [[ -x "$tools_dir/jdk/bin/java" ]]; then export JAVA_HOME="$tools_dir/jdk"; fi
python_runtime() {
    local candidate
    for candidate in python3 python3.11 python3.9; do
        if command -v "$candidate" >/dev/null && "$candidate" -c 'import sys; sys.exit(sys.version_info < (3,8))'; then
            command -v "$candidate"
            return
        fi
    done
    return 1
}
