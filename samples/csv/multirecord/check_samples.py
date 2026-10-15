"""Check mock fixtures, selected-profile XML mappings, and wrong-profile rejection."""
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


def inspect_xml(path, xml, expected):
    root = ET.fromstring(xml)
    ns = {"p": "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09"}
    def text(node, query):
        return node.findtext(query, default="", namespaces=ns)
    def equal(actual, wanted, description):
        require(actual == wanted, f"XML mismatch: {description}")
    groups = root.findall(".//p:PmtInf", ns)
    actual = {}
    for group in groups:
        transactions = group.findall("p:CdtTrfTxInf", ns)
        equal(int(text(group, "p:NbOfTxs")), len(transactions), "group count")
        total = sum((Decimal(text(tx, "p:Amt/p:InstdAmt")) for tx in transactions), Decimal(0))
        equal(Decimal(text(group, "p:CtrlSum")), total, "group total")
        for tx in transactions:
            ref = text(tx, "p:PmtId/p:EndToEndId")
            require(ref not in actual, "Duplicate output reference")
            actual[ref] = (tx, group)
    equal(len(actual), expected["payments"], "transaction count")
    equal(int(text(root, ".//p:GrpHdr/p:NbOfTxs")), expected["payments"], "message count")
    if len(expected["totals"]) == 1:
        equal(Decimal(text(root, ".//p:GrpHdr/p:CtrlSum")), Decimal(next(iter(expected["totals"].values()))), "message total")
    else:
        equal(text(root, ".//p:GrpHdr/p:CtrlSum"), "", "mixed-currency message sum omitted")
    header = current = None
    invoice_index = 0
    source_groups = set()
    with path.open(encoding="utf-8-sig", newline="") as stream:
        for row in csv.reader(stream, strict=True):
            if row[0] == "PH":
                header = row
            elif row[0] == "TX":
                current, group = actual[row[2]]
                invoice_index = 0
                source_groups.add((header[1], row[7]))
                equal(text(root, ".//p:GrpHdr/p:InitgPty/p:Nm"), header[2], "initiating party")
                for query, value in (("p:Dbtr/p:Nm", header[3]), ("p:DbtrAcct/p:Id/p:IBAN", header[4]),
                                     ("p:DbtrAgt/p:FinInstnId/p:BICFI", header[5]), ("p:ReqdExctnDt/p:Dt", header[6])):
                    equal(text(group, query), value, query)
                for query, value in (("p:Cdtr/p:Nm", row[3]), ("p:CdtrAcct/p:Id/p:IBAN", row[4]),
                                     ("p:CdtrAgt/p:FinInstnId/p:BICFI", row[5]), ("p:RmtInf/p:Ustrd", row[8])):
                    equal(text(current, query), value, query)
                instructed = current.find("p:Amt/p:InstdAmt", ns)
                equal(Decimal(instructed.text), Decimal(row[6]), "instructed amount")
                equal(instructed.attrib["Ccy"], row[7], "instructed currency")
            elif row[0] == "IN":
                invoices = current.findall("p:RmtInf/p:Strd", ns)
                require(invoice_index < len(invoices), "Missing invoice")
                invoice = invoices[invoice_index]
                invoice_index += 1
                equal(text(invoice, "p:RfrdDocInf/p:Nb"), row[2], "invoice ID")
                equal(text(invoice, "p:RfrdDocInf/p:RltdDt"), row[3], "invoice date")
                amount = invoice.find("p:RfrdDocAmt/p:RmtdAmt", ns)
                require(amount is not None, "Missing invoice amount")
                equal(Decimal(amount.text), Decimal(row[4]), "invoice amount")
                equal(amount.attrib["Ccy"], row[5], "invoice currency")
            elif row[0] == "FX":
                equal(text(current, "p:XchgRateInf/p:UnitCcy"), row[2], "FX unit currency")
                equal(Decimal(text(current, "p:XchgRateInf/p:XchgRate")), Decimal(row[5]), "FX rate")
                equal(text(current, "p:XchgRateInf/p:CtrctId"), row[6], "FX contract")
                equal(text(current, "p:XchgRateInf/p:RateTp"), "", "no invented rate type")
    equal(len(groups), len(source_groups), "source batch/currency groups")
    equal(len(root.findall(".//p:CdtTrfTxInf/p:RmtInf/p:Strd", ns)), expected["invoices"], "invoice count")
    equal(len(root.findall(".//p:CdtTrfTxInf/p:XchgRateInf", ns)), expected["exchanges"], "FX count")


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
            output = temporary / f"{path.stem}-selected.xml"
            result = subprocess.run([args.java, "-jar", str(args.jar.resolve()), str(path), "--profile", "mock-multirecord-v1", "--output", str(output)], cwd=temporary, capture_output=True, text=True, timeout=20)
            require(result.returncode == 0 and not result.stdout and not result.stderr and output.exists(), f"Selected-profile conversion failed: {result.stderr}")
            inspect_xml(path, output.read_text(), expected)
            sample["selected_profile"] = {"profile": "mock-multirecord-v1", "exit_code": result.returncode, "mapping_check": "payments, groups, invoices, FX and exact totals preserved"}
            for layout in ("default", "camel-case"):
                output = temporary / f"{path.stem}-{layout}.xml"
                result = subprocess.run([args.java, "-jar", str(args.jar.resolve()), str(path), "--layout", layout, "--output", str(output)], cwd=temporary, capture_output=True, text=True, timeout=20)
                sample["converter_attempts"].append({"layout": layout, "exit_code": result.returncode, "diagnostic": result.stderr.strip(), "xml_created": output.exists()})
                require(result.returncode == 4 and not result.stdout and not output.exists(), f"Unexpected conversion behavior for {path.name} / {layout}")
                require("missing required columns" in result.stderr, "Unexpected rejection reason: " + result.stderr)
            require(hashlib.sha256(path.read_bytes()).hexdigest() == sample["sha256"], "Input was modified")
            report["samples"].append(sample)
    report["conversion_result"] = "All six files converted with mock-multirecord-v1; mappings verified; both flat layouts reject them without output"
    destination = PROJECT / "target/multirecord-test-results.json"
    destination.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print("Fixture checks: 6/6 valid for the documented mock layout.")
    print("Flat CSV control: converted successfully.")
    print(report["conversion_result"])
    print(f"Detailed report: {destination}")


if __name__ == "__main__":
    main()
