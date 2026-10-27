"""The memory safety device shared by every long job (builds and tests): stop when the free memory of the whole PC drops below 3 GB.

The owner's rule (2026-10-28): every job may use the PC fully, but it must stop when less than MIN_FREE_GB of RAM is left.
"""
import subprocess
import time

try:
    import psutil
except ImportError:  # no psutil (for example a plain Linux box): the jobs run without this guard
    psutil = None

MIN_FREE_GB = 3


def free_gb():
    return psutil.virtual_memory().available / 2**30 if psutil else float("inf")


def kill_tree(proc):
    if psutil is None:
        proc.kill()
        return
    try:
        parent = psutil.Process(proc.pid)
        for p in parent.children(recursive=True) + [parent]:
            try:
                p.kill()
            except psutil.Error:
                pass
    except psutil.Error:
        pass


def run_guarded(cmd, **kw):
    """subprocess.run(cmd, check=True) that kills the whole process tree when less than MIN_FREE_GB is free"""
    proc = subprocess.Popen(cmd, **kw)
    while proc.poll() is None:
        if free_gb() < MIN_FREE_GB:
            kill_tree(proc)
            raise SystemExit(f"STOPPED: less than {MIN_FREE_GB} GB of free memory ({free_gb():.1f} GB), the job was killed")
        time.sleep(1)
    if proc.returncode:
        raise subprocess.CalledProcessError(proc.returncode, cmd)
