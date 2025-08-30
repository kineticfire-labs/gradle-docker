# Design Documents - Overview

This document provides an overview of the design documents guiding the implementation of this project.

## Design Documents - Types

There are two type of design documents in this project:

| Design Document Type         | Description                                                                                                                                                                                                |
|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Requirements Document (RD)   | outlines what a project or system needs to do, focusing on stakeholder needs and desired outcomes in a user-friendly, business-oriented language.  Requirements documents define the problem to be solved. |
| Specifications Document (SD) | details how requirements will be achieved, providing technical and implementation-specific information for developers.  Specifications documents describe the solution to the problem.                     |

A Requirements Document (RD) is used to develop specifications documents.  A Specification Document (SD) is used by
developers to implement the software.

Subtypes of design documents--down to individual use cases, requirements and specification--help break-up long documents 
for organization and focus on specific aspects, such as a feature.

### Requirements Document

Subtypes of requirements documents include:

| Requirements Document Sub-type              | Description                                                                                                                                                                                                                                                                                                                                                                    |
|---------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Use Case Document (UCD)                     | describes how a user, called an "actor," interacts with a system to achieve a specific goal, outlining the steps involved, potential issues, and system responses from the user's perspective.  These drive functional and non-functional requirements documents.                                                                                                              |
| Functional Requirements Document (FRD)      | provides a detailed guide that specifies what a software or system must do by describing its features, functions, and user interactions. It acts as a blueprint, explaining the system's purpose and how users will interact with it, ensuring that the final product meets user needs and business goals.                                                                     |
| Non-Functional Requirements Document (NFRD) | outlines the quality attributes and constraints of a system, describing how the system should work rather than what it should do. These documents are critical for setting measurable goals for performance, security, usability, reliability, and maintainability, and they are essential for guiding development, ensuring user satisfaction, and achieving project success. |


### Specifications Document

Subtypes of specifications documents include:

| Specifications Document Sub-type              | Description                                                                                                                                                                                                              |
|-----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Functional Specifications Document (FSD)      | describes what a system should do from an end-user perspective, focusing on business needs, user experiences, and features like inputs, processing, and outputs.                                                         |
| Non-functional Specifications Document (NFSD) | outlines how the system should perform its functions. The NFSD  specifies its operational qualities and constraints.                                                                                                     |
| Technical Specifications Document (TSD)       | details how the system will be built, providing developers with the technical blueprint for the architecture, data models, integrations, and implementation details needed to fulfill the FSD's and NFSD's requirements. |
 

## Relationship of Design Documents

1. A use case in a Use Case Document (UCD) results in the creation of one or functional requirements in a Functional 
Requirements Document (FRD) and/or Non-Functional Requirements Document (NFRD).
1. A requirement in a Functional Requirements Document (FRD) or Non-Functional Requirements Document (NFRD) results in
the creation of one or more specifications in a Functional Specification Document (FSD) and/or Non-functional 
Specification Document (NFSD).
1. A specification in a Functional Specification Document (FSD) or Non-functional Specification Document (NFSD) results
in the creation of one or more technical specifications in a Technical Specification Document (TSD).
1. Developers implement the system according to technical specifications in the Technical Specification Document (TSD). 

## Design Documents - Organization

Design documents are organized into this directory structure:

- `docs/design-docs/design-docs-overview.md`: overview of design documents (this document)
   - `docs/design-docs/requirements/`: directory containing the requirements documents (RDs)
      - `docs/design-docs/requirements/requirements.md`: the top-level requirements document (RDs)
      - `docs/design-docs/requirements/use-cases/`: directory containing use cases documents (UCDs)
      - `docs/design-docs/requirements/functional-requirements/`: directory containing  the functional 
      requirements (FRDs)
      - `docs/design-docs/requirements/non-functional-requirements/`: directory containing the non-functional 
      requirements (NFRDs)
   - `docs/design-docs/specifications/`: directory containing the specifications documents (SDs)
       - `docs/design-docs/specifications/specifications.md`: the top-level specifications document (SDs)
       - `docs/design-docs/specifications/functional-specifications/`: directory containing the functional 
       specifications (FSD)
       - `docs/design-docs/specifications/non-functional-specifications/`: directory containing the non-functional 
       specifications (NFSD)
       - `docs/design-docs/specifications/technical-specifications/`: directory containing the technical specifications 
       (TSD)

## Naming and Tracking of Design Documents

The convention for titling design documents follows the names given in the 
[Design Documents - Types](#design-documents---types), [Requirements Design Documents](#requirements-design-documents), 
and [Specifications Design Documents](#specifications-design-documents) sections.  File names generally drop the ending
"document".

Individual use cases, requirements, and specification file names are named per the following convention: 
`<type abbreviation>-<4 digit ID>-<short description, using kebab case>`. 
- Example design document file name for a Use Case Document (UCD) with ID 1: `uc-0001-user-buy-item`.

The title for those individual items are named per the following convention:
`<type abbreviation> - <4 digit ID> - <short decription, using normal document titling spacing and capialization>`
- An example title for the document above: `uc - 0001 - User Buys an Item`.

### Type Abbreviations

Type abbreviations refer to the type of content in the document, e.g. a Use Case Document (UCD) defines one or more use
cases (abbreviated below as "uc").

| Type                         | Abbreviation |
|------------------------------|--------------|
| Use case                     | uc           |
| Functional requirement       | fr           |
| Non-functional requirement   | nfr          |
| Functional specification     | fs           |
| Non-functional specification | nfs          |
| Technical specification      | ts           |

### ID

Each use case, requirement, and specification must have assigned a numeric ID.  The ID must be unique within that 
category (e.g., functional requirement, non-functional requirement, functional specification, non-functional 
specification, and technical specification).

The ID is expressed as a four-digit number, so numbers less than four digits must receive preceding zeros.

### Short Description

A short description to help quickly understand what the document might describe ends the document file name.  This 
should be lowercase and use kebab naming (e.g., separate words with dashes).

## Design Documents Meta Data & Status

All design documents have a metadata section at the top of the document to help understand the document, its status,
and the status of the implementation related to the document.

Example design document metadata appears below:
```
Doc meta
Owner: <name>
Status: Draft
Version: 0.1.0
Last updated: 2025-08-29
Links: repo, ADRs, prototypes, CI pipeline
Comment: <explain status of document, priority, dependent tasks, etc.>
```

### Status Metadata Property

The _Status_ metadata property gives the status of the document related to its review, approval, and implementation.  
Allowed values are as follows.  Note that "implemented" below may could refer to the implementation of a TSD
resulting in code or the implementation of an FRD resulting in functional specifications written into a FSD.

| Status Value         | Description                                                                                                                                                                                                                             |
|----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Draft                | The document has not been approved for implementation; do not implement!                                                                                                                                                                |
| Approved             | The document has been approved for implementation.                                                                                                                                                                                      |
| In-progress          | The document is being implemented.                                                                                                                                                                                                      |
| Complete             | The document was implemented.                                                                                                                                                                                                           |
| Revision-draft       | The document was previously implemented, and has now been changed but not approved for implementation; do not implement! Also, the content of the document may not reflect the current system state; refer to the document's changelog. |
| Revision-approved    | The document under revision has been approved for implementation.                                                                                                                                                                       |
| Revision-in-progress | The document under revision is being implemented.                                                                                                                                                                                       |

Note that once a document with status `Revision-in-progress` is implemented, it's status changes to `Complete`.

Update the document's _Status_ property as changes from the document are incorporated.

Update the document's _Version_ and _Date_ properties as the document content is modified or as the _Status_ changes.

## Requirements
The requirements for the project are defined by the [Requirements Document (RD)](requirements/requirements.md).

## Specifications
The specifications for the project are defined by the [Specifications Document (SD)](specifications/specifications.md).

## Acronyms

| Acronym | Term                                   |
|---------|----------------------------------------|
| RD      | Requirements Document                  |
| SD      | Specification Document                 |
| UCD     | Use Case Document                      |
| FRD     | Functional Requirements Document       |
| NFRD    | Non-Functional Requirements Document   |
| FSD     | Functional Specifications Document     |
| NFSD    | Non-functional Specifications Document |
| TSD     | Technical Specifications Document      |
