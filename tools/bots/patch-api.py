# Rewrites api-version inside a built plugin jar so it can load on an older test server.
import sys, zipfile, shutil
src, dst, version = sys.argv[1], sys.argv[2], sys.argv[3]
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst + '.tmp', 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename == 'plugin.yml':
            data = data.decode().replace("api-version: '26.3'", f"api-version: '{version}'").encode()
        zout.writestr(item, data)
shutil.move(dst + '.tmp', dst)
