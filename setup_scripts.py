import os
import json
import shutil
from os.path import join
with open("meta-data.json") as f:
    data = json.load(f)
    for entry in data:
        dest_dir = join(entry["subject"],entry["bug_id"])
        os.makedirs(dest_dir, exist_ok=True)
        
        for x in ["build_subject","clean_subject","config_subject","test_subject", "install_deps"]:
            shutil.copy2(x+"_"+entry["build_system"],join(dest_dir,x))
        if entry["build_system"] == "maven":
            pass
        elif entry["build_system"] == "gradle":    
            os.system("sed -i 's|<REPLACE>|{}|' {}".format(entry["compile_cmd"],join(dest_dir,"build_subject")))
            os.system("sed -i 's|<REPLACE>|{}|' {}".format(entry["test_all_cmd"],join(dest_dir,"test_subject")))
            
        else:
            raise Exception("Unknown build system: "+entry["build_system"])
        
        