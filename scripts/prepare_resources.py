import os
import shutil
import subprocess
import glob

def find_jlink():
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        cand = os.path.join(java_home, "bin", "jlink.exe" if os.name == "nt" else "jlink")
        if os.path.isfile(cand):
            return cand
    which = shutil.which("jlink")
    if which:
        return which
    if os.name == "nt":
        for pat in [r"C:\Program Files\Java\jdk-*\bin\jlink.exe", r"C:\Program Files\Eclipse Adoptium\jdk-*\bin\jlink.exe"]:
            matches = sorted(glob.glob(pat), reverse=True)
            if matches:
                return matches[0]
    return "jlink"

def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(root)
    
    res_dir = os.path.join(root, "src-tauri", "resources")
    if os.path.exists(res_dir):
        shutil.rmtree(res_dir, ignore_errors=True)
    
    jre_dir = os.path.join(res_dir, "jre")
    backend_dir = os.path.join(res_dir, "backend")
    defaults_dir = os.path.join(res_dir, "defaults")
    
    os.makedirs(backend_dir, exist_ok=True)
    os.makedirs(os.path.join(defaults_dir, "config"), exist_ok=True)
    os.makedirs(os.path.join(defaults_dir, "sample-data"), exist_ok=True)
    
    jlink_exe = find_jlink()
    print(f"1. Building lightweight private JRE with jlink ({jlink_exe})...")
    jlink_cmd = [
        jlink_exe,
        "--add-modules", "java.base,java.desktop,java.logging,java.management,java.naming,java.xml,java.sql,jdk.unsupported,jdk.crypto.ec,jdk.httpserver,jdk.zipfs",
        "--strip-debug",
        "--no-man-pages",
        "--no-header-files",
        "--output", jre_dir
    ]
    subprocess.run(jlink_cmd, check=True)
    print("   [OK] Private JRE generated successfully.")
    
    print("2. Staging backend JAR and libraries...")
    jar_src = os.path.join(root, "target", "timetable-generator-1.0.0.jar")
    lib_src = os.path.join(root, "target", "lib")
    
    shutil.copy2(jar_src, backend_dir)
    shutil.copytree(lib_src, os.path.join(backend_dir, "lib"))
    
    # Prune non-windows platform jars to minimize bundle size
    for pat in ["ortools-linux-*.jar", "ortools-darwin-*.jar"]:
        for match in glob.glob(os.path.join(backend_dir, "lib", pat)):
            os.remove(match)
            print(f"   Pruned non-Windows library: {os.path.basename(match)}")
            
    print("3. Staging default configuration and sample data...")
    for item in os.listdir("config"):
        s = os.path.join("config", item)
        d = os.path.join(defaults_dir, "config", item)
        if os.path.isfile(s):
            shutil.copy2(s, d)
        elif os.path.isdir(s):
            shutil.copytree(s, d, dirs_exist_ok=True)
            
    for item in os.listdir("sample-data"):
        s = os.path.join("sample-data", item)
        d = os.path.join(defaults_dir, "sample-data", item)
        if os.path.isfile(s):
            shutil.copy2(s, d)
        elif os.path.isdir(s):
            shutil.copytree(s, d, dirs_exist_ok=True)

    print("[OK] Resource preparation completed.")

if __name__ == "__main__":
    main()
