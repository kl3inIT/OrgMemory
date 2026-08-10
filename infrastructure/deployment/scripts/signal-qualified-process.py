#!/usr/bin/env python3
"""Signal an exact Linux PID/start-time identity through a pidfd."""

from __future__ import annotations

import ctypes
import errno
import os
import signal
import sys

# pidfd syscalls are part of the Linux generic syscall numbering used by the
# x86_64 and arm64 production architectures.
SYS_PIDFD_SEND_SIGNAL = 424
SYS_PIDFD_OPEN = 434
libc = ctypes.CDLL(None, use_errno=True)


def pidfd_open(pid: int) -> int:
    result = libc.syscall(SYS_PIDFD_OPEN, pid, 0)
    if result < 0:
        error = ctypes.get_errno()
        if error == errno.ESRCH:
            raise ProcessLookupError(error, os.strerror(error))
        raise OSError(error, os.strerror(error))
    return int(result)


def pidfd_send_signal(pidfd: int, signal_number: int) -> None:
    result = libc.syscall(SYS_PIDFD_SEND_SIGNAL, pidfd, signal_number, 0, 0)
    if result < 0:
        error = ctypes.get_errno()
        if error == errno.ESRCH:
            raise ProcessLookupError(error, os.strerror(error))
        raise OSError(error, os.strerror(error))


def main() -> int:
    if len(sys.argv) != 4:
        print(f"Usage: {sys.argv[0]} <pid> <proc-starttime> <signal>", file=sys.stderr)
        return 64
    pid = int(sys.argv[1])
    expected_starttime = sys.argv[2]
    signal_number = getattr(signal, f"SIG{sys.argv[3]}")
    try:
        pidfd = pidfd_open(pid)
    except ProcessLookupError:
        return 3
    try:
        try:
            with open(f"/proc/{pid}/stat", encoding="utf-8") as stat_file:
                stat_text = stat_file.read()
        except (FileNotFoundError, ProcessLookupError):
            return 3
        # Fields after the final ')' begin with field 3 (state); starttime is 22.
        fields_from_state = stat_text.rsplit(")", 1)[1].strip().split()
        observed_starttime = fields_from_state[19]
        if observed_starttime != expected_starttime:
            return 3
        try:
            pidfd_send_signal(pidfd, signal_number)
        except ProcessLookupError:
            return 3
        return 0
    finally:
        os.close(pidfd)


if __name__ == "__main__":
    raise SystemExit(main())
