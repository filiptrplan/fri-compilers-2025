#!/usr/bin/env python3

import os
import sys
import shutil
import zipfile
import tempfile
import fnmatch
import re

VPISNA = '63220340'

def parse_gitignore(gitignore_path):
    """Parse .gitignore file and return patterns to ignore."""
    if not os.path.exists(gitignore_path):
        return []
    
    patterns = []
    with open(gitignore_path, 'r') as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#'):
                patterns.append(line)
    return patterns

def should_ignore(path, ignore_patterns):
    """Check if a path should be ignored based on gitignore patterns."""
    # Convert path to relative path from project root
    rel_path = os.path.normpath(path)
    
    for pattern in ignore_patterns:
        # Handle negation patterns (those starting with !)
        if pattern.startswith('!'):
            negated = True
            pattern = pattern[1:]
        else:
            negated = False
            
        # Handle directory-specific patterns (those ending with /)
        if pattern.endswith('/'):
            if os.path.isdir(rel_path):
                pattern = pattern[:-1]
            else:
                continue
                
        # Convert gitignore pattern to regex pattern
        regex_pattern = fnmatch.translate(pattern)
        match = re.match(regex_pattern, rel_path)
        
        if match:
            return not negated
            
    return False

def main():
    if len(sys.argv) < 2:
        print("Error: First argument must be the phase name.")
        sys.exit(1)
    
    phase_name = sys.argv[1]
    zip_filename = f"{VPISNA}-{phase_name}.zip"
    temp_dir = "prev25"
    
    # Remove existing zip file and temp directory if they exist
    if os.path.exists(zip_filename):
        os.remove(zip_filename)
    if os.path.exists(temp_dir):
        shutil.rmtree(temp_dir)
    
    # Create temp directory
    os.makedirs(temp_dir)
    
    # Parse .gitignore
    ignore_patterns = parse_gitignore('.gitignore')
    
    # Copy src directory
    if os.path.exists('src'):
        shutil.copytree('src', os.path.join(temp_dir, 'src'), 
                        ignore=lambda dir, files: [f for f in files if should_ignore(os.path.join(dir, f), ignore_patterns)])
    
    # Copy lib directory (excluding .jar files)
    if os.path.exists('lib'):
        shutil.copytree('lib', os.path.join(temp_dir, 'lib'),
                        ignore=lambda dir, files: [f for f in files if f.endswith('.jar') or should_ignore(os.path.join(dir, f), ignore_patterns)])
    
    # Create prg directory and copy Makefile
    os.makedirs(os.path.join(temp_dir, 'prg'), exist_ok=True)
    if os.path.exists(os.path.join('prg', 'Makefile')):
        shutil.copy2(os.path.join('prg', 'Makefile'), os.path.join(temp_dir, 'prg', 'Makefile'))
    
    # Copy root Makefile
    if os.path.exists('Makefile'):
        shutil.copy2('Makefile', os.path.join(temp_dir, 'Makefile'))
    
    # Create zip file
    with zipfile.ZipFile(zip_filename, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for root, dirs, files in os.walk(temp_dir):
            for file in files:
                file_path = os.path.join(root, file)
                zipf.write(file_path, os.path.relpath(file_path, os.path.dirname(temp_dir)))
    
    # Clean up
    shutil.rmtree(temp_dir)
    print(f"Created {zip_filename}")

if __name__ == "__main__":
    main()
