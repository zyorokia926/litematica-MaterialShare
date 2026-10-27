"""Starts programs on a second Windows desktop that nobody looks at.

A desktop of its own has its own windows, its own keyboard focus and its own mouse grab, so a game that runs on it can never take the
keyboard or the mouse away from the person who sits at the PC (the games hide and clip the mouse and ask for the focus).
Used by two-clients.py. Windows only.
"""
import ctypes
import msvcrt
import os
import subprocess
from ctypes import wintypes

kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
user32 = ctypes.WinDLL("user32", use_last_error=True)

GENERIC_ALL = 0x10000000
GENERIC_READ, GENERIC_WRITE = 0x80000000, 0x40000000
FILE_SHARE_READ, FILE_SHARE_WRITE = 1, 2
CREATE_ALWAYS, OPEN_EXISTING = 2, 3
STARTF_USESTDHANDLES = 0x100
CREATE_NO_WINDOW, CREATE_UNICODE_ENVIRONMENT = 0x08000000, 0x400
HANDLE_FLAG_INHERIT = 1
STILL_ACTIVE = 259


class SECURITY_ATTRIBUTES(ctypes.Structure):
    _fields_ = [("nLength", wintypes.DWORD), ("lpSecurityDescriptor", wintypes.LPVOID), ("bInheritHandle", wintypes.BOOL)]


class STARTUPINFOW(ctypes.Structure):
    _fields_ = [("cb", wintypes.DWORD), ("lpReserved", wintypes.LPWSTR), ("lpDesktop", wintypes.LPWSTR), ("lpTitle", wintypes.LPWSTR),
                ("dwX", wintypes.DWORD), ("dwY", wintypes.DWORD), ("dwXSize", wintypes.DWORD), ("dwYSize", wintypes.DWORD),
                ("dwXCountChars", wintypes.DWORD), ("dwYCountChars", wintypes.DWORD), ("dwFillAttribute", wintypes.DWORD),
                ("dwFlags", wintypes.DWORD), ("wShowWindow", wintypes.WORD), ("cbReserved2", wintypes.WORD),
                ("lpReserved2", ctypes.POINTER(ctypes.c_byte)), ("hStdInput", wintypes.HANDLE), ("hStdOutput", wintypes.HANDLE),
                ("hStdError", wintypes.HANDLE)]


class PROCESS_INFORMATION(ctypes.Structure):
    _fields_ = [("hProcess", wintypes.HANDLE), ("hThread", wintypes.HANDLE), ("dwProcessId", wintypes.DWORD), ("dwThreadId", wintypes.DWORD)]


kernel32.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD, ctypes.POINTER(SECURITY_ATTRIBUTES), wintypes.DWORD,
                                 wintypes.DWORD, wintypes.HANDLE]
kernel32.CreateFileW.restype = wintypes.HANDLE
kernel32.CreatePipe.argtypes = [ctypes.POINTER(wintypes.HANDLE), ctypes.POINTER(wintypes.HANDLE), ctypes.POINTER(SECURITY_ATTRIBUTES), wintypes.DWORD]
kernel32.SetHandleInformation.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.DWORD]
kernel32.CreateProcessW.argtypes = [wintypes.LPCWSTR, wintypes.LPWSTR, wintypes.LPVOID, wintypes.LPVOID, wintypes.BOOL, wintypes.DWORD,
                                    wintypes.LPVOID, wintypes.LPCWSTR, ctypes.POINTER(STARTUPINFOW), ctypes.POINTER(PROCESS_INFORMATION)]
kernel32.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
user32.CreateDesktopW.argtypes = [wintypes.LPCWSTR, wintypes.LPCWSTR, wintypes.LPVOID, wintypes.DWORD, wintypes.DWORD, wintypes.LPVOID]
user32.CreateDesktopW.restype = wintypes.HANDLE
user32.CloseDesktop.argtypes = [wintypes.HANDLE]
user32.OpenInputDesktop.restype = wintypes.HANDLE


def create_desktop(name):
    """a new desktop in our window station; returns its handle (keep it open while programs run on it)"""
    h = user32.CreateDesktopW(name, None, None, 0, GENERIC_ALL, None)
    if not h:
        raise OSError(f"CreateDesktop failed: {ctypes.get_last_error()}")
    return h


def close_desktop(handle):
    user32.CloseDesktop(handle)


def _env_block(env):
    text = "".join(f"{k}={v}\0" for k, v in env.items()) + "\0"
    return ctypes.create_unicode_buffer(text, len(text))


class DesktopProc:
    """a program started on the desktop `desktop` (name only); its output goes to the file `log`. Has pid, poll() and, when
    stdin=True, a stdin file object to write to, like subprocess.Popen"""

    def __init__(self, args, cwd, env, desktop, log, stdin=False):
        sa = SECURITY_ATTRIBUTES(ctypes.sizeof(SECURITY_ATTRIBUTES), None, True)
        h_out = kernel32.CreateFileW(str(log), GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, ctypes.byref(sa), CREATE_ALWAYS, 0, None)
        self.stdin = None
        if stdin:
            h_in, h_write = wintypes.HANDLE(), wintypes.HANDLE()
            kernel32.CreatePipe(ctypes.byref(h_in), ctypes.byref(h_write), ctypes.byref(sa), 0)
            kernel32.SetHandleInformation(h_write, HANDLE_FLAG_INHERIT, 0)  # the child must not inherit the write end
            self.stdin = os.fdopen(msvcrt.open_osfhandle(h_write.value, os.O_WRONLY), "wb", buffering=0)
            stdin_handle = h_in.value
        else:
            stdin_handle = kernel32.CreateFileW("NUL", GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, ctypes.byref(sa), OPEN_EXISTING, 0, None)
        si = STARTUPINFOW()
        si.cb = ctypes.sizeof(si)
        si.lpDesktop = desktop if "\\" in desktop else "WinSta0\\" + desktop
        si.dwFlags = STARTF_USESTDHANDLES
        si.hStdInput, si.hStdOutput, si.hStdError = stdin_handle, h_out, h_out
        pi = PROCESS_INFORMATION()
        # .bat files need cmd.exe
        cmdline = 'cmd.exe /d /s /c "' + subprocess.list2cmdline([str(a) for a in args]) + '"'
        ok = kernel32.CreateProcessW(None, ctypes.create_unicode_buffer(cmdline), None, None, True, CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT,
                                     _env_block(env), str(cwd), ctypes.byref(si), ctypes.byref(pi))
        err = ctypes.get_last_error()
        kernel32.CloseHandle(h_out)
        kernel32.CloseHandle(stdin_handle)
        if not ok:
            raise OSError(f"CreateProcess failed: {err}")
        kernel32.CloseHandle(pi.hThread)
        self._handle = pi.hProcess
        self.pid = pi.dwProcessId

    def poll(self):
        code = wintypes.DWORD()
        kernel32.GetExitCodeProcess(self._handle, ctypes.byref(code))
        return None if code.value == STILL_ACTIVE else code.value


user32.EnumDesktopWindowsW = user32.EnumDesktopWindows
user32.EnumDesktopWindows.argtypes = [wintypes.HANDLE, wintypes.LPVOID, wintypes.LPARAM]


def count_windows(desktop):
    """how many windows of the game (class GLFW...) exist on the desktop"""
    found = []

    def cb(hwnd, _):
        buf = ctypes.create_unicode_buffer(64)
        user32.GetClassNameW(hwnd, buf, 64)
        if buf.value.startswith("GLFW"):
            found.append(hwnd)
        return True
    proc = ctypes.WINFUNCTYPE(ctypes.c_int, wintypes.HWND, wintypes.LPARAM)
    user32.EnumDesktopWindows(desktop, proc(cb), 0)
    return len(found)
