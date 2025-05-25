#!/usr/bin/env python3

from functools import partial
import os
import sys
import subprocess as sp
import difflib
import argparse
import time
from io import TextIOWrapper

from multiprocessing import Pool
from typing import Callable
import psutil
import platform
import signal

LIMIT_OUTPUT: bool = True
ALWAYS_SHOW_OUTPUT: bool = False
NO_COLOR: bool = False
OUTPUT_LIMIT_NUM: int = 10
SHOW_STDERR: bool = False
THREAD_COUNT: int = 1
MAKE_ARGS: list[str] = []
INTERPRET: bool = False
TIMEOUT: int = 2

# Command to execute compiled program (command is run inside directory 'prg')
CODE_RUN_COMMAND = "ripes"
CODE_RUN_ARGS = "--mode cli -t asm --src {file}.asm --proc RV64_SS --isaexts M"

# Look for environment variables that convey the programs will be interpreted
# None => check if exists
# else => check that it has a specific value
INTERPRET_ENV: list[tuple[str, None | str | list[str]]] = [
    # (NAME, None | str | list[str])
    ("IRI", "1"),
    ("INTERPRET", "on"),
]

RESET_COLOR = "\x1b[m"

COLORS: dict[str, str] = {
    "red": "\x1b[31m",
    "bred": "\x1b[41m",
    "RED": "\x1b[91m",
    "bRED": "\x1b[101m",
    "green": "\x1b[32m",
    "bgreen": "\x1b[42m",
    "GREEN": "\x1b[92m",
    "bGREEN": "\x1b[102m",
    "yellow": "\x1b[33m",
    "byellow": "\x1b[43m",
    "YELLOW": "\x1b[93m",
    "bYELLOW": "\x1b[103m",
    "blue": "\x1b[34m",
    "bblue": "\x1b[44m",
    "BLUE": "\x1b[94m",
    "bBLUE": "\x1b[104m",
    "cyan": "\x1b[36m",
    "bcyan": "\x1b[46m",
    "CYAN": "\x1b[96m",
    "bCYAN": "\x1b[106m",
    "white": "\x1b[37m",
    "bwhite": "\x1b[47m",
    "WHITE": "\x1b[97m",
    "bWHITE": "\x1b[107m",
    "gray": "\x1b[90m",
    "bgray": "\x1b[100m",
    "black": "\x1b[30m",
    "bblack": "\x1b[40m",
}

STYLES: dict[str, str] = {
    "bold": "\x1b[1m",
    "italic": "\x1b[3m",
    "underline": "\x1b[4m",
}


def colorize_text(
    text: str, color: str, styles: list[str] = [], background: str = ""
) -> str:
    if NO_COLOR:
        return text

    return (
        COLORS.get(color, "")
        + COLORS.get("b" + background, "")
        + "".join([STYLES.get(s, "") for s in styles])
        + text
        + RESET_COLOR
    )


def check_interpret_env() -> bool:
    for var, val in INTERPRET_ENV:
        evar = os.environ.get(var)
        if evar is None:
            continue

        if val is None:  # present
            return True

        if isinstance(val, str) and (evar == val):
            return True

        if isinstance(val, list) and (evar in val):
            return True

    return False


def get_diff(expected: str, actual: str) -> tuple[str, int]:
    actual = actual.replace("\0", "")
    if (len(expected) == 0) and (len(actual) == 0):
        return "", 0

    actual_lines = actual.splitlines(True)
    if (len(actual_lines) > 0) and actual_lines[0].startswith(":-)"):
        actual_lines = actual_lines[1:]

    if (len(actual_lines) > 0) and actual_lines[-1].startswith(":-)"):
        actual_lines = actual_lines[:-1]

    diff = difflib.unified_diff(
        expected.splitlines(True), actual_lines, "EXPECTED", "ACTUAL", n=2
    )

    is_diff = False
    diff_string = ""
    for line in diff:
        is_diff = True

        if len(line) == 0 or line[-1] != "\n":
            line += "\n"

        if line.startswith("---") or line.startswith("+++"):
            line = colorize_text(line, "WHITE", ["bold"])
        elif line.startswith("@@"):
            line = colorize_text(line, "cyan")
        elif line.startswith("-"):
            line = colorize_text(line, "red")
        elif line.startswith("+"):
            line = colorize_text(line, "green")

        diff_string += line

    if not is_diff:
        return diff_string, 0

    if (len(expected) > 0) and (len(actual) == 0):
        if expected[-1] == "\n":
            diff_string += "\\ No new line at the end"
    elif (len(expected) == 0) and (len(actual) > 0):
        if actual[-1] == "\n":
            diff_string += "\\ No new line at the end"
    elif (expected[-1] == "\n" and actual[-1] != "\n") or (
        expected[-1] != "\n" and actual[-1] == "\n"
    ):
        diff_string += "\\ No new line at the end"

    return diff_string, 1


def run_test(
    test: str, file_as_stdin: TextIOWrapper | None = None
) -> tuple[str, str, float, bool]:
    """Return stdout and stderr"""
    global MAKE_ARGS, INTERPRET, TIMEOUT

    stdin = sp.PIPE
    if (file_as_stdin is not None) and INTERPRET:
        stdin = file_as_stdin

    output = None
    with sp.Popen(
        ["make", "-s", test, *MAKE_ARGS],
        stdout=sp.PIPE,
        stderr=sp.PIPE,
        stdin=stdin,
        preexec_fn=os.setsid,  # LINUX SPECIFIC
    ) as p:
        try:
            # if TIMEOUT is None:
            start = time.perf_counter()
            p.wait(timeout=TIMEOUT)
            end = time.perf_counter()
            output = (
                p.stdout.read().decode("utf-8"),
                p.stderr.read().decode("utf-8"),
                (end - start),
                False,
            )
        except sp.TimeoutExpired as e:
            os.killpg(p.pid, signal.SIGKILL)  # LINUX SPECIFIC
            return ("" if INTERPRET else "COMPILE", "", e.timeout, True)

    if INTERPRET:
        return output

    if file_as_stdin is not None:
        stdin = file_as_stdin

    with sp.Popen(
        [CODE_RUN_COMMAND, *CODE_RUN_ARGS.format(file=test).split()],
        stdout=sp.PIPE,
        stderr=sp.PIPE,
        stdin=stdin,
        preexec_fn=os.setsid,  # LINUX SPECIFIC
    ) as p:
        try:
            start = time.perf_counter()
            p.wait(timeout=TIMEOUT)
            end = time.perf_counter()
            return (
                p.stdout.read().decode("utf-8"),
                p.stderr.read().decode("utf-8"),
                (end - start),
                False,
            )
        except sp.TimeoutExpired as e:
            os.killpg(p.pid, signal.SIGKILL)
            return ("RUNTIME", "", e.timeout, True)


def get_expected(file: str) -> str:
    with open(file, "r") as f:
        return f.read()


def limit_text(text: str, max_lines: int = 5) -> str:
    if max_lines < 0:
        return text

    if max_lines == 0:
        return ""

    lines = text.splitlines(True)

    if len(lines) <= (max_lines + 1):
        return text

    up = (max_lines + 1) // 2
    down = max_lines // 2

    return "".join(
        lines[:up] + [colorize_text("...", "RED", ["bold"]) + "\n"] + lines[-down:]
    )


def run_test_and_collect(file_and_has_input: tuple[str, bool]):
    file = file_and_has_input[0]
    expected = get_expected(file + ".out")

    stdin = None
    if file_and_has_input[1]:
        stdin = open(file + ".in")

    output, error, run_time, timeout = run_test(file, stdin)

    if not timeout:
        diff = get_diff(expected=expected, actual=output)
    else:
        diff = ("", 0)

    return file, dict(
        stdout=output,
        stderr=error,
        expected=expected,
        diff=diff,
        run_time=run_time,
        timeout=timeout,
    )


def run_tests_in_parallel(
    test_files: list[tuple[str, bool]],
    max_alignment: int,
    OK: str,
    FAIL: str,
    TM_OUT_STYLE: Callable,
) -> dict[str, dict[str, str]]:
    global THREAD_COUNT

    test_outputs: dict[str, dict[str, str]] = dict()

    CORES = psutil.cpu_count(logical=False) or 1

    if THREAD_COUNT <= 0:
        THREAD_COUNT = 1
    elif THREAD_COUNT > CORES:
        THREAD_COUNT = CORES

    ts = "TIMEOUT"

    if THREAD_COUNT > 1:
        pool = Pool(THREAD_COUNT)

        for file, out in pool.imap_unordered(run_test_and_collect, iter(test_files)):
            print(
                colorize_text("Test", "gray")
                + colorize_text(
                    f" {{:<{max_alignment}}}".format(file), "WHITE", ["bold"]
                ),
                end=" ",
                flush=True,
            )

            test_outputs[file] = out

            if out["stdout"] != "":
                ts = out["stdout"] + " TIMEOUT"

            if out["timeout"]:
                print(TM_OUT_STYLE(ts), end=" ")
            elif out["diff"][1] == 0:
                print(OK, end=" ")
            else:
                print(FAIL, end=" ")

            print(f"({out['run_time']:.3f}s)")
    else:
        for test_file in test_files:
            print(
                colorize_text("Test", "gray")
                + colorize_text(
                    f" {{:<{max_alignment}}}".format(test_file[0]), "WHITE", ["bold"]
                ),
                end=" ",
                flush=True,
            )

            file, out = run_test_and_collect(test_file)

            test_outputs[file] = out

            if out["stdout"] != "":
                ts = out["stdout"] + " TIMEOUT"

            if out["timeout"]:
                print(TM_OUT_STYLE(ts), end=" ")
            elif out["diff"][1] == 0:
                print(OK, end=" ")
            else:
                print(FAIL, end=" ")

            print(f"({out['run_time']:.3f}s)")
    return test_outputs


def test_files(test_files: list[tuple[str, bool]]) -> int:
    if len(test_files) == 0:
        print("No tests ran")
        return

    max_alignment = len(max(test_files, key=lambda x: len(x[0]))[0])

    # TESTING = colorize_text("Testing", "WHITE", ["bold"])
    TESTING = "Testing"
    OK = colorize_text("OK", "GREEN", ["bold", "italic"])
    FAIL = colorize_text("FAIL", "RED", ["bold", "italic"])
    TM_OUT_STYLE = partial(colorize_text, color="yellow", styles=["bold", "italic"])
    DIFF = colorize_text("DIFF", "YELLOW", ["bold", "italic"])
    STDOUT = colorize_text("STDOUT", "CYAN", ["bold", "italic"])
    STDERR = colorize_text("STDERR", "red", ["bold", "italic"])
    RESULTS = colorize_text("RESULTS", "YELLOW", ["bold"])

    print(f"{TESTING} {len(test_files)} {"test" if len(test_files) == 1 else "tests"}:")
    test_outputs: dict[str, dict[str, str]] = run_tests_in_parallel(
        test_files,
        max_alignment,
        OK,
        FAIL,
        TM_OUT_STYLE,
    )
    print()

    fails = []
    timeouts = []
    for file, _ in test_files:
        is_ok = True
        if test_outputs[file]["timeout"]:
            timeouts.append(file)
        elif test_outputs[file]["diff"][1] != 0:
            is_ok = False
            fails.append(file)

        file_txt = colorize_text(file, "WHITE", ["bold"])
        has_stderr = len(test_outputs[file]["stderr"]) > 0

        if is_ok and ((SHOW_STDERR and has_stderr) or ALWAYS_SHOW_OUTPUT):
            print(f"===========  [{OK}]  =========== ({file_txt})")

        if not is_ok:
            print(f"=========== [{FAIL}] =========== ({file_txt})")

            print(f"===========  {DIFF}  ===========")
            print(test_outputs[file]["diff"][0])

        if (not is_ok) or ALWAYS_SHOW_OUTPUT:
            print(f"=========== {STDOUT} ===========")
            print(
                limit_text(
                    test_outputs[file]["stdout"],
                    max_lines=OUTPUT_LIMIT_NUM if LIMIT_OUTPUT else -1,
                )
            )

        if SHOW_STDERR and has_stderr:
            print(f"=========== {STDERR} ===========")
            print(
                limit_text(
                    test_outputs[file]["stderr"],
                    max_lines=OUTPUT_LIMIT_NUM * 2 if LIMIT_OUTPUT else -1,
                )
            )

    print(f"=========== {RESULTS} ==========")

    print(
        f"Successful: {len(test_files) - len(fails) - len(timeouts)}/{len(test_files)} {"test" if len(test_files) == 1 else "tests"}"
    )
    if len(timeouts) != 0:
        print(
            f"Timed out: {len(timeouts)}/{len(test_files)} {"test" if len(timeouts) == 1 else "tests"}"
        )
        DASH = colorize_text(" +", "WHITE", ["bold"])
        for to in timeouts:
            print(DASH, to)

    if len(fails) != 0:
        print(
            f"Failed: {len(fails)}/{len(test_files)} {"test" if len(fails) == 1 else "tests"}"
        )
        DASH = colorize_text(" +", "WHITE", ["bold"])
        for fail in fails:
            print(DASH, fail)

    return len(fails)


def main(fs: list[str]):
    cur_dir = os.path.abspath(".")
    match os.path.basename(cur_dir):
        case "prev25":
            os.chdir("prg")
        case "prg":
            pass
        case d:
            raise ValueError(f"Current directory is not prev25 or prg. Is {d}")

    #
    # ===================== SELECTED FILES =====================
    #

    if fs is not None:

        testing_files = []
        for file in fs:
            file_name, ext = os.path.splitext(file)

            if not os.path.exists(file_name + ".prev25"):
                print(
                    f"Skipping '{colorize_text(file_name, "WHITE")}' because '{file_name}.prev25' does not exist"
                )
                continue

            if not os.path.exists(file_name + ".out"):
                print(
                    f"Skipping '{colorize_text(file_name, "WHITE")}' because '{file_name}.out' does not exist"
                )
                continue

            has_input = os.path.exists(file_name + ".in")

            testing_files.append((file, has_input))

        testing_files = list(set(testing_files))
        testing_files.sort(key=lambda x: x[0])
        exit_status = test_files(testing_files)
        sys.exit(exit_status)

    #
    # ===================== ALL FILES =====================
    #

    _, _, files = next(os.walk(os.curdir))

    testing_files = []
    for file in files:
        file_name, ext = os.path.splitext(file)
        if ext != ".prev25":
            continue
        if not os.path.exists(file_name + ".out"):
            continue

        has_input = os.path.exists(file_name + ".in")

        testing_files.append((file_name, has_input))

    testing_files.sort(key=lambda x: x[0])
    exit_status = test_files(testing_files)
    sys.exit(exit_status)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        formatter_class=argparse.RawDescriptionHelpFormatter,
        description="\
    Run inside folder 'prev25' or 'prg'.\n\n\
    Compare all tests in 'prg' with their corresponding outputs.\n\
    If files are provided and a corresponding '.out' files exist, it will test them.\n\
    Every argument after '--' will be passed to make file.",
    )
    parser.add_argument("files", default=None, nargs="*", help="Files to be tested")
    parser.add_argument(
        "-i",
        "--interpret",
        default=False,
        help="Interpret programs",
        action="store_true",
    )
    parser.add_argument(
        "-n",
        "--no-limit",
        default=False,
        help="Do not limit output length",
        action="store_true",
    )
    parser.add_argument(
        "-l",
        "--limit",
        type=int,
        default=10,
        help="Set output limit to 'LIMIT'",
    )
    parser.add_argument(
        "-o",
        "--show-output",
        default=False,
        help="Always show test output",
        action="store_true",
    )
    parser.add_argument(
        "-c",
        "--no-color",
        default=False,
        help="Do not print output in color",
        action="store_true",
    )
    parser.add_argument(
        "-e",
        "--show-stderr",
        default=False,
        help="Show output from stderr",
        action="store_true",
    )
    parser.add_argument(
        "-j",
        "--jobs",
        type=int,
        default=1,
        help="Parallelize test execution to 'JOBS' threads (capped at number of cpu cores)",
    )
    parser.add_argument(
        "-t",
        "--timeout",
        type=float,
        help="Set timeout for program execution in seconds",
    )

    try:
        split = sys.argv.index("--")
    except ValueError:
        split = None

    if split is None:
        args = parser.parse_args()
    else:
        args = parser.parse_args(sys.argv[1:split])
        MAKE_ARGS = sys.argv[split + 1 :]

    LIMIT_OUTPUT = not args.no_limit
    ALWAYS_SHOW_OUTPUT = args.show_output
    NO_COLOR = args.no_color
    OUTPUT_LIMIT_NUM = args.limit
    SHOW_STDERR = args.show_stderr
    THREAD_COUNT = args.jobs
    INTERPRET = args.interpret or check_interpret_env()
    TIMEOUT = args.timeout

    main(args.files or None)
