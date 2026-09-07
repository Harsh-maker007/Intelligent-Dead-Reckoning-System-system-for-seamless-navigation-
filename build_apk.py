import os
import subprocess
import shutil
import zipfile
import time

def build_android_apk():
    print("=== Building Installable Android APK using Android SDK Build-Tools ===")
    
    sdk_dir = r"C:\Users\harsh\AppData\Local\Android\Sdk"
    build_tools = os.path.join(sdk_dir, "build-tools", "36.0.0")
    android_jar = os.path.join(sdk_dir, "platforms", "android-37.0", "android.jar")
    
    aapt2 = os.path.join(build_tools, "aapt2.exe")
    d8_jar = os.path.join(build_tools, "lib", "d8.jar")
    apksigner_jar = os.path.join(build_tools, "lib", "apksigner.jar")
    zipalign = os.path.join(build_tools, "zipalign.exe")
    
    java_home = r"C:\Program Files\Java\jdk-26.0.2"
    java = os.path.join(java_home, "bin", "java.exe")
    javac = os.path.join(java_home, "bin", "javac.exe")
    keytool = os.path.join(java_home, "bin", "keytool.exe")

    project_dir = r"c:\Users\harsh\OneDrive\Desktop\SIH Project\android_app"
    build_dir = os.path.join(project_dir, f"build_out_{int(time.time())}")
    os.makedirs(build_dir, exist_ok=True)

    res_dir = os.path.join(project_dir, "app", "src", "main", "res")
    manifest = os.path.join(project_dir, "app", "src", "main", "AndroidManifest.xml")
    assets_dir = os.path.join(project_dir, "app", "src", "main", "assets")
    src_dir = os.path.join(project_dir, "app", "src", "main", "java")

    compiled_res_zip = os.path.join(build_dir, "compiled_res.zip")
    gen_dir = os.path.join(build_dir, "gen")
    os.makedirs(gen_dir, exist_ok=True)

    # 1. Compile Resources with AAPT2
    print("Step 1: Compiling resources with AAPT2...")
    cmd_aapt2_compile = [aapt2, "compile", "--dir", res_dir, "-o", compiled_res_zip]
    subprocess.check_call(cmd_aapt2_compile)

    # 2. Link Resources to generate R.java and initial APK
    print("Step 2: Linking resources with AAPT2...")
    base_apk = os.path.join(build_dir, "base.apk")
    cmd_aapt2_link = [
        aapt2, "link",
        "-I", android_jar,
        "--manifest", manifest,
        "-A", assets_dir,
        "-o", base_apk,
        "--java", gen_dir,
        compiled_res_zip
    ]
    subprocess.check_call(cmd_aapt2_link)

    # 3. Collect Java Source Files
    print("Step 3: Compiling Java source files with javac...")
    java_files = []
    for root, dirs, files in os.walk(src_dir):
        for f in files:
            if f.endswith(".java"):
                java_files.append(os.path.join(root, f))
    for root, dirs, files in os.walk(gen_dir):
        for f in files:
            if f.endswith(".java"):
                java_files.append(os.path.join(root, f))

    bin_dir = os.path.join(build_dir, "bin")
    os.makedirs(bin_dir, exist_ok=True)

    cmd_javac = [javac, "-source", "17", "-target", "17", "-cp", android_jar, "-d", bin_dir] + java_files
    subprocess.check_call(cmd_javac)

    # 4. Dexing with D8 jar directly
    print("Step 4: Converting bytecode to DEX with D8...")
    class_files = []
    for root, dirs, files in os.walk(bin_dir):
        for f in files:
            if f.endswith(".class"):
                class_files.append(os.path.join(root, f))

    cmd_d8 = [java, "-cp", d8_jar, "com.android.tools.r8.D8", "--output", build_dir, "--lib", android_jar] + class_files
    subprocess.check_call(cmd_d8)

    classes_dex = os.path.join(build_dir, "classes.dex")
    if not os.path.exists(classes_dex):
        raise FileNotFoundError(f"classes.dex not generated in {build_dir}")

    # 5. Package classes.dex into unaligned APK
    print("Step 5: Packaging classes.dex into APK...")
    unaligned_apk = os.path.join(build_dir, "unaligned.apk")
    shutil.copyfile(base_apk, unaligned_apk)

    with zipfile.ZipFile(unaligned_apk, "a") as z:
        z.write(classes_dex, "classes.dex")

    # 6. Zipalign APK
    print("Step 6: Zip-aligning APK...")
    aligned_apk = os.path.join(build_dir, "aligned.apk")
    cmd_zipalign = [zipalign, "-v", "-p", "4", unaligned_apk, aligned_apk]
    subprocess.check_call(cmd_zipalign)

    # 7. Create Keystore & Sign APK with apksigner jar
    print("Step 7: Creating Debug Keystore and Signing APK...")
    keystore = os.path.join(build_dir, "debug.keystore")
    cmd_keytool = [
        keytool, "-genkeypair", "-v",
        "-keystore", keystore,
        "-storepass", "android",
        "-alias", "androiddebugkey",
        "-keypass", "android",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-dname", "CN=Android Debug,O=Android,C=US"
    ]
    subprocess.check_call(cmd_keytool)

    final_apk = os.path.join(r"c:\Users\harsh\OneDrive\Desktop\SIH Project", "Intelligent_Dead_Reckoning_SIH2026.apk")
    cmd_sign = [
        java, "-jar", apksigner_jar, "sign",
        "--v4-signing-enabled", "false",
        "--ks", keystore,
        "--ks-pass", "pass:android",
        "--key-pass", "pass:android",
        "--out", final_apk,
        aligned_apk
    ]
    subprocess.check_call(cmd_sign)

    print(f"\n========================================================")
    print(f"SUCCESS! Installable Android APK generated at:")
    print(f"--> {os.path.abspath(final_apk)}")
    print(f"========================================================")

if __name__ == "__main__":
    build_android_apk()
