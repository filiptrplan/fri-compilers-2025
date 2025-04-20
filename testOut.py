#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = [
# "psutil",
# ]
# ///
import os
import sys
import subprocess as sp
import difflib
import argparse
import time

from multiprocessing import Pool
import psutil

LIMIT_OUTPUT: bool = True
ALWAYS_SHOW_OUTPUT: bool = False
NO_COLOR: bool = False
OUTPUT_LIMIT_NUM: int = 10
SHOW_STDERR: bool = False
THREAD_COUNT: int = 1

RESET_COLOR = "\x1b[m"

COLORS: dict[str, str] = {
    "red": "\x1b[31m",
    "RED": "\x1b[91m",
    "green": "\x1b[32m",
    "GREEN": "\x1b[92m",
    "yellow": "\x1b[33m",
    "YELLOW": "\x1b[93m",
    "blue": "\x1b[34m",
    "BLUE": "\x1b[94m",
    "cyan": "\x1b[36m",
    "CYAN": "\x1b[96m",
    "white": "\x1b[37m",
    "WHITE": "\x1b[97m",
    "gray": "\x1b[90m",
}

STYLES: dict[str, str] = {
    "bold": "\x1b[1m",
    "italic": "\x1b[3m",
    "underline": "\x1b[4m",
}


def colorize_text(text: str, color: str, styles: list[str] = []) -> str:
    if NO_COLOR:
        return text

    return (
        COLORS.get(color, "")
        + "".join([STYLES.get(s, "") for s in styles])
        + text
        + RESET_COLOR
    )


def get_diff(expected: str, actual: str) -> tuple[str, int]:
    if (len(expected) == 0) and (len(expected) == 0):
        return "", 0

    diff = difflib.unified_diff(
        expected.splitlines(True), actual.splitlines(True), "EXPECTED", "ACTUAL", n=2
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


def run_test(test: str) -> tuple[str, str, float]:
    """Return stdout and stderr"""

    with sp.Popen(
        ["make", "-s", test], stdout=sp.PIPE, stderr=sp.PIPE, stdin=sp.PIPE
    ) as p:
        start = time.perf_counter()
        p.wait()
        end = time.perf_counter()
        return (
            p.stdout.read().decode("utf-8"),
            p.stderr.read().decode("utf-8"),
            (end - start),
        )


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


def run_test_and_collect(file: str):
    expected = get_expected(file + ".out")
    output, error, run_time = run_test(file)
    diff = get_diff(expected=expected, actual=output)

    return file, dict(
        stdout=output,
        stderr=error,
        expected=expected,
        diff=diff,
        run_time=run_time,
    )


def run_tests_in_parallel(
    test_files: list[str], max_alignment: int, OK: str, FAIL: str
) -> dict[str, dict[str, str]]:
    global THREAD_COUNT

    test_outputs: dict[str, dict[str, str]] = dict()

    CORES = psutil.cpu_count(logical=False) or 1

    if THREAD_COUNT <= 0:
        THREAD_COUNT = 1
    elif THREAD_COUNT > CORES:
        THREAD_COUNT = CORES

    pool = Pool(THREAD_COUNT)

    for file, out in pool.imap_unordered(run_test_and_collect, iter(test_files)):
        print(
            colorize_text("Test", "gray")
            + colorize_text(f" {{:<{max_alignment}}}".format(file), "WHITE", ["bold"]),
            end=" ",
            flush=True,
        )

        test_outputs[file] = out
        if out["diff"][1] == 0:
            print(OK, end=" ")
        else:
            print(FAIL, end=" ")

        print(f"({out['run_time']:.3f}s)")

    return test_outputs


def test_files(test_files: list[str]) -> int:
    if len(test_files) == 0:
        print("No tests ran")
        return

    max_alignment = len(max(test_files, key=len))

    # TESTING = colorize_text("Testing", "WHITE", ["bold"])
    TESTING = "Testing"
    OK = colorize_text("OK", "GREEN", ["bold"])
    FAIL = colorize_text("FAIL", "RED", ["bold"])
    DIFF = colorize_text("DIFF", "YELLOW", ["bold", "italic"])
    STDOUT = colorize_text("STDOUT", "CYAN", ["bold", "italic"])
    STDERR = colorize_text("STDERR", "red", ["bold", "italic"])
    RESULTS = colorize_text("RESULTS", "YELLOW", ["bold"])

    print(f"{TESTING} {len(test_files)} {'test' if len(test_files) == 1 else 'tests'}:")
    test_outputs: dict[str, dict[str, str]] = run_tests_in_parallel(
        test_files, max_alignment, OK, FAIL
    )
    print()

    fails = []
    for file in test_files:
        is_ok = True
        if test_outputs[file]["diff"][1] != 0:
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
        f"Successful: {len(test_files) - len(fails)}/{len(test_files)} {'test' if len(test_files) == 1 else 'tests'}"
    )
    if len(fails) != 0:
        print(
            f"Failed: {len(fails)}/{len(test_files)} {'test' if len(fails) == 1 else 'tests'}"
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
        case "fri-compilers-2025":
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
                    f"Skipping '{colorize_text(file_name, 'WHITE')}' because '{file_name}.prev25' does not exist"
                )
                continue

            if not os.path.exists(file_name + ".out"):
                print(
                    f"Skipping '{colorize_text(file_name, 'WHITE')}' because '{file_name}.out' does not exist"
                )
                continue

            testing_files.append(file)

        exit_status = test_files(list(set(testing_files)))
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

        testing_files.append(file_name)

    exit_status = test_files(testing_files)
    sys.exit(exit_status)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        formatter_class=argparse.RawDescriptionHelpFormatter,
        description="Run inside folder 'prev25' or 'prg'.\n\nCompare all tests in 'prg' with their corresponding outputs.\nIf files are provided and a corresponding '.out' files exist, it will test them.",
    )
    parser.add_argument("files", default=None, nargs="*", help="Files to be tested")
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
    args = parser.parse_args()

    LIMIT_OUTPUT = not args.no_limit
    ALWAYS_SHOW_OUTPUT = args.show_output
    NO_COLOR = args.no_color
    OUTPUT_LIMIT_NUM = args.limit
    SHOW_STDERR = args.show_stderr
    THREAD_COUNT = args.jobs

    main(args.files or None)
