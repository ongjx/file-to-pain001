# file-to-pain001

Java 21/JAXB 4 Maven library that a webMethods Java service calls with an input path and optional profile to obtain validated pain.001 XML, with a separate runner for local verification.

The flat CSV baseline is implemented. The planned extension adds explicit versioned input profiles, shared payment validation/assembly, and a PH/TX/IN/FX multi-record handler with invoice and FX mappings. The six mock multi-record samples exist, but converter support remains pending.

See [proposal](proposal.md), [design](design.md), [profile requirements](specs/payment-input-profiles/spec.md), [CSV requirements](specs/csv-payment-conversion/spec.md), and [tasks](tasks.md). Completed baseline tasks remain checked; new extension tasks are unchecked.
