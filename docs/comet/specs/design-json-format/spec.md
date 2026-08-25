# Capability: design-json-format

## Requirements

### Requirement: Preserve semantic JSON structure

`design/design.json` MUST preserve every entry, key, value, array order, field object and Chinese `description`; only insignificant whitespace may change.

#### Scenario: Structure is unchanged

- **WHEN** the reformatted file is parsed and compared with the pre-change file after whitespace normalization
- **THEN** the parsed structures are deeply equal
- **AND** the entry count remains 57
- **AND** every `fields` and `resultFields` item remains present with the same values

### Requirement: Use partial compression for field arrays

The file MUST keep top-level and entry-level objects readable across multiple lines. `fields` and `resultFields` arrays MUST be multiline arrays whose field objects each occupy one compact line in the form `{ "name": "...", "type": "...", "description": "..." }`, with `defaultValue` retained when present.

#### Scenario: Human-readable field layout

- **WHEN** a reviewer opens `design/design.json`
- **THEN** the file is not a single-line JSON document
- **AND** each `fields`/`resultFields` array has one field object per line
- **AND** field object keys remain compact without redundant indentation or blank lines

### Requirement: Preserve generator compatibility

The reformatted JSON MUST remain compatible with the existing source generation and analyzer pipeline.

#### Scenario: Generation still succeeds

- **WHEN** the project runs the existing source generation and start test suite
- **THEN** both complete successfully without requiring generator changes
