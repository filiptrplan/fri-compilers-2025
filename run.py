#!/usr/bin/env python3
import argparse
import subprocess
import os
import sys

def main():
    # Set up argument parser
    parser = argparse.ArgumentParser(description='Run compiler tests with optional cleaning')
    parser.add_argument('testname', nargs='?', default='', help='Name of the test to run')
    parser.add_argument('--clean', action='store_true', help='Run make clean before building')
    parser.add_argument('--phase', default='', help='Set the DEBUG environment variable to debug a specific phase')
    
    args = parser.parse_args()
    
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
            
            subprocess.run(['make', '-C', 'prg', args.testname], env=env, check=True)
        else:
            print("No test specified. Build completed successfully.")
            
    except subprocess.CalledProcessError as e:
        print(f"Error: Command failed with return code {e.returncode}")
        sys.exit(e.returncode)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
