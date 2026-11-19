#!/usr/bin/env python3
"""Regression test for the bytecode reachability analyzer using a tiny Java fixture."""
import json
import pathlib
import subprocess
import sys
import tempfile

root = pathlib.Path(__file__).resolve().parents[1]
classes = pathlib.Path(sys.argv[1]).resolve()
asm = pathlib.Path(sys.argv[2]).resolve()
with tempfile.TemporaryDirectory(prefix="bytecode-reachability-test-") as tmp:
    work = pathlib.Path(tmp)
    src = work / "src/com/khayom1/missminutes"
    out = work / "classes"
    src.mkdir(parents=True)
    out.mkdir()
    (src / "Entry.java").write_text("""
package com.khayom1.missminutes;
public class Entry {
  public Entry() {}
  public void onCreate() { new Helper().used(); }
  public void unused() {}
}
""", encoding="utf-8")
    (src / "Helper.java").write_text("""
package com.khayom1.missminutes;
public class Helper {
  public Helper() {}
  public void used() {}
  public void dead() {}
}
""", encoding="utf-8")
    manifest = work / "AndroidManifest.xml"
    manifest.write_text("""<manifest xmlns:android="http://schemas.android.com/apk/res/android"
 package="com.khayom1.missminutes"><application><activity android:name=".Entry"/></application></manifest>""", encoding="utf-8")
    subprocess.run(["javac", "-d", str(out), *map(str, src.glob("*.java"))], check=True)
    report = work / "report.json"
    cp = str(classes) + ":" + str(asm)
    subprocess.run(["java", "-cp", cp, "BytecodeReachability",
                    str(manifest), str(report), str(out)], check=True)
    data = json.loads(report.read_text(encoding="utf-8"))
    unreachable = set(data["unreachable_candidates"])
    assert "com.khayom1.missminutes.Entry#unused()V" in unreachable, unreachable
    assert "com.khayom1.missminutes.Helper#dead()V" in unreachable, unreachable
    assert "com.khayom1.missminutes.Helper#used()V" not in unreachable, unreachable
    print("PASS: Android lifecycle roots, direct call reachability, and dead-method candidate detection")
