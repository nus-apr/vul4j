import csv
import json
import os
import shutil

DATASET_PATH = os.path.join(os.getcwd(), "dataset", "vul4j_dataset.csv")
result = []
id = 0
with open(DATASET_PATH) as f:
    reader = csv.DictReader(f, delimiter=',', )
    for row in reader:
        vul_id = row['vul_id'].strip()
        cve_id = row['cve_id'].strip()
        repo_slug = row['repo_slug'].strip().replace("/", "_")
        build_system = row['build_system'].strip()
        compliance_level = int(row['compliance_level'].strip())
        compile_cmd = row['compile_cmd'].strip()
        test_all_cmd = row['test_all_cmd'].strip()
        test_cmd = row['test_cmd'].strip()
        cmd_options = row['cmd_options'].strip()
        src_classes_dir = row['src_classes'].strip()
        test_classes_dir = row['test_classes'].strip()
        human_patch_url = row['human_patch'].strip()

        failing_module_dir = row['failing_module'].strip()
        failing_module_dir = "" if failing_module_dir == "root" else failing_module_dir
        failing_test = row['failing_tests'].strip().split("#")[0]

        path_to_create = os.path.join(os.getcwd(), row['cve_id'].strip(), row['vul_id'].strip())

        # os.makedirs(path_to_create, 0o775, exist_ok=True)
        # shutil.copy2(os.path.join(os.getcwd(), "deps.sh"), path_to_create)

        id += 1
        result.append(
            {
                "id": id,
                "subject": row['cve_id'].strip(),
                "bug_id": row['vul_id'].strip(),
                "failing_module": failing_module_dir,
                "source_directory": os.path.join(failing_module_dir, row['src'].strip()),
                "class_directory": os.path.join(failing_module_dir, row['src_classes'].strip()),
                "test_directory": os.path.join(failing_module_dir, row['test'].strip()),
                "test_class_directory": os.path.join(failing_module_dir, row['test_classes'].strip()),
                "line_numbers": [],
                "failing_test": [failing_test],
                "passing_test": [],
                "dependencies": [os.path.join("src", failing_module_dir, "target", "all-dependencies.jar")],
                "count_neg": 0,
                "count_pos": 0,
                "test_timeout": 5,
                "build_system": row['build_system'].strip().lower(),
                "java_version": int(row['compliance_level'].strip())
            }
        )



root_project = os.getcwd()
os.chdir(root_project)
x = open("meta-data.json", "w")
x.write(json.dumps(result, indent=4))
x.close()
