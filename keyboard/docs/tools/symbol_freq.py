import argparse
import collections
import io
import os
import re
import sys
import tokenize

PUNCT = "`~!@#$%^&*()-_=+[]{}\\|;:'\",.<>/?"
SHIFTED = set('~!@#$%^&*()_+{}|:"<>?') | set("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
SHELL_SHEBANG = re.compile(rb"#!\s*/(usr/)?bin/(env\s+)?(ba|da|k|z)?sh\b")


def walk(root, keep, skip=()):
    for d, _, files in os.walk(root):
        if any(s in d for s in skip):
            continue
        for f in files:
            p = os.path.join(d, f)
            if keep(p):
                yield p


def python_code(src):
    out = []
    try:
        tokens = list(tokenize.generate_tokens(io.StringIO(src).readline))
    except (tokenize.TokenError, IndentationError, SyntaxError):
        return None
    for t in tokens:
        if t.type == tokenize.COMMENT:
            continue
        if t.type == tokenize.STRING and re.match(r'^[rbuRBUfF]*("""|\'\'\')', t.string):
            continue
        if t.type in (tokenize.NL, tokenize.NEWLINE):
            out.append("\n")
            continue
        if t.type in (tokenize.INDENT, tokenize.DEDENT, tokenize.ENDMARKER):
            continue
        out.append(t.string)
        out.append(" ")
    return "".join(out)


def go_code(src):
    if re.search(r"^// Code generated .* DO NOT EDIT\.$", src, re.M):
        return None
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return re.sub(r"(?m)(^|[^:\"])//.*$", r"\1", src)


def shell_code(src):
    return "\n".join(line for line in src.splitlines() if not line.lstrip().startswith("#"))


def is_shell(path):
    try:
        if os.path.islink(path) or not os.path.isfile(path) or os.path.getsize(path) > 1_000_000:
            return False
        if path.endswith(".sh"):
            return True
        with open(path, "rb") as fh:
            return bool(SHELL_SHEBANG.match(fh.read(64)))
    except OSError:
        return False


def measure(paths, strip):
    counts = collections.Counter()
    files = 0
    seen = set()
    for p in paths:
        real = os.path.realpath(p)
        if real in seen:
            continue
        seen.add(real)
        try:
            with open(p, encoding="utf-8", errors="ignore") as fh:
                code = strip(fh.read())
        except OSError:
            continue
        if code is None:
            continue
        files += 1
        code = re.sub(r"(?m)^[ \t]+", "", code)
        code = re.sub(r"[ \t]+", " ", code)
        counts.update(c for c in code if 32 <= ord(c) < 127 or c == "\n")
    total = sum(counts.values())
    punct = {c: counts.get(c, 0) for c in PUNCT}
    punct_total = sum(punct.values()) or 1
    shifted = sum(v for k, v in counts.items() if k in SHIFTED)
    upper = sum(v for k, v in counts.items() if k.isupper())
    return {
        "files": files,
        "chars": total,
        "shift_needed": 100 * shifted / total if total else 0,
        "upper": 100 * upper / total if total else 0,
        "shifted_symbols": 100 * (shifted - upper) / total if total else 0,
        "punct_share": sorted(((c, 100 * n / punct_total) for c, n in punct.items()), key=lambda x: -x[1]),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--python", default="/usr/lib/python3.12")
    ap.add_argument("--python-extra", default="/usr/local/lib/python3.11/dist-packages")
    ap.add_argument("--go", default="/usr/local/go/src")
    ap.add_argument("--shell", nargs="*", default=["/usr/bin", "/usr/sbin", "/etc", "/usr/share", "/usr/lib", "/usr/libexec", "/opt"])
    args = ap.parse_args()
    corpora = {
        "python-stdlib": measure(
            walk(args.python, lambda p: p.endswith(".py"), ("site-packages", "dist-packages", "/test", "idlelib", "lib2to3", "__pycache__")),
            python_code,
        ),
        "python-extra": measure(walk(args.python_extra, lambda p: p.endswith(".py")), python_code),
        "go": measure(
            walk(args.go, lambda p: p.endswith(".go") and not p.endswith("_test.go"), ("testdata", "/vendor")),
            go_code,
        ),
        "shell": measure((p for root in args.shell for p in walk(root, is_shell)), shell_code),
    }
    for name, r in corpora.items():
        print(f"{name}: files={r['files']} chars={r['chars']} shift={r['shift_needed']:.2f}% "
              f"upper={r['upper']:.2f}% shifted-symbols={r['shifted_symbols']:.2f}%")
        print("  " + " ".join(f"{c}:{v:.1f}" for c, v in r["punct_share"][:16]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
