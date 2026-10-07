import subprocess, sys, xml.etree.ElementTree as ET, re
from pathlib import Path
adb = r'C:\Users\Mate\AppData\Local\Android\Sdk\platform-tools\adb.exe'
base = [adb, '-s', 'emulator-5554']
def run(*args):
    return subprocess.check_output(base + list(args))
def dump():
    run('shell', 'rm', '-f', '/sdcard/theme-window.xml')
    output = run('shell', 'uiautomator', 'dump', '/sdcard/theme-window.xml')
    if b'ERROR:' in output: raise Exception(output.decode())
    return ET.fromstring(run('shell', 'cat', '/sdcard/theme-window.xml'))
command = sys.argv[1]
if command == 'dump':
    for n in dump().iter('node'):
        label = n.get('text') or n.get('content-desc')
        if label: print(label, n.get('bounds'), 'checked='+n.get('checked',''), 'enabled='+n.get('enabled',''))
elif command == 'tap':
    nodes = [n for n in dump().iter('node') if sys.argv[2] in [n.get('text'), n.get('content-desc')]]
    if not nodes: raise Exception('No such visible UI element: '+sys.argv[2])
    b = list(map(int, re.findall(r'\d+', nodes[0].get('bounds'))))
    run('shell', 'input', 'tap', str((b[0]+b[2])//2), str((b[1]+b[3])//2))
elif command == 'capture':
    Path(sys.argv[2]).write_bytes(run('exec-out', 'screencap', '-p'))
