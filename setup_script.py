import os
import json
import shutil
from os.path import join


with open("meta-data.json") as f:
    data = json.load(f)
    for entry in data:
        # Please avoid re-using the script to overwrite build_subject
        # Some bugs have custom build_subject
        dest_dir = join(entry["subject"],entry["bug_id"])
        # build_subject = join(dest_dir, "build_subject")
        
