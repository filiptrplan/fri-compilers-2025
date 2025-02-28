#!/usr/bin/env python3
# This helper was generate using GenAI. This is the only part of the codebase where
# AI was used.
import argparse
import subprocess
import os
import sys
import re
import tempfile
import shutil

def check_for_error(output):
    """Check if the output contains an error message (last line contains ':-(')"""
    lines = output.strip().split('\n')
    if lines and ':-(' in lines[-1]:
        return True
    return False

def main():
    # Set up argument parser with a more detailed description
    parser = argparse.ArgumentParser(
        description='Run compiler tests for the Prev25 compiler with various options',
        epilog='''
File Structure Information:
  - prg/*.prev25: Test files written in the Prev25 language
  - src/compiler/phase/lexan/: Lexical analyzer phase
  - src/compiler/phase/synan/: Syntax analyzer phase
  
How This Program Works:
  1. Builds the compiler using 'make'
  2. Runs specified tests from the prg/ directory
  3. Can test for expected errors by uncommenting lines prefixed with #
  4. Supports debugging specific compiler phases
  
Examples:
  ./run.py lexer/test          Run the test in prg/lexer/test.prev25
  ./run.py --clean lexer/test  Clean build before running test
  ./run.py --phase lexan       Debug the lexical analysis phase
  ./run.py --error lexer/test  Test each commented line for expected errors
        ''',
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument('testname', nargs='?', default='', 
                        help='Name of the test to run (without .prev25 extension)')
    parser.add_argument('--clean', action='store_true', 
                        help='Run make clean before building')
    parser.add_argument('--phase', default='', 
                        help='Set the DEBUG environment variable to debug a specific phase (e.g., lexan, synan)')
    parser.add_argument('--error', action='store_true', 
                        help='Test each commented line for errors (useful for testing invalid syntax)')
    
    args = parser.parse_args()
    
    # If no arguments provided, print help and exit
    if len(sys.argv) == 1:
        parser.print_help()
        sys.exit(0)
    
    # Get the root directory of the project
    root_dir = os.path.dirname(os.path.abspath(__file__))
    
    try:
        # Change to the root directory
        os.chdir(root_dir)
        
        # Run make clean if requested
        if args.clean:
            print("Running make clean...")
            subprocess.run(['make', 'clean'], check=True)
        
        # Run make in the root directory
        print("Building project...")
        result = subprocess.run(['make'], capture_output=True, text=True)
        
        # Check if make command was successful
        if result.returncode != 0:
            print("Build failed. Error output:")
            print(result.stdout)
            print(result.stderr)
            sys.exit(result.returncode)
        else:
            print("Build completed successfully.")
        
        # Run the test
        if args.testname:
            print(f"Running test: {args.testname}")
            env = os.environ.copy()
            # Set DEBUG env var to the phase if specified, otherwise keep any existing DEBUG value
            if args.phase:
                env['DEBUG'] = args.phase
                print(f"Debugging phase: {args.phase}")
            elif 'DEBUG' not in env:
                # Default to 'synan' if no phase specified and no DEBUG in environment
                env['DEBUG'] = 'synan'
                print("Debugging phase: synan (default)")
            else:
                print(f"Using existing DEBUG setting: {env.get('DEBUG')}")
            
            if args.error:
                run_error_tests(args.testname, env)
            else:
                subprocess.run(['make', '-C', 'prg', args.testname], env=env, check=True)
        else:
            print("No test specified. Build completed successfully.")
            
    except subprocess.CalledProcessError as e:
        print(f"Error: Command failed with return code {e.returncode}")
        sys.exit(e.returncode)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

def run_error_tests(testname, env):
    """Run tests on each commented line in the file to check for errors"""
    test_file_path = f"prg/{testname}.prev25"
    
    # Check if the test file exists
    if not os.path.exists(test_file_path):
        print(f"Error: Test file {test_file_path} not found")
        sys.exit(1)
    
    # Create a temporary directory for our modified test files
    with tempfile.TemporaryDirectory() as temp_dir:
        temp_file_path = os.path.join(temp_dir, f"{testname}.prev25")
        
        # Read the original file
        with open(test_file_path, 'r') as f:
            original_content = f.read()
            
        # Find all commented lines (lines starting with #) that have content after the comment
        commented_lines = []
        for i, line in enumerate(original_content.split('\n')):
            if line.strip().startswith('#'):
                # Check if there's actual content after removing the comment prefix and whitespace
                uncommented = re.sub(r'^\s*#\s*', '', line)
                if uncommented.strip():  # Only include if there's non-whitespace content
                    commented_lines.append((i, line))
        
        if not commented_lines:
            print("No commented lines found in the test file.")
            return
            
        print(f"Found {len(commented_lines)} commented lines to test.")
        
        # Lines that didn't cause errors
        no_error_lines = []
        
        # Test each commented line
        for line_num, line in commented_lines:
            # Create a copy of the original content
            modified_content = original_content.split('\n')
            
            # Uncomment the line (remove the # prefix)
            uncommented_line = re.sub(r'^\s*#\s*', '', line)
            modified_content[line_num] = uncommented_line
            
            # Write the modified content to the temporary file
            with open(temp_file_path, 'w') as f:
                f.write('\n'.join(modified_content))
            
            # Copy the temp file to the prg directory
            shutil.copy(temp_file_path, test_file_path)
            
            try:
                # Run the test and capture output
                print(f"\nTesting line {line_num + 1}: {uncommented_line}")
                result = subprocess.run(['make', '-C', 'prg', testname], 
                                       env=env, 
                                       capture_output=True, 
                                       text=True)
                
                # Check if the output contains an error
                if not check_for_error(result.stdout + result.stderr):
                    no_error_lines.append((line_num + 1, uncommented_line))
                    print(f"  No error detected!")
                else:
                    print(f"  Error detected as expected.")
            except subprocess.CalledProcessError:
                # If the process fails, it's likely due to an error, which is what we want
                print(f"  Error detected as expected.")
            
            # Restore the original file after each test
            with open(test_file_path, 'w') as f:
                f.write(original_content)
        
        # Print summary of lines that didn't cause errors
        print("\n=== SUMMARY ===")
        if no_error_lines:
            print(f"The following {len(no_error_lines)} lines did NOT cause errors (unexpected):")
            for line_num, line in no_error_lines:
                print(f"Line {line_num}: {line}")
        else:
            print("All tested lines caused errors as expected. Good job!")

if __name__ == "__main__":
    main()
