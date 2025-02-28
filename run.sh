#!/usr/bin/env python3
# This file is deprecated. Please use run.py instead.
import os
import sys
import subprocess

print("Warning: run.sh is deprecated. Please use run.py instead.")
print("Forwarding to run.py...")

args = sys.argv[1:]
cmd = [sys.executable, "run.py"] + args
os.execvp(sys.executable, cmd)
