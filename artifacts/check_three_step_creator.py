import sys, time, re, json
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
import check_guided_creator as ui
ui.OUT = Path("artifacts/three-step-creator")
ui.OUT.mkdir(exist_ok=True)
checks=[]
def tap(label):
    for _ in range(8):
        items=[n for n in ui.nodes() if label in (n.get("text"),n.get("content-desc"))]
        if items:
            bounds=list(map(int,re.findall(r"\d+",items[0].get("bounds"))))
            ui.adb("shell","input","tap",str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
            time.sleep(1)
            print("Tap",label,flush=True)
            return
        time.sleep(.5)
    raise AssertionError(label)
ui.tap=tap
def check(label):
    assert any(n.get("text")==label for n in ui.nodes()), label
    checks.append(label)
def find(label):
    for _ in range(7):
        for n in ui.nodes():
            if label in (n.get("text"), n.get("content-desc")):
                bounds=list(map(int,re.findall(r"\d+",n.get("bounds"))))
                if bounds[3]-bounds[1] > 30 and bounds[3] < 2450: return
        ui.adb("shell","input","swipe","640","1900","640","850","250")
        time.sleep(.4)
    raise AssertionError(label)
ui.launch()
check("1 of 3")
ui.tap("Lavender")
ui.shot("colors")
find("Charcoal"); ui.tap("Charcoal")
find("Custom background color"); ui.tap("Custom background color")
check("Background color")
ui.shot("background-picker")
ui.tap("Done")
find("Edit all color values"); ui.tap("Edit all color values")
find("Text on accent"); ui.tap("Text on accent")
check("Text on accent"); ui.tap("Done")
ui.tap("Next"); check("2 of 3")
check("Use custom app icon")
ui.shot("icon")
ui.tap("Use custom app icon")
ui.tap("Next"); check("3 of 3")
ui.shot("name")
ui.tap("Theme name"); ui.adb("shell","input","text","Three%sstep%spreview")
ui.adb("shell","input","keyevent","111")
ui.tap("Save and use theme")
time.sleep(2)
expected="app.still.themepreview/app.still.CustomThemeLauncher6_4"
def active_launcher():
    return ui.adb("shell","cmd","package","query-activities","--brief","-a","android.intent.action.MAIN","-c","android.intent.category.LAUNCHER","-p","app.still.themepreview").decode()
assert expected in active_launcher(), active_launcher()
assert "DefaultLauncher" not in active_launcher(), active_launcher()
checks.append("Custom launcher alias activated; default disabled")
ui.shot("saved-theme")
ui.adb("shell","input","keyevent","3")
ui.adb("shell","input","swipe","640","2650","640","650","350")
time.sleep(1)
ui.shot("launcher-icons")
catalog=json.loads(ui.adb("shell","run-as","app.still.themepreview","cat","no_backup/community-themes/catalog.json"))
assert any(item.get("customIcon") for item in catalog["themes"])
checks.append("App icon preference persisted with theme")
ui.adb("shell","am","force-stop","app.still.themepreview")
ui.adb("shell","am","start","-W","-n",expected)
time.sleep(2)
assert expected in active_launcher(), active_launcher()
checks.append("Custom alias opens app and survives process restart")
(ui.OUT/"runtime-checks.json").write_text(json.dumps(checks,indent=2))
print(json.dumps(checks),flush=True)
