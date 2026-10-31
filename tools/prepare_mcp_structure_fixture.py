#!/usr/bin/env python3
"""Prepare an owned, disposable IDEA project and its isolated sandbox SDK entry."""
import argparse
import os
from pathlib import Path
import xml.etree.ElementTree as ET

SDK = "WicketLens MCP fixture Java"


def write_xml(path, root):
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(root)
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


def prepare(project, config, java_home, libraries):
    marker = project / ".wicketlens-mcp-fixture"
    if project.exists() and any(project.iterdir()) and not marker.exists():
        raise ValueError("Refusing to overwrite a nonempty project without the owned fixture marker.")
    project.mkdir(parents=True, exist_ok=True)
    marker.write_text("Disposable WicketLens MCP structure fixture\n")
    if not (java_home / "bin/java").is_file():
        raise ValueError("A real JDK installation is required.")
    sdk_path = config / "options/jdk.table.xml"
    sdk_root = ET.parse(sdk_path).getroot() if sdk_path.exists() else ET.Element("application")
    table = sdk_root.find("component[@name='ProjectJdkTable']")
    if table is None:
        table = ET.SubElement(sdk_root, "component", name="ProjectJdkTable")
    for old in list(table):
        if old.find("name") is not None and old.find("name").get("value") == SDK:
            table.remove(old)
    jdk = ET.SubElement(table, "jdk", version="2")
    ET.SubElement(jdk, "name", value=SDK)
    ET.SubElement(jdk, "type", value="JavaSDK")
    ET.SubElement(jdk, "version", value='java version "25"')
    ET.SubElement(jdk, "homePath", value=str(java_home))
    roots = ET.SubElement(jdk, "roots")
    classes = ET.SubElement(ET.SubElement(roots, "classPath"), "root", type="composite")
    for module in sorted((java_home / "jmods").glob("*.jmod")):
        ET.SubElement(classes, "root", type="simple", url=f"jrt://{java_home}!/{module.stem}")
    ET.SubElement(ET.SubElement(roots, "sourcePath"), "root", type="composite")
    ET.SubElement(jdk, "additional")
    write_xml(sdk_path, sdk_root)
    misc = ET.Element("project", version="4")
    ET.SubElement(misc, "component", {"name": "ProjectRootManager", "version": "2", "languageLevel": "JDK_17",
                                       "project-jdk-name": SDK, "project-jdk-type": "JavaSDK"})
    write_xml(project / ".idea/misc.xml", misc)
    modules_xml = ET.Element("project", version="4")
    entries = ET.SubElement(ET.SubElement(modules_xml, "component", name="ProjectModuleManager"), "modules")
    for name, body, html in [
        ("mcp-fixture", 'add(new Label("title")); Form<Void> form = new Form<>("form"); form.add(new TextField<String>("name")); add(form);',
         '<html><body><span wicket:id="title"></span><form wicket:id="form"><input wicket:id="name"/></form></body></html>'),
        ("mcp-other", 'add(new Label("other"));', '<html><body><span wicket:id="other"></span></body></html>'),
    ]:
        directory = project / name
        java = directory / "src/main/java/example/McpPage.java"
        java.parent.mkdir(parents=True, exist_ok=True)
        java.write_text('package example; import org.apache.wicket.markup.html.WebPage; import org.apache.wicket.markup.html.basic.Label; '
                        'import org.apache.wicket.markup.html.form.Form; import org.apache.wicket.markup.html.form.TextField; '
                        f'public class McpPage extends WebPage {{ public McpPage() {{ {body} }} }}\n')
        java.with_name("PlainJava.java").write_text("package example; public class PlainJava {}\n")
        markup = directory / "src/main/resources/example/McpPage.html"
        markup.parent.mkdir(parents=True, exist_ok=True)
        markup.write_text(html)
        iml = directory / f"{name}.iml"
        ET.SubElement(entries, "module", fileurl=f"file://{iml}", filepath=str(iml))
        module = ET.Element("module", type="JAVA_MODULE", version="4")
        manager = ET.SubElement(module, "component", name="NewModuleRootManager", LANGUAGE_LEVEL="JDK_17")
        content = ET.SubElement(manager, "content", url="file://$MODULE_DIR$")
        ET.SubElement(content, "sourceFolder", url="file://$MODULE_DIR$/src/main/java", isTestSource="false")
        ET.SubElement(content, "sourceFolder", url="file://$MODULE_DIR$/src/main/resources", type="java-resource")
        ET.SubElement(manager, "orderEntry", type="jdk", jdkName=SDK, jdkType="JavaSDK")
        ET.SubElement(manager, "orderEntry", type="sourceFolder", forTests="false")
        library = ET.SubElement(ET.SubElement(manager, "orderEntry", type="module-library"), "library", name="Wicket fixture")
        class_roots = ET.SubElement(library, "CLASSES")
        for jar in libraries:
            ET.SubElement(class_roots, "root", url=f"jar://{jar}!/")
        ET.SubElement(library, "JAVADOC")
        ET.SubElement(library, "SOURCES")
        write_xml(iml, module)
    source = project / "mcp-fixture/src/main/java/example"
    resources = project / "mcp-fixture/src/main/resources/example"
    for name, body, markup in [
        ("ValidationPage", 'add(new org.apache.wicket.markup.html.basic.Label("javaOnly"));', '<html><body><span wicket:id="htmlOnly"></span></body></html>'),
        ("UnresolvedPage", 'if (System.getProperty("enabled") != null) add(new org.apache.wicket.markup.html.basic.Label("title"));', '<html><body><span wicket:id="title"></span></body></html>'),
        ("RepeaterPage", '''add(new org.apache.wicket.markup.html.list.ListView<String>("rows", java.util.List.of("Ada")) {
            protected void populateItem(org.apache.wicket.markup.html.list.ListItem<String> item) {
                item.add(new org.apache.wicket.markup.html.basic.Label("name", item.getModelObject()));
            }
        });''', '<html><body><div wicket:id="rows"><span wicket:id="name"></span></div></body></html>'),
        ("DuplicatePage", 'add(new org.apache.wicket.markup.html.basic.Label("title")); add(new org.apache.wicket.markup.html.basic.Label("title"));', '<html><body><span wicket:id="title"></span></body></html>'),
    ]:
        (source / f"{name}.java").write_text(f'package example; public class {name} extends org.apache.wicket.markup.html.WebPage {{ public {name}() {{ {body} }} }}\n')
        (resources / f"{name}.html").write_text(markup)
    write_xml(project / ".idea/modules.xml", modules_xml)
    print("Prepared disposable two-module Wicket project and isolated SDK configuration.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ["project-dir", "ide-config", "java-home", "libraries"]:
        parser.add_argument("--" + key, required=True)
    args = parser.parse_args()
    prepare(Path(args.project_dir).resolve(), Path(args.ide_config).resolve(), Path(args.java_home).resolve(),
            [Path(path).resolve() for path in args.libraries.split(os.pathsep)])
