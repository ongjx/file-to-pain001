# Payment transformation: mapping assurance and deployment options

Research recorded: **2026-10-15**.

This note captures the research behind this project's custom Java converter,
webMethods integration, and build-versus-buy discussion. It is supporting research,
not a bank acceptance certificate, a procurement decision, or a commitment to migrate
to Spring Boot. The implementation contract remains in [OpenSpec](../openspec/changes/file-to-pain001/design.md).

## Findings and strength of evidence

| Finding | Evidence | Limit of the conclusion |
| --- | --- | --- |
| Java services are a supported webMethods extension mechanism. | IBM documents FLOW, adapter and Java services, including development outside Designer. [1] | This establishes product support, not how frequently banks use this exact architecture. |
| Banks use Java/Spring Boot for payment applications. | ING describes Java 21/Spring Boot in payment-order orchestration; JPMorganChase advertises Java/Spring Boot roles in Commercial & Investment Bank Payments Technology. [2][3] | Recruitment descriptions demonstrate adoption in those teams. They do not reveal the banks' complete architectures or prove middleware has been eliminated. |
| Specialist transformation products provide capabilities beyond XML serialization. | Trace Financial describes visual mapping, reusable standards/mapping libraries, testing, and cross-field validation in Transformer. [4][5] | These are vendor descriptions of capabilities, not independently measured cost savings or proof of suitability for our client contracts. |
| XSD validation is only one part of ISO 20022 compliance. | ISO's checklist separately identifies schema validity, message constraints and registered code values. [6] | Schema-valid XML does not establish correct source interpretation or acceptance by a particular bank. |

**Assessment:** custom Java transformation is a legitimate implementation approach,
and independently deployed Java applications are an evidenced banking practice.
The sources do not establish one universal banking architecture, nor that engineering
team strength determines whether an integration platform should be retained.

## What makes a mapping correct?

There are separate questions to answer:

1. **Is the XML structurally valid?** The bundled XSD and generated JAXB classes
   help answer this: element structure, types, required schema values and constraints.
2. **Does the implementation preserve the agreed source meaning?** A reviewed
   mapping contract and independent expected results must establish this. Correct
   XML structure alone cannot detect a valid-looking debtor/creditor swap or a
   misinterpreted amount.
3. **Does it satisfy applicable message and recipient rules?** ISO message
   documentation, code sets and the receiving bank's implementation guide supply
   requirements beyond our generic XSD. ISO provides message-definition reports
   and, where applicable, usage guides alongside schemas. [6][7]
4. **Does the receiving bank accept this agreed profile?** Its onboarding or test
   process supplies acceptance evidence for the particular contract and version.
   Acceptance of test messages does not guarantee every future production payment
   will be accepted or executed.

Our synthetic fixtures establish behavior against documented prototype contracts.
They are not independent confirmation of a real client's semantics. A test suite
can consistently verify an incorrect assumption if both its inputs and expected
results come from that assumption.

A BA/product owner and payments-domain reviewers should specify more than source
and target field names. A mapping matrix should include:

| Item | Example question |
| --- | --- |
| Source meaning | Is `amount` the instructed creditor amount, debtor debit amount, or invoice amount? |
| Target meaning | Which pain.001 element or attribute expresses that meaning in the agreed edition? |
| Transformation | Which date format, decimal rules, account identification, FX direction and rounding apply? |
| Relationships | Which invoices belong to which transaction, and must source batches remain separate? |
| Missing or unsupported data | Reject, explicitly default under an approved rule, or record an agreed omission? |
| Validation | Which file-level, cross-field, ISO and recipient-specific rules apply? |
| Expected result | What independently reviewed output and rejection cases demonstrate the rule? |

The working process is: **approved mapping contract → Java implementation →
independently approved tests → recipient acceptance testing**. A commercial mapping
tool changes the implementation tooling; it does not eliminate this process.

## Custom Java versus a specialist transformer

Trace Financial's Transformer offers a graphical mapping environment, reusable
message/mapping libraries and test tools. Its brochure describes financial-standard
libraries with cross-field validation and prebuilt mappings for certain standards.
Those capabilities can reduce the work a team otherwise owns. [4][5]

However, a library for a published standard does not by itself identify the meaning
of a proprietary client CSV. Custom input contracts still require specification,
review and acceptance evidence. Product coverage must be checked for the exact
message version, usage profile and source semantics needed.

The following are project assessments rather than vendor performance claims:

| Situation | Reasonable approach to evaluate |
| --- | --- |
| A manageable number of stable layouts and output versions, maintained by developers | Continue with explicit Java mappings and strong contract/acceptance tests. |
| Numerous financial standards, recipient profiles, frequent standards updates, or analyst-maintained mappings | Evaluate a specialist transformer against representative real contracts. |
| An enterprise already operates a suitable transformation platform | Assess its actual coverage and operating model before duplicating capabilities. |

There is no technical dependency on Trace Financial in this project. Maintaining
the converter in Java is viable if the team accepts responsibility for source
contracts, mapping changes, validation coverage, version updates and operations.
No total-cost comparison, licensing evaluation or product proof of concept has
been performed. A procurement decision needs that additional evidence.

## Mapping implementation and application hosting are separate choices

Choosing Java mappings does not determine whether webMethods should remain.
IBM supports both Java services and graphical FLOW mappings. [1][8]

| Deployment option | Mapping location | Role of webMethods | Main tradeoff to assess |
| --- | --- | --- | --- |
| Native FLOW mapping | FLOW/document mappings | Mapping and surrounding integration | Platform tooling and release process versus code-based mapping maintenance |
| Embedded Java library — current project | JAR inside Integration Server | Hosts the Java call and surrounding FLOW | Reuses the integration runtime; shares its dependency/class-loading environment |
| Independently deployed Java/Spring Boot application called by webMethods | Separate application process | Receives/routes requests and calls the application over an API or messaging | Independent releases/resources; introduces a network interface and another runtime to operate |
| Application-owned integration for this flow | Separate application process | May have no role in this particular flow | Team/platform services must own the surrounding integration and operations |

Spring Boot is one possible application host, not a prerequisite for payment
transformation and not a requirement to split the system into many microservices.
Removing webMethods from one flow does not imply removing it from a bank's estate.

For an independent application, responsibilities still need explicit owners:
authentication, file transfer/storage, processing status, audit records, monitoring,
retry/replay and duplicate protection. Existing platform services or libraries may
provide them; they need not all be handwritten. A remote application also cannot
automatically read a file path available only on the webMethods host.

## Implications for this repository

- Keep conversion logic independent of webMethods. The core accepts ordinary Java
  values; the vendor-specific adapter lives under `examples/webmethods/`.
- Continue the current deployment while webMethods provides useful orchestration
  and connectivity. Moving to Spring Boot is not necessary to make a mapping correct.
- Reuse the conversion core behind an HTTP, messaging or batch entry point if
  independent releases, scaling, reuse or operational ownership justify it.
- Treat our PH/TX/IN/FX contract as a mock contract. Obtain a real client and
  receiving-bank specification before treating that profile as production-ready.
- Use one approved client-to-bank mapping and its independent expected results as
  the next benchmark, including for any future vendor proof of concept.

The local dependency and Xerces incidents illustrate shared-runtime coupling in
the embedded arrangement. A fat JAR avoids missing dependency files; selecting the
Java runtime's schema provider resolves the reproduced provider conflict. These
fixes do not demonstrate that all class-loader conflicts are impossible, or that
a separately hosted application would remove the need for dependency management.
See [verification evidence](verification.md), [deployment guidance](webmethods-integration.md)
and [input profile design](input-profiles.md).

## Sources

Sources were consulted on 2026-10-15. Product documentation and recruitment pages
may change; bank examples are evidence of specific published roles, not a survey
of industry deployment prevalence. Vendor capability descriptions are attributed
to the vendor. Recommendations above are our interpretation of the evidence and
this project's scope.

1. [IBM — Managing Services](https://www.ibm.com/docs/en/webmethods-integration/wm-integration-server/10.15.0?topic=guide-managing-services).
   Documents Java, FLOW and adapter services and external development environments.
2. [ING — Java Engineer, payment-order orchestration platform](https://careers.ing.com/en/job/makati-city/java-engineer/3121/44885917696).
   Describes Java 21/Spring Boot services and development-through-operations ownership
   in the Payment & Settlement Services landscape.
3. [JPMorganChase — Senior Lead Software Engineer, Payments, AI, Java, Spring Boot](https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001/job/210797091).
   Published role in Commercial & Investment Bank Payments Technology.
4. [Trace Financial — Transformer](https://www.tracefinancial.com/transformer/).
   Product description of visual mapping, reusable libraries, testing and deployment interfaces.
5. [Trace Financial — Transformer brochure](https://www.tracefinancial.com/wp-content/uploads/2021/04/Trace_Financial_Transformer_Brochure_A4_1_FMT_AW.pdf).
   Vendor description of supported standards, cross-field validation and mapping libraries.
6. [ISO 20022 — Compliance Checklist](https://www.iso20022.org/sites/default/files/media/file/ISO20022_ComplianceChecklist.pdf).
   Separates schema validity, message constraints and registered external code values.
7. [ISO 20022 — Catalogue of messages](https://www.iso20022.org/catalogue-messages).
   Identifies message-definition reports, usage guides where applicable, schemas and examples.
8. [IBM — Mapping Data in Flow Services](https://www.ibm.com/docs/en/webmethods-integration/wm-integration-server/10.15.0?topic=guide-mapping-data-in-flow-services).
   Documents graphical name, structure and value transformations in FLOW.
