// eslint-disable-next-line node/no-unpublished-require
import ffi from 'ffi-napi';
import ref from 'ref-napi';
import StructType from 'ref-struct-napi';

const WINDOWS = process.platform === 'win32';

function getUser32() {
  if (!WINDOWS) return null;
  return ffi.Library('user32', {
    GetForegroundWindow: [ref.types.uint32, []],
    GetWindowTextA: [ref.types.bool, [ref.types.uint32, ref.types.CString, ref.types.uint32]],
    GetWindowTextLengthA: [ref.types.uint32, [ref.types.uint32]],
    GetWindowThreadProcessId: [ref.types.uint32, [ref.types.uint32, ref.types.uint32]],
    IsWindowVisible: [ref.types.bool, [ref.types.uint32]],
    GetClassNameA: [ref.types.bool, [ref.types.uint32, ref.types.CString, ref.types.uint32]],
  });
}

function getPsapi() {
  if (!WINDOWS) return null;
  return ffi.Library('psapi', {
    GetModuleBaseNameA: [ref.types.bool, [ref.types.uint32, ref.types.uint32, ref.types.CString, ref.types.uint32]],
    OpenProcess: [ref.types.uint32, [ref.types.uint32, ref.types.bool, ref.types.uint32]],
    CloseHandle: [ref.types.bool, [ref.types.uint32]],
  });
}

class WindowDetector {
  constructor() {
    this.user32 = getUser32();
    this.psapi = getPsapi();
    this.lastProcessName = null;
    this.lastWindowTitle = null;
  }

  getActiveWindowInfo() {
    if (!this.user32) return null;

    try {
      const hwnd = this.user32.GetForegroundWindow();
      if (!hwnd) return null;

      const length = this.user32.GetWindowTextLengthA(hwnd);
      let windowTitle = null;
      if (length > 0) {
        const buf = Buffer.alloc(length + 1);
        this.user32.GetWindowTextA(hwnd, buf, length + 1);
        windowTitle = buf.toString('utf8').replace(/\0/g, '');
      }

      const pidRef = ref.alloc(ref.types.uint32);
      this.user32.GetWindowThreadProcessId(hwnd, pidRef);
      const pid = Number(pidRef.deref());
      const processName = this.getProcessNameByPid(pid);

      const info = {
        processName: processName || this.lastProcessName,
        windowTitle: windowTitle || this.lastWindowTitle,
      };

      if (info.processName !== this.lastProcessName || info.windowTitle !== this.lastWindowTitle) {
        this.lastProcessName = info.processName;
        this.lastWindowTitle = info.windowTitle;
        return info;
      }
      return null;
    } catch (err) {
      console.error('[WindowDetector] Failed to get active window:', err.message);
      return null;
    }
  }

  getProcessNameByPid(pid) {
    if (!this.psapi) return null;
    try {
      const PROCESS_QUERY_INFORMATION = 0x0400;
      const PROCESS_VM_READ = 0x0010;
      const handle = this.psapi.OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, false, pid);
      if (handle === 0) return null;

      const buf = Buffer.alloc(256);
      const result = this.psapi.GetModuleBaseNameA(handle, 0, buf, 256);
      this.psapi.CloseHandle(handle);

      if (result) {
        return buf.toString('utf8').replace(/\0/g, '');
      }
    } catch (err) {
      console.error('[WindowDetector] Failed to get process name:', err.message);
    }
    return null;
  }

  startPolling(intervalMs = 500, callback) {
    if (!this.user32) {
      console.warn('[WindowDetector] Window detection is only available on Windows.');
      return;
    }
    setInterval(() => {
      const info = this.getActiveWindowInfo();
      if (info) {
        callback(info);
      }
    }, intervalMs);
  }
}

export { WindowDetector };