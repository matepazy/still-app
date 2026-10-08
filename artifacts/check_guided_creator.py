import subprocess, time, re, os, xml.etree.ElementTree as ET
from pathlib import Path
ADB = r'C:/Users/Mate/AppData/Local/Android/Sdk/platform-tools/adb.exe'
OUT = Path('.impeccable/review')
def adb(*args):
    return subprocess.check_output([ADB, '-s', os.environ.get('GUIDED_DEVICE', 'emulator-5554'), *args])
def nodes():
    path = f'/sdcard/guided-{time.time_ns()}.xml'
    result = adb('shell', 'uiautomator', 'dump', path)
    if b'dumped to' not in result:
        time.sleep(1)
        result = adb('shell', 'uiautomator', 'dump', path)
    assert b'dumped to' in result, result
    data = adb('shell', 'cat', path)
    adb('shell', 'rm', path)
    return list(ET.fromstring(data).iter('node'))
def tap(label):
    items = [n for n in nodes() if label in (n.get('text'), n.get('content-desc'))]
    assert items, f'Missing control: {label}'
    bounds = list(map(int, re.findall(r'\d+', items[0].get('bounds'))))
    print(f'Tap {label}: {bounds}', flush=True)
    adb('shell', 'input', 'tap', str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2))
    time.sleep(1.5)
def shot(name):
    time.sleep(1.5)
    (OUT / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
def launch(light=False):
    adb('shell', 'am', 'force-stop', 'app.still.themepreview')
    time.sleep(1)
    adb('shell', 'am', 'start', '-S', '-W', '-f', '0x10008000', '-n', 'app.still.themepreview/app.still.ui.preview.DesignPreviewActivity', '--es', 'screen', 'theme-creator', '--ez', 'light', str(light).lower())
    time.sleep(2)
    for _ in range(5):
        if any(n.get('text') == 'Choose your accent' for n in nodes()):
            break
        time.sleep(.5)
    else:
        raise AssertionError('Creator did not finish loading')
if __name__ == '__main__':
    launch()
    shot('phone-accent')
    tap('Lavender'); tap('Next')
    shot('phone-background')
    tap('Slate'); tap('Next')
    shot('phone-details')
    tap('Text on accent')
    shot('phone-color-drawer')
    tap('Done'); tap('Next')
    shot('phone-save')
    tap('Theme name')
    adb('shell', 'input', 'text', 'Guided%spreview')
    shot('phone-keyboard')
    tap('Save and use theme')
    for _ in range(8):
        if any(n.get('text') == 'Guided preview' for n in nodes()):
            break
        adb('shell', 'input', 'swipe', '640', '2200', '640', '700', '300')
        time.sleep(.4)
    else:
        raise AssertionError('Saved theme missing')
    print('Four steps, palette choices, color drawer, name entry and save verified.')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
    launch()
    shot('phone-enlarged')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'wm', 'size', '1600x2400')
    adb('shell', 'wm', 'density', '320')
    launch()
    shot('tablet')
    adb('shell', 'wm', 'size', 'reset')
    adb('shell', 'wm', 'density', 'reset')
    launch(True)
    shot('phone-light')
    print('Captured normal, enlarged, light phone and 800dp tablet. Emulator configuration restored.')
