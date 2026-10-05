#!/usr/bin/env python3
from pathlib import Path

project = Path(__file__).resolve().parent / "iosApp.xcodeproj" / "project.pbxproj"
text = project.read_text()

PACKAGE_REF = "C11111111111111111111111"
UI_PRODUCT = "C22222222222222222222222"
CORE_PRODUCT = "C33333333333333333333333"
UI_BUILD = "C44444444444444444444444"
CORE_BUILD = "C55555555555555555555555"

if PACKAGE_REF not in text:
    replacements = [
        (
            "\t\t7397CF462F80FE3A00AC0F84 /* MPVKit in Frameworks */ = {isa = PBXBuildFile; productRef = A1B2C3D4E5F6A7B8C9D0E1F2 /* MPVKit */; };",
            "\t\t7397CF462F80FE3A00AC0F84 /* MPVKit in Frameworks */ = {isa = PBXBuildFile; productRef = A1B2C3D4E5F6A7B8C9D0E1F2 /* MPVKit */; };\n"
            f"\t\t{UI_BUILD} /* SmartTubeIOS in Frameworks */ = {{isa = PBXBuildFile; productRef = {UI_PRODUCT} /* SmartTubeIOS */; }};\n"
            f"\t\t{CORE_BUILD} /* SmartTubeIOSCore in Frameworks */ = {{isa = PBXBuildFile; productRef = {CORE_PRODUCT} /* SmartTubeIOSCore */; }};",
        ),
        (
            "\t\t\t\t7397CF462F80FE3A00AC0F84 /* MPVKit in Frameworks */,",
            "\t\t\t\t7397CF462F80FE3A00AC0F84 /* MPVKit in Frameworks */,\n"
            f"\t\t\t\t{UI_BUILD} /* SmartTubeIOS in Frameworks */,\n"
            f"\t\t\t\t{CORE_BUILD} /* SmartTubeIOSCore in Frameworks */,",
        ),
        (
            "\t\t\t\tA1B2C3D4E5F6A7B8C9D0E1F2 /* MPVKit */,",
            "\t\t\t\tA1B2C3D4E5F6A7B8C9D0E1F2 /* MPVKit */,\n"
            f"\t\t\t\t{UI_PRODUCT} /* SmartTubeIOS */,\n"
            f"\t\t\t\t{CORE_PRODUCT} /* SmartTubeIOSCore */,",
        ),
        (
            "\t\t\t\tF1E2D3C4B5A6F7E8D9C0B1A2 /* XCLocalSwiftPackageReference \"../MPVKit\" */,",
            "\t\t\t\tF1E2D3C4B5A6F7E8D9C0B1A2 /* XCLocalSwiftPackageReference \"../MPVKit\" */,\n"
            f"\t\t\t\t{PACKAGE_REF} /* XCLocalSwiftPackageReference \"../SmartTubeIOS/SmartTubeIOS\" */,",
        ),
        (
            "/* End XCLocalSwiftPackageReference section */",
            f"\t\t{PACKAGE_REF} /* XCLocalSwiftPackageReference \"../SmartTubeIOS/SmartTubeIOS\" */ = {{\n"
            "\t\t\tisa = XCLocalSwiftPackageReference;\n"
            "\t\t\trelativePath = ../SmartTubeIOS/SmartTubeIOS;\n"
            "\t\t};\n"
            "/* End XCLocalSwiftPackageReference section */",
        ),
        (
            "/* End XCSwiftPackageProductDependency section */",
            f"\t\t{UI_PRODUCT} /* SmartTubeIOS */ = {{\n"
            "\t\t\tisa = XCSwiftPackageProductDependency;\n"
            f"\t\t\tpackage = {PACKAGE_REF} /* XCLocalSwiftPackageReference \"../SmartTubeIOS/SmartTubeIOS\" */;\n"
            "\t\t\tproductName = SmartTubeIOS;\n"
            "\t\t};\n"
            f"\t\t{CORE_PRODUCT} /* SmartTubeIOSCore */ = {{\n"
            "\t\t\tisa = XCSwiftPackageProductDependency;\n"
            f"\t\t\tpackage = {PACKAGE_REF} /* XCLocalSwiftPackageReference \"../SmartTubeIOS/SmartTubeIOS\" */;\n"
            "\t\t\tproductName = SmartTubeIOSCore;\n"
            "\t\t};\n"
            "/* End XCSwiftPackageProductDependency section */",
        ),
    ]

    for needle, replacement in replacements:
        if needle not in text:
            raise SystemExit(f"SmartTube Xcode patch anchor not found: {needle[:80]}")
        text = text.replace(needle, replacement, 1)

# SmartTubeIOS upstream currently declares iOS 17 as its minimum. Keep the
# upstream package untouched and align the Nuvio iOS build target instead.
text = text.replace("IPHONEOS_DEPLOYMENT_TARGET = 16.1;", "IPHONEOS_DEPLOYMENT_TARGET = 17.0;")

project.write_text(text)
print("SmartTubeIOS local Swift package linked into Nuvio iOS project")
