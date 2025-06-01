#!/usr/bin/env python3

import os
import subprocess
import sys
import glob
from pathlib import Path
import re


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


def compile_test(test_name):
    try:
        result = subprocess.run(
            ["./run.py", test_name, "--no-compile"],
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


def compare_outputs(expected, actual):
    """Compare expected and actual outputs (ignoring exit codes)"""
    if expected is None or actual is None:
        return False, "Failed to parse output"

    # Normalize outputs for comparison
    expected_output = normalize_output(expected["output"])
    actual_output = normalize_output(actual["output"])

    output_match = expected_output == actual_output

    if output_match:
        return True, "Match"
    else:
        return (
            False,
            f"Output mismatch:\nExpected: '{expected_output}'\nActual: '{actual_output}'",
        )


def main():
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

    print(f"Found {len(out_files)} test files")
    print("-" * 60)

    passed = 0
    failed = 0

    for out_file in sorted(out_files):
        test_name = out_file.stem
        print(f"Testing {test_name}...")

        # Read expected output
        try:
            with open(out_file, "r") as f:
                expected_content = f.read()
        except Exception as e:
            print(f"  Error reading {out_file}: {e}")
            failed += 1
            continue

        expected = extract_expected_output(expected_content)
        if expected is None:
            print(f"  Error: Could not parse expected output from {out_file}")
            failed += 1
            continue

        # Compile the test
        if not compile_test(test_name):
            print(f"  ✗ FAIL (compilation failed)")
            failed += 1
            continue

        # Run with Ripes
        stdout, stderr, returncode = run_ripes(test_name)

        if stdout is None:
            print(f"  Error: Failed to run Ripes for {test_name}")
            failed += 1
            continue

        actual = extract_ripes_output(stdout, stderr, returncode)
        if actual is None:
            print(f"  Error: Could not parse Ripes output for {test_name}")
            failed += 1
            continue

        # Compare outputs
        match, details = compare_outputs(expected, actual)

        if match:
            print(f"  ✓ PASS")
            passed += 1
        else:
            print(f"  ✗ FAIL")
            for line in details.split("\n"):
                print(f"    {line}")
            failed += 1

        print()

    print("-" * 60)
    print(f"Results: {passed} passed, {failed} failed")

    if failed > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
