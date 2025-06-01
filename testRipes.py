#!/usr/bin/env python3
import os
import subprocess
import sys
import glob
from pathlib import Path
import re
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading
import argparse
import difflib


# ANSI color codes
class Colors:
    RED = "\033[91m"
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    BLUE = "\033[94m"
    MAGENTA = "\033[95m"
    CYAN = "\033[96m"
    WHITE = "\033[97m"
    BOLD = "\033[1m"
    UNDERLINE = "\033[4m"
    RESET = "\033[0m"

    # Diff-specific colors
    DIFF_HEADER = "\033[1;36m"  # Bold cyan
    DIFF_REMOVE = "\033[91m"  # Red
    DIFF_ADD = "\033[92m"  # Green
    DIFF_CONTEXT = "\033[37m"  # Light gray


def colored_diff(expected, actual, test_name):
    """Generate a colored unified diff between expected and actual output"""
    expected_lines = expected.splitlines(keepends=True)
    actual_lines = actual.splitlines(keepends=True)

    diff = difflib.unified_diff(
        expected_lines,
        actual_lines,
        fromfile=f"Expected ({test_name})",
        tofile=f"Actual ({test_name})",
        lineterm="",
    )

    colored_output = []
    for line in diff:
        if line.startswith("---") or line.startswith("+++"):
            colored_output.append(f"{Colors.DIFF_HEADER}{line}{Colors.RESET}")
        elif line.startswith("@@"):
            colored_output.append(f"{Colors.CYAN}{line}{Colors.RESET}")
        elif line.startswith("-"):
            colored_output.append(f"{Colors.DIFF_REMOVE}{line}{Colors.RESET}")
        elif line.startswith("+"):
            colored_output.append(f"{Colors.DIFF_ADD}{line}{Colors.RESET}")
        else:
            colored_output.append(f"{Colors.DIFF_CONTEXT}{line}{Colors.RESET}")

    return "\n".join(colored_output)


def extract_expected_output(out_file_content):
    """Extract the expected output from a .out file"""
    lines = out_file_content.strip().split("\n")
    # New format: direct program output followed by "Program exited with code: X"
    content_lines = []
    for line in lines:
        line_stripped = line.strip()
        # Skip the "Program exited with code: X" line
        if line_stripped.startswith("Program exited with code:"):
            break
        if line_stripped:  # Only include non-empty lines
            content_lines.append(line_stripped)
    return {
        "output": "\n".join(content_lines),
    }


def compile_test(test_name, num_regs=None):
    try:
        cmd = ["./run.py", test_name, "--no-compile"]
        if num_regs is not None:
            cmd.append(f"--num-regs={num_regs}")
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=60,
        )
        if result.returncode != 0:
            print(f"    Compilation failed for {test_name}")
            print(f"    stdout: {result.stdout}")
            print(f"    stderr: {result.stderr}")
            return False
        # Check if the .asm file was generated
        asm_file = Path(f"prg/{test_name}.prev25.asm")
        if not asm_file.exists():
            print(f"    Assembly file not found: {asm_file}")
            return False
        return True
    except subprocess.TimeoutExpired:
        print(f"    Timeout compiling test {test_name}")
        return False
    except Exception as e:
        print(f"    Error compiling test {test_name}: {e}")
        return False


def run_ripes(test_name):
    """Run the test using Ripes directly"""
    try:
        ripes_cmd = [
            "./Ripes.AppImage",
            "--mode",
            "cli",
            "--src",
            f"prg/{test_name}.prev25.asm",
            "--t",
            "asm",
            "--proc",
            "RV64_5S",
            "--isaexts",
            "M",
            "--reginit",
            "gpr:2=0x7ffffff0",
        ]
        # Check if there's a corresponding .in file for stdin input
        input_file = Path(f"prg/{test_name}.in")
        stdin_input = None
        if input_file.exists():
            try:
                with open(input_file, "r") as f:
                    stdin_input = f.read()
                print(f"    Using input file: {input_file}")
            except Exception as e:
                print(f"    Warning: Could not read input file {input_file}: {e}")
        result = subprocess.run(
            ripes_cmd, capture_output=True, text=True, timeout=300, input=stdin_input
        )
        return result.stdout, result.stderr, result.returncode
    except subprocess.TimeoutExpired:
        print(f"    Timeout running Ripes for {test_name}")
        return None, None, None
    except Exception as e:
        print(f"    Error running Ripes for {test_name}: {e}")
        return None, None, None


def extract_ripes_output(stdout, stderr, returncode):
    """Extract program output from Ripes output"""
    # Combine stdout and stderr
    combined_output = (stdout or "") + (stderr or "")
    if not combined_output.strip():
        return None
    # Remove null bytes
    combined_output = combined_output.replace("\x00", "")
    lines = combined_output.strip().split("\n")
    # remove lines starting with qt
    lines = [line for line in lines if not line.startswith("qt")]
    # Remove "Program exited with code: X" lines
    content_lines = []
    for line in lines:
        line = line.strip()
        if line and not line.startswith("Program exited with code:"):
            content_lines.append(line)
    return {
        "output": "\n".join(content_lines),
    }


def normalize_output(output):
    """Normalize output by removing extra whitespace and handling special cases"""
    if not output:
        return ""
    # Remove null bytes
    output = output.replace("\x00", "")
    lines = output.split("\n")
    filtered_lines = []
    for line in lines:
        line = line.strip()
        if (
            line
            and not line.startswith("EXIT CODE:")
            and not line.startswith("Program exited with code:")
        ):
            filtered_lines.append(line)
    return "\n".join(filtered_lines)


def compare_outputs(expected, actual, test_name):
    """Compare expected and actual outputs (ignoring exit codes)"""
    if expected is None or actual is None:
        return False, "Failed to parse output", None
    # Normalize outputs for comparison
    expected_output = normalize_output(expected["output"])
    actual_output = normalize_output(actual["output"])
    output_match = expected_output == actual_output
    if output_match:
        return True, "Match", None
    else:
        # Generate colored diff
        diff_output = colored_diff(expected_output, actual_output, test_name)
        return (False, f"Output mismatch", diff_output)


def run_single_test(out_file, skip_input=False, num_regs=None):
    """Run a single test and return the result"""
    test_name = out_file.stem
    # Check if test requires input and should be skipped
    input_file = Path(f"prg/{test_name}.in")
    if skip_input and input_file.exists():
        return test_name, True, "SKIPPED (requires input)", None
    # Read expected output
    try:
        with open(out_file, "r") as f:
            expected_content = f.read()
    except Exception as e:
        return test_name, False, f"Error reading {out_file}: {e}", None
    expected = extract_expected_output(expected_content)
    if expected is None:
        return (
            test_name,
            False,
            f"Could not parse expected output from {out_file}",
            None,
        )
    # Compile the test
    if not compile_test(test_name, num_regs):
        return test_name, False, "Compilation failed", None
    # Run with Ripes
    stdout, stderr, returncode = run_ripes(test_name)
    if stdout is None:
        return test_name, False, "Failed to run Ripes", None
    actual = extract_ripes_output(stdout, stderr, returncode)
    if actual is None:
        return test_name, False, "Could not parse Ripes output", None
    # Compare outputs
    match, details, diff_output = compare_outputs(expected, actual, test_name)
    if match:
        return test_name, True, "PASS", None
    else:
        return test_name, False, details, diff_output


def main():
    parser = argparse.ArgumentParser(description="Run Ripes tests")
    parser.add_argument(
        "--skip-input",
        action="store_true",
        help="Skip tests that require .in input files",
    )
    parser.add_argument(
        "--num-regs",
        type=int,
        help="Number of registers to use for register allocation",
    )
    parser.add_argument(
        "--no-color",
        action="store_true",
        help="Disable colored output",
    )
    args = parser.parse_args()

    # Disable colors if requested or if not outputting to a terminal
    if args.no_color or not sys.stdout.isatty():
        for attr in dir(Colors):
            if not attr.startswith("_"):
                setattr(Colors, attr, "")

    prg_dir = Path("prg")
    if not prg_dir.exists():
        print("Error: 'prg' directory not found")
        sys.exit(1)
    # Check if Ripes is available
    try:
        subprocess.run(["Ripes", "--help"], capture_output=True, timeout=5)
    except (subprocess.TimeoutExpired, FileNotFoundError):
        print(
            "Error: Ripes command not found. Make sure Ripes is installed and in PATH."
        )
        sys.exit(1)
    # Find all .out files (excluding .temp.out files)
    out_files = [f for f in prg_dir.glob("*.out") if not f.name.endswith(".temp.out")]
    if not out_files:
        print("No .out files found in prg directory")
        sys.exit(1)
    # Count input-requiring tests if skip-input is enabled
    input_tests = []
    if args.skip_input:
        for out_file in out_files:
            test_name = out_file.stem
            input_file = Path(f"prg/{test_name}.in")
            if input_file.exists():
                input_tests.append(test_name)
    print(f"Found {len(out_files)} test files")
    if args.skip_input:
        print(
            f"Skipping {len(input_tests)} tests that require input files: {', '.join(input_tests)}"
        )
    print("Running tests in parallel...")
    print("-" * 60)
    passed = 0
    failed = 0
    skipped = 0
    results = []
    failed_results = []  # Store failed results with diff output

    # Use ThreadPoolExecutor to run tests in parallel
    # Limit max workers to avoid overwhelming the system
    # max_workers = len(out_files)
    max_workers = 8
    print(f"Running with {max_workers} workers.")
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        # Submit all tests
        future_to_test = {
            executor.submit(
                run_single_test, out_file, args.skip_input, args.num_regs
            ): out_file
            for out_file in sorted(out_files)
        }
        # Collect results as they complete
        for future in as_completed(future_to_test):
            test_name, success, details, diff_output = future.result()
            results.append((test_name, success, details, diff_output))
            if success:
                if details == "SKIPPED (requires input)":
                    skipped += 1
                    print(
                        f"{test_name}: {Colors.YELLOW}⊘ SKIPPED (requires input){Colors.RESET}"
                    )
                else:
                    passed += 1
                    print(f"{test_name}: {Colors.GREEN}✓ PASS{Colors.RESET}")
            else:
                failed += 1
                failed_results.append((test_name, details, diff_output))
                print(f"{test_name}: {Colors.RED}✗ FAIL{Colors.RESET} - {details}")

    # Sort results by test name for consistent output
    results.sort(key=lambda x: x[0])

    # Show detailed failure information with colored diffs
    if failed_results:
        print("-" * 60)
        print(f"{Colors.BOLD}Detailed failure information:{Colors.RESET}")
        print()

        for test_name, details, diff_output in failed_results:
            print(f"{Colors.BOLD}{Colors.RED}FAILED: {test_name}{Colors.RESET}")
            print(f"Reason: {details}")
            if diff_output:
                print()
                print(diff_output)
            print("-" * 40)
            print()

    print("-" * 60)
    if skipped > 0:
        total_color = Colors.GREEN if failed == 0 else Colors.RED
        print(
            f"{Colors.BOLD}Results:{Colors.RESET} {Colors.GREEN}{passed} passed{Colors.RESET}, {Colors.RED}{failed} failed{Colors.RESET}, {Colors.YELLOW}{skipped} skipped{Colors.RESET}"
        )
    else:
        total_color = Colors.GREEN if failed == 0 else Colors.RED
        print(
            f"{Colors.BOLD}Results:{Colors.RESET} {Colors.GREEN}{passed} passed{Colors.RESET}, {Colors.RED}{failed} failed{Colors.RESET}"
        )

    if failed > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
