#!/usr/bin/env python3

import sys
import subprocess
import glob

def print_usage():
    print("./mmtk-harness.py <script_name> <plan> <options>")

positionals = []
opts = {
    "verbose": 2,
    "initHeap": "20M",
    #"scheduler": "DETERMINISTIC",
    "timeout": 30,
    # "variableSizeHeap" : "true",
    "variableSizeHeap" : "false",
    "repeat" : 1,
}

JAVA = "java"

cmds = [JAVA, "-ea", "-jar", "dist/mmtk-harness.jar"]
for arg in sys.argv[1:]:
    if "=" in arg:
        k,v = arg.split("=")
        opts[k] = v
    elif arg == "--ls":
        from os.path import basename
        scripts = glob.glob("./MMTk/harness/test-scripts/*.script")
        [print(basename(path)) for path in scripts]
        sys.exit(0)
    elif arg == "--help":
        print_usage()
        sys.exit(0)
    else:
        positionals.append(arg)

script_name = positionals[1]
plan = positionals[0]

repeat = int(opts["repeat"])
del opts["repeat"]

cmds.append("MMTk/harness/test-scripts/{}.script".format(script_name))
cmds.append("plan={}".format(plan))

cmds = cmds + ["{}={}".format(k,v) for k,v in opts.items()]

print(cmds)
sys.stdout.flush()

returncodes = []
for i in range(repeat):
    print("iteration #{}".format(i))
    sys.stdout.flush()

    ret = subprocess.run(cmds)
    returncodes.append(ret.returncode)

print("return codes:")
print(returncodes)
