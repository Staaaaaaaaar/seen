#!/usr/bin/env python3
"""Create project-local IDE settings without overwriting existing preferences."""
from pathlib import Path
from urllib.parse import urlsplit
import os,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
java=Path(os.environ.get('SEEN_JAVA_HOME', '/usr/lib/jvm/java-17-openjdk-amd64'))
if not (java/'bin/java').is_file(): raise SystemExit(f'JDK not found: {java}')
def property_path(path):
    return str(path).replace('\\', '\\\\').replace(' ', '\\ ')
tools=root/'.tools'
profile=tools/'studio-profile'
for name in ['config/options','system','plugins','log']:
    (profile/name).mkdir(parents=True,exist_ok=True)
props=tools/'studio.properties'
if not props.exists():
    props.write_text('\n'.join(f'idea.{key}.path={profile/value}' for key,value in [
        ('config','config'),('system','system'),('plugins','plugins'),('log','log')])+'\n')
(root/'.gradle').mkdir(exist_ok=True)
localjava=root/'.gradle/config.properties'
if not localjava.exists(): localjava.write_text('java.home='+property_path(java)+'\n')
(root/'.idea').mkdir(exist_ok=True)
gradle=root/'.idea/gradle.xml'
if not gradle.exists():
    gradle.write_text('''<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="GradleSettings">
    <option name="linkedExternalProjectsSettings">
      <GradleProjectSettings>
        <option name="externalProjectPath" value="$PROJECT_DIR$" />
        <option name="gradleJvm" value="#GRADLE_LOCAL_JAVA_HOME" />
        <option name="modules"><set><option value="$PROJECT_DIR$" /><option value="$PROJECT_DIR$/app" /></set></option>
      </GradleProjectSettings>
    </option>
  </component>
</project>
''')
# Keep this project's default SDK aligned across the IDE and CLI installer.
sdk=Path(os.environ.get('SEEN_ANDROID_SDK', str(tools/'android-sdk'))); sdk.mkdir(parents=True,exist_ok=True)
local=root/'local.properties'
if not local.exists(): local.write_text('sdk.dir='+property_path(sdk)+'\n')
gradlehome=tools/'gradle-home'; gradlehome.mkdir(exist_ok=True)
proxy=urlsplit(os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy') or '')
if proxy.scheme=='http' and proxy.hostname and proxy.port and not proxy.username and not proxy.password:
    p=profile/'config/options/proxy.settings.xml'
    if not p.exists():
        a=ET.Element('application');c=ET.SubElement(a,'component',name='HttpConfigurable')
        for name,value in [('USE_HTTP_PROXY','true'),('USE_PROXY_PAC','false'),('PROXY_HOST',proxy.hostname),('PROXY_PORT',str(proxy.port))]:
            ET.SubElement(c,'option',name=name,value=value)
        ET.indent(a); ET.ElementTree(a).write(p,encoding='UTF-8',xml_declaration=True)
    p=gradlehome/'gradle.properties'
    if not p.exists():
        p.write_text('\n'.join(f'systemProp.{protocol}.proxy{part}={value}' for protocol in ['http','https'] for part,value in [('Host',proxy.hostname),('Port',str(proxy.port))])+'\nsystemProp.http.nonProxyHosts=localhost|127.*|[::1]\n')
    print('Inherited proxy considered for missing settings; existing preferences preserved.')
print('Seen local IDE settings ready; existing settings preserved.')
