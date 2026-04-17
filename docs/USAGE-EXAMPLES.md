# Usage Examples

Example prompts for interacting with the SAP ABAP MCP Server.

## Reading Code

```
"Show me the source code for class ZCL_MY_CLASS"

"Get the test include for ZCL_MY_CLASS"

"Read program ZTEST_REPORT"

"Search for all classes starting with ZCL_UTIL"
```

## Editing Code

```
"Create a session, update class ZCL_MY_CLASS to add a new method GET_DATA, then activate it"

"Modify program ZTEST_REPORT to add error handling"

"Add unit tests to ZCL_MY_CLASS test include"
```

## Testing

```
"Run ABAP Unit tests for class ZCL_MY_CLASS"

"Run tests for program ZTEST_REPORT with coverage"

"Check syntax of class ZCL_MY_CLASS without activating"
```

## Code Analysis

```
"Run ATC checks on class ZCL_MY_CLASS"

"Run ATC checks on package ZTEST_PKG using check variant DEFAULT"

"Get ATC findings for worklist ID 42010AEF83F01FE180A2C80BB75D6DF1"
```

## Version History and Comparison

```
"Show me the version history for class ZCL_MY_CLASS"

"Get the source code from version 00005 of ZCL_MY_CLASS"

"Compare the last two versions of ZCL_MY_CLASS"

"Compare the active and inactive versions of program ZTEST_REPORT"
```

## Dictionary Objects

```
"Get me the fields of structure BAPIRET2"

"Show the definition of table SFLIGHT"

"What are the properties of data element MATNR?"

"Get the CDS view I_FLIGHT"
```

## Complete Workflow Example

```
"Read class ZCL_MY_CLASS, add a new method CALCULATE_TOTAL that sums an internal table,
save the changes, check syntax, and if valid, activate the class"
```

## Multi-System Operations

```
"List available SAP systems"

"Create a session for the production system"

"Get class ZCL_MY_CLASS from the test system"

"Compare the same class across dev and prod systems"
```
