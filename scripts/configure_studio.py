#!/usr/bin/env python3
"""Align selected SDK/JDK paths; preserve unrelated IDE preferences."""
from pathlib import Path
import os
import re
root=Path(__file__).resolve().parents[1]
java=Path(os.environ['JAVA_HOME'])
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
def set_property(path, key, value):
    lines = path.read_text().splitlines() if path.exists() else []
    lines = [line for line in lines if not re.match(r'\s*' + re.escape(key) + r'\s*[=:]', line)]
    path.write_text('\n'.join(lines + [key + '=' + property_path(value)]) + '\n')
set_property(localjava, 'java.home', java)
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
sdk=Path(os.environ['ANDROID_HOME'])
local=root/'local.properties'
set_property(local, 'sdk.dir', sdk)
print('Seen SDK/JDK paths aligned; existing IDE preferences preserved.')
