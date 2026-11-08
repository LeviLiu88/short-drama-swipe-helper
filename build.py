"""Build with the Android SDK and JDK. No downloaded Python dependencies."""
from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '/opt/homebrew/share/android-commandlinetools')
java_home = os.environ.get('JAVA_HOME')
java = Path(java_home) / 'bin' if java_home else Path(shutil.which('javac') or '/opt/homebrew/opt/openjdk/bin/javac').parent
if not java_home and Path('/opt/homebrew/opt/openjdk/bin/javac').exists():
    java = Path('/opt/homebrew/opt/openjdk/bin')
android = sdk / 'platforms' / os.environ.get('ANDROID_PLATFORM', 'android-37.0') / 'android.jar'
tools = sdk / 'build-tools' / os.environ.get('ANDROID_BUILD_TOOLS', '36.0.0')
for required in [android, tools/'aapt2', tools/'d8', tools/'apksigner', java/'javac']:
    if not required.exists():
        raise SystemExit(f'Missing {required}. Set ANDROID_HOME, JAVA_HOME, ANDROID_PLATFORM and ANDROID_BUILD_TOOLS as needed.')
env = {**os.environ, 'JAVA_HOME': str(java.parent)}
build = root / 'build'
# A clean output prevents removed Java classes from entering subsequent APKs.
if build.exists():
    shutil.rmtree(build)
for directory in ['classes', 'dex', 'tests']:
    (build/directory).mkdir(parents=True, exist_ok=True)

def run(args):
    subprocess.run([str(a) for a in args], env=env, check=True)

source = root / 'src/local/hongguo/adhelper'
run([java/'javac', '--release', '8', '-d', build/'tests', source/'CountdownDetector.java', source/'AdSignals.java', *sorted((root/'tests').glob('*.java'))])
run([java/'java', '-ea', '-cp', build/'tests', 'CountdownDetectorTest'])
run([java/'java', '-ea', '-cp', build/'tests', 'AdSignalsTest'])
manifest = ET.parse(root/'AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
assert not manifest.findall('uses-permission'), 'Unexpected application permission'
assert manifest.find('application/service').get(ns+'permission') == 'android.permission.BIND_ACCESSIBILITY_SERVICE'
config = ET.parse(root/'res/xml/helper_service.xml').getroot()
assert config.get(ns+'isAccessibilityTool') == 'false'
assert config.get(ns+'packageNames') == 'com.phoenix.read,local.hongguo.adhelper'
assert manifest.find('application/activity[@'+ns+'name=".TestActivity"]').get(ns+'exported') == 'false'
run([java/'javac', '--release', '8', '-classpath', android, '-d', build/'classes', *sorted(source.glob('*.java'))])
run([java/'jar', 'cf', build/'classes.jar', '-C', build/'classes', '.'])
run([tools/'d8', '--min-api', '28', '--lib', android, '--output', build/'dex', build/'classes.jar'])
run([tools/'aapt2', 'compile', '--dir', root/'res', '-o', build/'resources.zip'])
run([tools/'aapt2', 'link', '-o', build/'unsigned.apk', '--manifest', root/'AndroidManifest.xml', '-I', android, build/'resources.zip'])
with zipfile.ZipFile(build/'unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as archive:
    archive.write(build/'dex/classes.dex', 'classes.dex')
run([tools/'zipalign', '-f', '4', build/'unsigned.apk', build/'aligned.apk'])

keystore = Path(os.environ.get('SIGNING_KEYSTORE', str(root/'.local/debug.p12'))).expanduser()
alias = os.environ.get('SIGNING_ALIAS', 'androiddebugkey')
password_file = os.environ.get('SIGNING_PASSWORD_FILE')
if not keystore.exists():
    if 'SIGNING_KEYSTORE' in os.environ:
        raise SystemExit('SIGNING_KEYSTORE does not exist')
    keystore.parent.mkdir(parents=True, exist_ok=True)
    run([java/'keytool', '-genkeypair', '-keystore', keystore, '-storetype', 'PKCS12', '-alias', alias,
         '-storepass', 'android', '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '3072',
         '-validity', '3650', '-dname', 'CN=Local Development'])
    keystore.chmod(0o600)
password = 'file:' + str(Path(password_file).expanduser()) if password_file else 'pass:android'
version = manifest.get(ns+'versionName')
apk = root/f'短剧滑动助手-{version}.apk'
run([tools/'apksigner', 'sign', '--ks', keystore, '--ks-key-alias', alias, '--ks-pass', password, '--out', apk, build/'aligned.apk'])
run([tools/'apksigner', 'verify', '--print-certs', apk])
(root/'SHA256.txt').write_text(hashlib.sha256(apk.read_bytes()).hexdigest() + '  ' + apk.name + '\n')
print('Build, regression checks, signature and manifest checks passed.')
