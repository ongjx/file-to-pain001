"""Validate synthetic fixtures, then record current converter compatibility."""
import argparse
import csv
from datetime import date
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parents[2]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def positive(value):
    require(re.fullmatch(r"[0-9]+(?:\.[0-9]+)?", value), f"Invalid decimal: {value}")
    result = Decimal(value)
    require(result > 0, f"Nonpositive decimal: {value}")
    return result


def inspect_fixture(path, expected):
    raw = path.read_bytes()
    require(raw.startswith(b"\xef\xbb\xbf") == expected.get("bom", False), "Unexpected BOM")
    if expected.get("crlf"):
        require(b"\r\n" in raw and b"\n" not in raw.replace(b"\r\n", b""), "Expected CRLF")
    with path.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.reader(stream, strict=True))
    counts = dict.fromkeys(("PH", "TX", "IN", "FX"), 0)
    widths = {"PH": 8, "TX": 9, "IN": 6, "FX": 7}
    batches, payments, invoice_ids, fx_refs, invoice_sums, totals = {}, {}, set(), set(), {}, {}
    active_batch = active_payment = None
    for number, row in enumerate(rows, 1):
        tag = row[0] if row else ""
        require(tag in widths and len(row) == widths[tag], f"Record {number}: bad type/width")
        require(all(value for i, value in enumerate(row) if not (tag == "TX" and i in (5, 8))), f"Record {number}: blank required field")
        counts[tag] += 1
        if tag == "PH":
            require(row[1] not in batches, "Duplicate batch")
            date.fromisoformat(row[6])
            require(row[7].isdigit() and int(row[7]) > 0, "Invalid declared payment count")
            batches[row[1]] = {"expected": int(row[7]), "actual": 0}
            active_batch, active_payment = row[1], None
        elif tag == "TX":
            require(row[1] == active_batch, "TX must belong to current PH")
            require(row[2] not in payments, "Duplicate payment reference")
            require(re.fullmatch(r"[A-Z]{3}", row[7]), "Invalid currency syntax")
            amount = positive(row[6])
            payments[row[2]] = (amount, row[7])
            totals[row[7]] = totals.get(row[7], Decimal(0)) + amount
            batches[active_batch]["actual"] += 1
            active_payment = row[2]
        else:
            require(row[1] == active_payment and active_payment in payments, "Detail must follow its TX")
            amount, currency = payments[active_payment]
            if tag == "IN":
                key = (active_payment, row[2])
                require(key not in invoice_ids, "Duplicate invoice within payment")
                invoice_ids.add(key)
                date.fromisoformat(row[3])
                require(row[5] == currency, "Invoice currency differs from TX")
                invoice_sums[active_payment] = invoice_sums.get(active_payment, Decimal(0)) + positive(row[4])
            else:
                require(active_payment not in fx_refs, "Duplicate FX for payment")
                fx_refs.add(active_payment)
                require(re.fullmatch(r"[A-Z]{3}", row[2]) and row[2] != row[3] == currency, "Invalid FX currencies")
                require(positive(row[4]) * positive(row[5]) == amount, "FX source amount times rate differs from TX")
    require(all(batch["expected"] == batch["actual"] for batch in batches.values()), "PH count mismatch")
    require(all(total == payments[ref][0] for ref, total in invoice_sums.items()), "Invoices do not reconcile to TX")
    require(counts == dict(zip(("PH", "TX", "IN", "FX"), (expected[k] for k in ("batches", "payments", "invoices", "exchanges")))), "Unexpected record counts")
    require(totals == {k: Decimal(v) for k, v in expected["totals"].items()}, "Payment totals mismatch")
    return {"record_counts": counts, "payment_totals": {k: str(v) for k, v in totals.items()}, "sha256": hashlib.sha256(raw).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default=str(Path(os.environ["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in os.environ else "java")
    parser.add_argument("--jar", type=Path, default=PROJECT / "target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar")
    args = parser.parse_args()
    report = {"layout": "Synthetic PH/TX/IN/FX v1; not a bank specification", "samples": []}
    with tempfile.TemporaryDirectory(prefix="multirecord-check-") as temporary:
        temporary = Path(temporary)
        control = subprocess.run([args.java, "-jar", str(args.jar.resolve()), str(PROJECT / "samples/csv/valid/basic.csv")], cwd=temporary, capture_output=True, text=True, timeout=20)
        require(control.returncode == 0 and not control.stderr, "Flat CSV control failed: " + control.stderr)
        root = ET.fromstring(control.stdout)
        ns = {"p": "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09"}
        require(root.findtext(".//p:GrpHdr/p:NbOfTxs", namespaces=ns) == "2", "Control transaction count mismatch")
        report["flat_csv_control"] = "Converted successfully; two transactions"
        for expected in json.loads((HERE / "expected.json").read_text()):
            path = HERE / expected["name"]
            sample = {"file": path.name, "fixture_check": "valid for documented mock layout", **inspect_fixture(path, expected), "converter_attempts": []}
            for layout in ("default", "camel-case"):
                output = temporary / f"{path.stem}-{layout}.xml"
                result = subprocess.run([args.java, "-jar", str(args.jar.resolve()), str(path), "--layout", layout, "--output", str(output)], cwd=temporary, capture_output=True, text=True, timeout=20)
                sample["converter_attempts"].append({"layout": layout, "exit_code": result.returncode, "diagnostic": result.stderr.strip(), "xml_created": output.exists()})
                require(result.returncode == 4 and not result.stdout and not output.exists(), f"Unexpected conversion behavior for {path.name} / {layout}")
                require("missing required columns" in result.stderr, "Unexpected rejection reason: " + result.stderr)
            require(hashlib.sha256(path.read_bytes()).hexdigest() == sample["sha256"], "Input was modified")
            report["samples"].append(sample)
    report["conversion_result"] = "Unsupported: all six multi-record files rejected by both existing layouts; no XML created"
    destination = PROJECT / "target/multirecord-test-results.json"
    destination.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print("Fixture checks: 6/6 valid for the documented mock layout.")
    print("Flat CSV control: converted successfully.")
    print(report["conversion_result"])
    print(f"Detailed report: {destination}")


if __name__ == "__main__":
    main()
