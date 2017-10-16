#!/usr/bin/env python3

import sys
import subprocess
import os

def print_usage():
    print("./run-test.py <testsuite> <plan>")

# plan = "FastAdaptiveRCImmixConcurrent"
plan = "production"
if len(sys.argv) > 1: plan = sys.argv[1]
testsuite = "basic"
if len(sys.argv) > 2: testsuite = sys.argv[2]

host = "localhost"

cmds = ["./bin/buildit", host, "-t", testsuite, plan]

ret = subprocess.run(cmds)
print("return state:")
print(ret)
