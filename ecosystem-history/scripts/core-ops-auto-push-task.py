import os
import runpy
os.environ["ROOTRECORD_AUTO_PUSH"] = "1"
runpy.run_path(str(__import__('pathlib').Path(__file__).with_name('auto-push.py')), run_name='__main__')
