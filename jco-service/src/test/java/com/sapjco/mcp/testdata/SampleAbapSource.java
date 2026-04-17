package com.sapjco.mcp.testdata;

/**
 * Sample ABAP source code for testing handlers.
 */
public final class SampleAbapSource {

    private SampleAbapSource() {
        // Utility class - no instantiation
    }

    /**
     * Simple ABAP class definition.
     */
    public static final String CLASS_DEFINITION = """
            CLASS zcl_test_class DEFINITION
              PUBLIC
              FINAL
              CREATE PUBLIC.

              PUBLIC SECTION.
                METHODS constructor.
                METHODS get_value
                  RETURNING VALUE(rv_result) TYPE string.

              PROTECTED SECTION.
              PRIVATE SECTION.
                DATA mv_value TYPE string.
            ENDCLASS.

            CLASS zcl_test_class IMPLEMENTATION.
              METHOD constructor.
                mv_value = 'test'.
              ENDMETHOD.

              METHOD get_value.
                rv_result = mv_value.
              ENDMETHOD.
            ENDCLASS.
            """;

    /**
     * ABAP interface definition.
     */
    public static final String INTERFACE_DEFINITION = """
            INTERFACE zif_test_interface
              PUBLIC.

              TYPES:
                BEGIN OF ty_data,
                  id   TYPE sysuuid_c32,
                  name TYPE char50,
                END OF ty_data.

              METHODS process
                IMPORTING
                  iv_input TYPE string
                RETURNING
                  VALUE(rv_result) TYPE string.

              CONSTANTS c_version TYPE char10 VALUE '1.0.0'.

            ENDINTERFACE.
            """;

    /**
     * ABAP Unit test class.
     */
    public static final String TEST_CLASS = """
            CLASS ltcl_test DEFINITION FOR TESTING
              DURATION SHORT
              RISK LEVEL HARMLESS.

              PRIVATE SECTION.
                DATA mo_cut TYPE REF TO zcl_test_class.

                METHODS setup.
                METHODS test_get_value FOR TESTING.
                METHODS test_constructor FOR TESTING.
            ENDCLASS.

            CLASS ltcl_test IMPLEMENTATION.
              METHOD setup.
                mo_cut = NEW #( ).
              ENDMETHOD.

              METHOD test_get_value.
                DATA(lv_result) = mo_cut->get_value( ).
                cl_abap_unit_assert=>assert_equals(
                  act = lv_result
                  exp = 'test' ).
              ENDMETHOD.

              METHOD test_constructor.
                cl_abap_unit_assert=>assert_bound( mo_cut ).
              ENDMETHOD.
            ENDCLASS.
            """;

    /**
     * Simple ABAP program.
     */
    public static final String PROGRAM_SOURCE = """
            REPORT ztest_program.

            DATA: lv_text TYPE string.

            START-OF-SELECTION.
              lv_text = 'Hello World'.
              WRITE: / lv_text.
            """;

    /**
     * Function group include.
     */
    public static final String FUNCTION_GROUP_INCLUDE = """
            *"----------------------------------------------------------------------
            *"  Include LZTEST_FGTOP
            *"----------------------------------------------------------------------

            FUNCTION-POOL ztest_fg.

            DATA: gv_counter TYPE i.
            """;

    /**
     * ABAP include program.
     */
    public static final String INCLUDE_SOURCE = """
            *&---------------------------------------------------------------------*
            *& Include ZTEST_INCLUDE
            *&---------------------------------------------------------------------*

            CLASS-METHODS get_instance
              RETURNING VALUE(ro_instance) TYPE REF TO zcl_singleton.
            """;

    /**
     * Namespaced class definition.
     */
    public static final String NAMESPACED_CLASS = """
            CLASS /namespace/cl_test DEFINITION
              PUBLIC
              FINAL
              CREATE PUBLIC.

              PUBLIC SECTION.
                METHODS execute.
            ENDCLASS.

            CLASS /namespace/cl_test IMPLEMENTATION.
              METHOD execute.
                " Implementation
              ENDMETHOD.
            ENDCLASS.
            """;

    /**
     * Class with syntax error for testing activation failures.
     */
    public static final String CLASS_WITH_SYNTAX_ERROR = """
            CLASS zcl_syntax_error DEFINITION
              PUBLIC
              FINAL
              CREATE PUBLIC.

              PUBLIC SECTION.
                METHODS broken_method.
            ENDCLASS.

            CLASS zcl_syntax_error IMPLEMENTATION.
              METHOD broken_method.
                " Missing DATA keyword
                lv_value = 'oops'.
              ENDMETHOD.
            ENDCLASS.
            """;

    /**
     * Minimal class for save operations.
     */
    public static final String MINIMAL_CLASS = """
            CLASS zcl_minimal DEFINITION PUBLIC.
            ENDCLASS.
            CLASS zcl_minimal IMPLEMENTATION.
            ENDCLASS.
            """;
}
