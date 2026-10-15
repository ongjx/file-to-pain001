# file-to-pain001

Java 21/JAXB 4 Maven library that a webMethods Java service calls with an input path and optional profile to obtain validated pain.001 XML, with a separate runner for local verification.

The flat CSV baseline and profile extension are implemented: explicit versioned input profiles, shared payment validation/assembly, and a PH/TX/IN/FX multi-record handler with invoice and FX mappings. All six core multi-record samples convert with `mock-multirecord-v1`; focused valid and invalid cases cover the remaining contract rules.

The subsequent behavior-preserving cleanup separates CSV parsing from `MultiRecordPaymentAssembler` in the conversion package and shares Shade configuration between the webMethods and runner JARs. The implemented layout is recorded in the design; tasks section 13 and `docs/verification.md` record cleanup verification, including the optional skipped server-Xerces check.

See [proposal](proposal.md), [design](design.md), [profile requirements](specs/payment-input-profiles/spec.md), [CSV requirements](specs/csv-payment-conversion/spec.md), and [tasks](tasks.md). Implementation tasks and verification evidence are recorded in the linked task list and the project verification guide.
