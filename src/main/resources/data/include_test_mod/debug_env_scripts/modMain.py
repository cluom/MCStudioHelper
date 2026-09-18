# -*- coding: utf-8 -*-
from .QuModLibs.QuMod import *
from .Game import RELOAD_MOD, _RELOAD_MOD, RELOAD_ADDON, \
    RELOAD_WORLD, RELOAD_SHADERS
from .Config import DEBUG_CONFIG
import sys
lambda: "By Zero123"

REF = 0

class STD_OUT_WRAPPER(object):
    def __init__(self, baseIO):
        self.baseIO = baseIO
        self._buffer = []

    def __getattr__(self, name):
        return getattr(self.baseIO, name)

    def write(self, text):
        # Keep incomplete writes as bytes: Py2 mixed str/unicode join implicitly
        # decodes the whole buffer and can poison every following log record.
        if isinstance(text, type(u"")):
            text = text.encode("utf-8")
        elif not isinstance(text, bytes):
            text = str(text).encode("utf-8")
        self._buffer.append(text)
        buf = b"".join(self._buffer)

        if b"\n" not in buf:
            return

        lines = buf.split(b"\n")
        self._buffer = [lines.pop()]

        for line in lines:
            # Decode only complete lines, so split UTF-8 characters stay intact.
            # Invalid foreign log bytes cannot break subsequent game logging.
            line = line.decode("utf-8", "replace").encode("utf-8")
            if not line.strip():
                self.baseIO.write(b"\n")
            else:
                self.baseIO.write(b"[Python] " + line + b"\n")

    def close(self):
        return self.baseIO.close()

    def writelines(self, lines):
        for line in lines:
            self.write(line)

    def fileno(self):
        return self.baseIO.fileno()

stdout = sys.stdout
stderr = sys.stderr

def REST_STDOUT():
    sys.stdout = stdout
    sys.stderr = stderr

sys.stdout = STD_OUT_WRAPPER(sys.stdout)
sys.stderr = STD_OUT_WRAPPER(sys.stderr)

@PRE_SERVER_LOADER_HOOK
def SERVER_INIT():
    global REF
    REF += 1
    def _DESTROY():
        global REF
        REF -= 1
        if REF != 0:
            return
        REST_STDOUT()
    from .QuModLibs.Systems.Loader.Server import LoaderSystem
    LoaderSystem.REG_DESTROY_CALL_FUNC(_DESTROY)

def CLOnKeyPressInGame(args={}):
    if args["isDown"] != "0":
        return
    if args["screenName"] != "hud_screen" and not DEBUG_CONFIG.get("reload_key_global", False):
        return
    key = args["key"]
    if key == str(DEBUG_CONFIG.get("reload_key", "82")):
        RELOAD_MOD()
    elif key == str(DEBUG_CONFIG.get("reload_world_key", "")):
        RELOAD_WORLD()
    elif key == str(DEBUG_CONFIG.get("reload_addon_key", "")):
        RELOAD_ADDON()
    elif key == str(DEBUG_CONFIG.get("reload_shaders_key", "")):
        RELOAD_SHADERS()

@PRE_CLIENT_LOADER_HOOK
def CLIENT_INIT():
    global REF
    REF += 1
    from . import IPCSystem
    def _DESTROY():
        global REF
        REF -= 1
        IPCSystem.ON_CLIENT_EXIT()
        if REF != 0:
            return
        REST_STDOUT()
    from .QuModLibs.Systems.Loader.Client import LoaderSystem
    LoaderSystem.REG_DESTROY_CALL_FUNC(_DESTROY)

    LoaderSystem.getSystem().nativeStaticListen(
        "OnKeyPressInGame",
        CLOnKeyPressInGame
    )
    IPCSystem.ON_CLIENT_INIT()

try:
    _RELOAD_MOD()
except:
    pass