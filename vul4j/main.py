#!env python3

import argparse
import csv
import json
import logging
import os
import subprocess
import sys
from os.path import expanduser
from shutil import copytree, ignore_patterns
from xml.etree.ElementTree import parse

from unidiff import PatchSet

JAVA7_HOME = os.environ.get("JAVA7_HOME", expanduser("/Library/Java/JavaVirtualMachines/jdk1.7.0_80.jdk/Contents/Home"))
JAVA8_HOME = os.environ.get("JAVA8_HOME",
                            expanduser("/Library/Java/JavaVirtualMachines/jdk1.8.0_281.jdk/Contents/Home"))
JAVA_ARGS = os.environ.get("JAVA_ARGS", "-Xmx4g -Xms1g -XX:MaxPermSize=512m")
MVN_OPTS = os.environ.get("MVN_OPTS", "-Xmx4g -Xms1g -XX:MaxPermSize=512m")

DATASET_PATH = "/Users/cuong/PycharmProjects/vul4j/dataset/vul4j_dataset.csv"
BENCHMARK_PATH = os.environ.get("BENCHMARK_PATH", expanduser("/Users/cuong/Research/securethemall/benchmarks/sapkb"))
GZOLTAR_RUNNER = os.environ.get("GZOLTAR_RUNNER", expanduser("/Users/cuong/PycharmProjects/vul4j/gzoltar_runner"))
OUTPUT_FOLDER_NAME = "VUL4J"

ENABLE_EXECUTING_LOGS = os.environ.get("ENABLE_EXECUTING_LOGS", "1")

FNULL = open(os.devnull, 'w')
root = logging.getLogger()
root.setLevel(logging.DEBUG)

handler = logging.StreamHandler(sys.stdout)
handler.setLevel(logging.DEBUG)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
handler.setFormatter(formatter)
root.addHandler(handler)


class Vul4J:
    def __init__(self):
        self.vulnerabilities = []
        self.load_vulnerabilities()

    def load_vulnerabilities(self):
        # get all the vulnerabilities of the benchmark
        with open(os.path.join(DATASET_PATH)) as f:
            reader = csv.DictReader(f, delimiter=',', )
            for row in reader:
                vul_id = row['vul_id'].strip()
                cve_id = row['cve_id'].strip()
                repo_slug = row['repo_slug'].strip().replace("/", "_")
                build_system = row['build_system'].strip()
                compliance_level = int(row['compliance_level'].strip())
                compile_cmd = row['compile_cmd'].strip()
                test_all_cmd = row['test_all_cmd'].strip()
                cmd_options = row['cmd_options'].strip()
                failing_module = row['failing_module'].strip()
                src_classes_dir = row['src_classes'].strip()
                test_classes_dir = row['test_classes'].strip()
                human_patch_url = row['human_patch'].strip()
                fixing_commit = human_patch_url[human_patch_url.rfind('/') + 1:]

                if failing_module != "root" and failing_module != "":
                    src_classes_dir = failing_module + '/' + src_classes_dir
                    test_classes_dir = failing_module + '/' + test_classes_dir

                self.vulnerabilities += [{
                    "vul_id": vul_id,
                    "cve_id": cve_id,
                    "project": repo_slug,
                    "project_url": "https://github.com/%s" % (repo_slug.replace("_", "/")),
                    "build_system": build_system,
                    "compliance_level": compliance_level,
                    "compile_cmd": compile_cmd,
                    "test_all_cmd": test_all_cmd,
                    "cmd_options": cmd_options,
                    "failing_module": failing_module,
                    "src_classes_dir": src_classes_dir,
                    "test_classes_dir": test_classes_dir,
                    "human_patch_url": human_patch_url,
                    "human_patch": [],
                    "fixing_commit_hash": fixing_commit,
                }]
        return self.vulnerabilities

    def get_vulnerability(self, vul_id):
        for vul in self.vulnerabilities:
            if vul_id.lower() == vul['vul_id'].lower():
                return vul
        return None

    @staticmethod
    def get_patch(vul):
        cmd = "cd " + BENCHMARK_PATH + "; git diff %s %s~1" % (vul['fixing_commit_hash'], vul['fixing_commit_hash'])
        diff = subprocess.check_output(cmd, shell=True)
        patch = PatchSet(diff.decode('utf-8'))

        changed_java_source_files = []
        for a_file in patch.added_files:
            file_path = a_file.path
            if file_path.endswith('.java') and ('test/' not in file_path and 'tests/' not in file_path):
                changed_java_source_files.append(file_path)

        for m_file in patch.modified_files:
            file_path = m_file.path
            if file_path.endswith('.java') and ('test/' not in file_path and 'tests/' not in file_path):
                changed_java_source_files.append(file_path)

        patch_data = []
        for file in changed_java_source_files:
            cmd = "cd " + BENCHMARK_PATH + "; git show %s:%s" % (vul['fixing_commit_hash'], file)
            content = subprocess.check_output(cmd, shell=True).decode('utf-8')
            patch_data.append({'file_path': file, 'content': content})
        return patch_data

    def checkout(self, vul_id, output_dir):
        vul = self.get_vulnerability(vul_id)
        if os.path.exists(output_dir):
            logging.error("Directory '%s' has already existed!" % output_dir)
            exit(1)

        cmd = "cd %s; git reset .; git checkout -- .; git clean -x -d --force; git checkout -f %s" % (
            BENCHMARK_PATH, vul["vul_id"])
        ret = subprocess.call(cmd, shell=True, stdout=FNULL, stderr=subprocess.STDOUT)
        if ret != 0:
            exit(1)

        # extract the original patch
        # some vulns with customized-patch will fail here
        # patch_data = self.get_patch(vul)
        # vul['human_patch'] = patch_data

        # copy to working directory
        copytree(BENCHMARK_PATH, output_dir, ignore=ignore_patterns('.git'))
        os.makedirs(os.path.join(output_dir, OUTPUT_FOLDER_NAME))
        with open(os.path.join(output_dir, OUTPUT_FOLDER_NAME, "vulnerability_info.json"), "w", encoding='utf-8') as f:
            f.write(json.dumps(vul, indent=2))

        print("Id: %s" % 123)
        print("Working directory: %s" % output_dir)
        return 0

    @staticmethod
    def read_vulnerability_from_output_dir(output_dir):
        info_file = os.path.join(output_dir, OUTPUT_FOLDER_NAME, "vulnerability_info.json")
        try:
            vul = json.load(open(info_file))
            return vul
        except IOError:
            logging.error("Not found the info file of vulnerability: '%s'" % info_file)
            exit(1)

    def compile(self, output_dir):
        vul = self.read_vulnerability_from_output_dir(output_dir)

        java_home = JAVA7_HOME if vul['compliance_level'] <= 7 else JAVA8_HOME

        cmd = """cd %s;
export JAVA_HOME="%s";
export _JAVA_OPTIONS=-Djdk.net.URLClassPath.disableClassPathURLCheck=true;
export MAVEN_OPTS="%s";
%s;""" % (output_dir, java_home, MVN_OPTS, vul['compile_cmd'])

        cmd_options = vul['cmd_options']
        if cmd_options:
            cmd = cmd[:-1]  # remove comma
            cmd += " " + cmd_options + ';'

        log_path = os.path.join(output_dir, OUTPUT_FOLDER_NAME, "compile.log")
        stdout = open(log_path, "w", encoding="utf-8") if ENABLE_EXECUTING_LOGS == "1" else FNULL
        ret = subprocess.call(cmd, shell=True, stdout=stdout, stderr=subprocess.STDOUT)
        with (open(os.path.join(output_dir, OUTPUT_FOLDER_NAME, "compile_result.txt"), "w")) as f:
            f.write("1" if ret == 0 else "0")
        return ret

    def test(self, output_dir):
        vul = self.read_vulnerability_from_output_dir(output_dir)

        java_home = JAVA7_HOME if vul['compliance_level'] <= 7 else JAVA8_HOME

        cmd = """cd %s;
export JAVA_HOME="%s";
export _JAVA_OPTIONS=-Djdk.net.URLClassPath.disableClassPathURLCheck=true;
export MAVEN_OPTS="%s";
%s;""" % (output_dir, java_home, MVN_OPTS, vul['test_all_cmd'])

        cmd_options = vul['cmd_options']
        if cmd_options:
            cmd = cmd[:-1]  # remove comma
            cmd += " " + cmd_options + ';'

        log_path = os.path.join(output_dir, OUTPUT_FOLDER_NAME, "testing.log")
        stdout = open(log_path, "w", encoding="utf-8") if ENABLE_EXECUTING_LOGS == "1" else FNULL
        subprocess.call(cmd, shell=True, stdout=stdout, stderr=subprocess.STDOUT)

        test_results = self.read_test_results_maven(vul, output_dir) \
            if vul['build_system'] == "Maven" \
            else self.read_test_results_gradle(vul, output_dir)

        json_str = json.dumps(test_results, indent=2)
        print(json_str)
        with (open(os.path.join(output_dir, OUTPUT_FOLDER_NAME, "testing_results.json"), "w")) as f:
            f.write(json_str)
        return 0

    def classpath(self, output_dir):
        print(self.get_classpath(output_dir))
        return 0

    def get_classpath(self, output_dir):
        vul = self.read_vulnerability_from_output_dir(output_dir)

        hardcode_classpath_cmds = {
            # 'KB-654': 'pwd',
            'KB-654': './gradlew :spring-webmvc:copyClasspath;cat spring-webmvc/classpath.info',
            'KB-258': './gradlew :spring-web:copyClasspath;cat spring-web/classpath.info',
            'KB-189': './gradlew :spring-security-oauth2-jose:copyClasspath;cat oauth2/oauth2-jose/classpath.info',
            'KB-578': './gradlew :cloudfoundry-identity-server:copyClasspath;cat server/classpath.info',
        }

        cp_cmd = ""
        if vul['vul_id'] in hardcode_classpath_cmds.keys():
            cp_cmd = hardcode_classpath_cmds[vul['vul_id']]

        elif vul['build_system'] == "Maven":
            failing_module = vul['failing_module']
            if failing_module != "root" and failing_module != "":
                cp_cmd = "mvn dependency:build-classpath -Dmdep.outputFile='classpath.info' -pl %s; cat %s/classpath.info" \
                         % (failing_module, failing_module)
            else:
                cp_cmd = "mvn dependency:build-classpath -Dmdep.outputFile='classpath.info'; cat classpath.info"

        else:
            print("Not support for %s" % vul['vul_id'])
            exit(1)

        java_home = JAVA7_HOME if vul['compliance_level'] <= 7 else JAVA8_HOME

        cmd = """cd %s;
        export JAVA_HOME="%s";
        export _JAVA_OPTIONS=-Djdk.net.URLClassPath.disableClassPathURLCheck=true;
        export MAVEN_OPTS="%s";
        %s;""" % (output_dir, java_home, MVN_OPTS, cp_cmd.split(';')[0])

        subprocess.call(cmd, shell=True, stdout=FNULL, stderr=subprocess.STDOUT)

        classpath = subprocess.check_output(
            "cd %s;%s;" % (output_dir, cp_cmd.split(';')[1]), shell=True)
        return classpath

    def fault_localization(self, output_dir):
        vul = self.read_vulnerability_from_output_dir(output_dir)
        java_home = JAVA7_HOME if vul['compliance_level'] <= 7 else JAVA8_HOME

        subprocess.call("cp -r %s/* %s" % (GZOLTAR_RUNNER, output_dir),
                        shell=True, stdout=FNULL, stderr=subprocess.STDOUT)

        src_classes_dir = vul['src_classes_dir']
        test_classes_dir = vul['test_classes_dir']
        classpath = self.get_classpath(output_dir).decode('utf-8')

        result_dir = os.path.abspath(os.path.join("fl", vul['vul_id']))

        fl_cmd = 'bash run_fl.sh -o="%s" -s="%s" -t="%s" -c="%s"' \
                 % (result_dir, src_classes_dir, test_classes_dir, classpath)

        cmd = """cd %s;
export JAVA_HOME="%s"
export PATH="%s/bin:$PATH";
%s;""" % (os.path.abspath(output_dir), java_home, java_home, fl_cmd)

        log_path = os.path.join("logs", "fl", vul['vul_id'] + '.log')
        os.makedirs(os.path.dirname(log_path), exist_ok=True)
        stdout = open(log_path, "w", encoding="utf-8") if ENABLE_EXECUTING_LOGS == "1" else FNULL
        stdout.write(cmd + "\n")
        stdout.flush()
        subprocess.call(cmd, shell=True, stdout=stdout, stderr=subprocess.STDOUT)

        # classpath = subprocess.check_output(
        #     "cd %s;%s;" % (output_dir, cp_cmd.split(';')[1]), shell=True)
        # print(classpath)
        return 0

    '''
    modify from https://github.com/program-repair/RepairThemAll/blob/master/script/info_json_file.py
    '''

    @staticmethod
    def read_test_results_maven(vul, project_dir):
        surefire_report_files = []
        for r, dirs, files in os.walk(project_dir):
            for file in files:
                filePath = os.path.join(r, file)
                if ("target/surefire-reports" in filePath or "target/failsafe-reports" in filePath) and file.endswith(
                        '.xml') and file.startswith('TEST-'):
                    surefire_report_files.append(filePath)

        failing_tests_count = 0
        error_tests_count = 0
        passing_tests_count = 0
        skipping_tests_count = 0

        passingTestCases = set()
        skippingTestCases = set()

        failures = []

        for report_file in surefire_report_files:
            with open(report_file, 'r') as file:
                xml_tree = parse(file)
                testsuite_class_name = xml_tree.getroot().attrib['name']
                test_cases = xml_tree.findall('testcase')
                for test_case in test_cases:
                    failure_list = {}
                    class_name = test_case.attrib[
                        'classname'] if 'classname' in test_case.attrib else testsuite_class_name
                    method_name = test_case.attrib['name']
                    failure_list['test_class'] = class_name
                    failure_list['test_method'] = method_name

                    failure = test_case.findall('failure')
                    if len(failure) > 0:
                        failing_tests_count += 1
                        failure_list['failure_name'] = failure[0].attrib['type']
                        if 'message' in failure[0].attrib:
                            failure_list['detail'] = failure[0].attrib['message']
                        failure_list['is_error'] = False
                        failures.append(failure_list)
                    else:
                        error = test_case.findall('error')
                        if len(error) > 0:
                            error_tests_count += 1
                            failure_list['failure_name'] = error[0].attrib['type']
                            if 'message' in error[0].attrib:
                                failure_list['detail'] = error[0].attrib['message']
                            failure_list['is_error'] = True
                            failures.append(failure_list)
                        else:
                            skipTags = test_case.findall("skipped")
                            if len(skipTags) > 0:
                                skipping_tests_count += 1
                                skippingTestCases.add(class_name + '#' + method_name)
                            else:
                                passing_tests_count += 1
                                passingTestCases.add(class_name + '#' + method_name)

        repository = {'name': vul['project'], 'url': vul['project_url'], 'human_patch_url': vul['human_patch_url']}
        overall_metrics = {'number_running': passing_tests_count + error_tests_count + failing_tests_count,
                           'number_passing': passing_tests_count, 'number_error': error_tests_count,
                           'number_failing': failing_tests_count, 'number_skipping': skipping_tests_count}
        tests = {'overall_metrics': overall_metrics, 'failures': failures, 'passing_tests': list(passingTestCases),
                 'skipping_tests': list(skippingTestCases)}

        json_data = {'vul_id': vul['vul_id'], 'cve_id': vul['cve_id'], 'repository': repository, 'tests': tests}
        return json_data

    @staticmethod
    def read_test_results_gradle(vul, project_dir):
        surefire_report_files = []
        for r, dirs, files in os.walk(project_dir):
            for file in files:
                filePath = os.path.join(r, file)
                if "build/test-results" in filePath and file.endswith('.xml') and file.startswith('TEST-'):
                    surefire_report_files.append(filePath)

        failing_tests_count = 0
        error_tests_count = 0
        passing_tests_count = 0
        skipping_tests_count = 0
        failures = []

        passingTestCases = set()
        skippingTestCases = set()

        for report_file in surefire_report_files:
            with open(report_file, 'r') as file:
                xml_tree = parse(file)
                testsuite_class_name = xml_tree.getroot().attrib['name']
                test_cases = xml_tree.findall('testcase')
                for test_case in test_cases:
                    failure_list = {}
                    class_name = test_case.attrib[
                        'classname'] if 'classname' in test_case.attrib else testsuite_class_name
                    method_name = test_case.attrib['name']
                    failure_list['test_class'] = class_name
                    failure_list['test_method'] = method_name

                    failure = test_case.findall('failure')
                    if len(failure) > 0:
                        failing_tests_count += 1
                        failure_list['failure_name'] = failure[0].attrib['type']
                        if 'message' in failure[0].attrib:
                            failure_list['detail'] = failure[0].attrib['message']
                        failure_list['is_error'] = False
                        failures.append(failure_list)
                    else:
                        error = test_case.findall('error')
                        if len(error) > 0:
                            error_tests_count += 1
                            failure_list['failure_name'] = error[0].attrib['type']
                            if 'message' in error[0].attrib:
                                failure_list['detail'] = error[0].attrib['message']
                            failure_list['is_error'] = True
                            failures.append(failure_list)
                        else:
                            skipTags = test_case.findall("skipped")
                            if len(skipTags) > 0:
                                skipping_tests_count += 1
                                skippingTestCases.add(class_name + '#' + method_name)
                            else:
                                passing_tests_count += 1
                                passingTestCases.add(class_name + '#' + method_name)

        repository = {'name': vul['project'], 'url': vul['project_url'], 'human_patch': vul['human_patch']}
        overall_metrics = {'number_running': passing_tests_count + error_tests_count + failing_tests_count,
                           'number_passing': passing_tests_count, 'number_error': error_tests_count,
                           'number_failing': failing_tests_count, 'number_skipping': skipping_tests_count}
        tests = {'overall_metrics': overall_metrics, 'failures': failures, 'passing_tests': list(passingTestCases),
                 'skipping_tests': list(skippingTestCases)}

        json_data = {'vul_id': vul['vul_id'], 'cve_id': vul['cve_id'], 'repository': repository, 'tests': tests}
        return json_data


def main_checkout(args):
    vul4j = Vul4J()
    ret = vul4j.checkout(args.id, args.outdir)
    if ret != 0:
        print("Checkout failed!")
    exit(ret)


def main_compile(args):
    vul4j = Vul4J()
    ret = vul4j.compile(args.outdir)
    if ret != 0:
        print("Compile failed!")
    exit(ret)


def main_test(args):
    vul4j = Vul4J()
    ret = vul4j.test(args.outdir)
    exit(ret)


def main_classpath(args):
    vul4j = Vul4J()
    ret = vul4j.classpath(args.outdir)
    exit(ret)


def main_fl(args):
    vul4j = Vul4J()
    ret = vul4j.fault_localization(args.outdir)
    exit(ret)


def main(args=None):
    if args is None:
        args = sys.argv[1:]

    parser = argparse.ArgumentParser(prog="vul4j", description="A Benchmark of Java vulnerabilities.")

    sub_parsers = parser.add_subparsers(help="Checkout a vulnerability in the benchmark.")

    checkout_parser = sub_parsers.add_parser('checkout')
    checkout_parser.set_defaults(func=main_checkout)
    checkout_parser.add_argument("-i", "--id", help="Vulnerability Id.", required=True)
    checkout_parser.add_argument("-d", "--outdir", help="The destination directory.", required=True)

    compile_parser = sub_parsers.add_parser('compile')
    compile_parser.set_defaults(func=main_compile)
    compile_parser.add_argument("-i", "--id", help="Vulnerability Id.", required=False)
    compile_parser.add_argument("-d", "--outdir", help="The directory to which the vulnerability was checked out.",
                                required=True)

    test_parser = sub_parsers.add_parser('test')
    test_parser.set_defaults(func=main_test)
    test_parser.add_argument("-i", "--id", help="Vulnerability Id.", required=False)
    test_parser.add_argument("-d", "--outdir", help="The directory to which the vulnerability was checked out.",
                             required=True)

    cp_parser = sub_parsers.add_parser('classpath')
    cp_parser.set_defaults(func=main_classpath)
    cp_parser.add_argument("-i", "--id", help="Vulnerability Id.", required=False)
    cp_parser.add_argument("-d", "--outdir", help="The directory to which the vulnerability was checked out.",
                           required=True)

    fl_parser = sub_parsers.add_parser('fl')
    fl_parser.set_defaults(func=main_fl)
    fl_parser.add_argument("-i", "--id", help="Vulnerability Id.", required=False)
    fl_parser.add_argument("-d", "--outdir", help="The directory to which the vulnerability was checked out.",
                           required=True)

    options = parser.parse_args(args)
    if not hasattr(options, 'func'):
        parser.print_help()
        exit(1)
    options.func(options)
    return options


if __name__ == "__main__":
    main()
