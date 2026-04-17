package com.sapjco.mcp.util;

import java.util.List;

/**
 * Utility class for building XML payloads for ADT object creation.
 * Provides consistent XML construction following ADT EMF format.
 */
public final class XmlCreationBuilder {

    private XmlCreationBuilder() {
        // Utility class - prevent instantiation
    }

    // XML namespaces
    public static final String NS_ADTCORE = "http://www.sap.com/adt/core";
    public static final String NS_ABAPOO = "http://www.sap.com/adt/oo";
    public static final String NS_CLASS = "http://www.sap.com/adt/oo/classes";
    public static final String NS_INTF = "http://www.sap.com/adt/oo/interfaces";
    public static final String NS_PROGRAMS = "http://www.sap.com/adt/programs/programs";
    public static final String NS_FUGR = "http://www.sap.com/adt/functions/groups";
    public static final String NS_INCLUDES = "http://www.sap.com/adt/programs/includes";

    // ADT type codes
    public static final String TYPE_CLASS = "CLAS/OC";
    public static final String TYPE_INTERFACE = "INTF/OI";
    public static final String TYPE_PROGRAM = "PROG/P";
    public static final String TYPE_INCLUDE = "PROG/I";
    public static final String TYPE_FUNCTION_GROUP = "FUGR/F";

    /**
     * Build XML body for class creation.
     * Uses ADT EMF XML format with attributes on root element.
     *
     * @param className    Class name
     * @param description  Short description
     * @param packageName  Target package
     * @param visibility   Visibility (public, protected, private)
     * @param isFinal      Whether the class is final
     * @param superClass   Optional superclass name
     * @param interfaces   Optional list of interfaces to implement
     * @return XML string for class creation
     */
    public static String buildClassCreationXml(
            String className,
            String description,
            String packageName,
            String visibility,
            boolean isFinal,
            String superClass,
            List<String> interfaces) {

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        // Build root element with attributes (ADT EMF format matching Eclipse)
        xml.append("<class:abapClass");
        xml.append(" xmlns:abapoo=\"").append(NS_ABAPOO).append("\"");
        xml.append(" xmlns:adtcore=\"").append(NS_ADTCORE).append("\"");
        xml.append(" xmlns:class=\"").append(NS_CLASS).append("\"");
        xml.append(" adtcore:description=\"").append(escapeXml(description)).append("\"");
        xml.append(" adtcore:name=\"").append(escapeXml(className.toUpperCase())).append("\"");
        xml.append(" adtcore:type=\"").append(TYPE_CLASS).append("\"");

        // Visibility (default: public)
        String vis = (visibility != null && !visibility.isEmpty()) ? visibility.toLowerCase() : "public";
        xml.append(" class:visibility=\"").append(vis).append("\"");

        // Final flag (default: true)
        xml.append(" class:final=\"").append(isFinal).append("\"");
        xml.append(">\n");

        // Package reference (child element)
        xml.append("  <adtcore:packageRef adtcore:name=\"")
                .append(escapeXml(packageName.toUpperCase()))
                .append("\"/>\n");

        // Implemented interfaces (optional) - using abapoo:interfaceRef with minimal attributes
        if (interfaces != null && !interfaces.isEmpty()) {
            for (String intf : interfaces) {
                xml.append("  <abapoo:interfaceRef adtcore:name=\"")
                        .append(escapeXml(intf.toUpperCase()))
                        .append("\"/>\n");
            }
        }

        // Superclass (optional)
        if (superClass != null && !superClass.isEmpty()) {
            xml.append("  <class:superClassRef adtcore:name=\"")
                    .append(escapeXml(superClass.toUpperCase()))
                    .append("\"/>\n");
        }

        xml.append("</class:abapClass>");
        return xml.toString();
    }

    /**
     * Build XML body for interface creation.
     *
     * @param interfaceName Interface name
     * @param description   Short description
     * @param packageName   Target package
     * @return XML string for interface creation
     */
    public static String buildInterfaceCreationXml(
            String interfaceName,
            String description,
            String packageName) {

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        xml.append("<intf:abapInterface");
        xml.append(" xmlns:adtcore=\"").append(NS_ADTCORE).append("\"");
        xml.append(" xmlns:intf=\"").append(NS_INTF).append("\"");
        xml.append(" adtcore:description=\"").append(escapeXml(description)).append("\"");
        xml.append(" adtcore:name=\"").append(escapeXml(interfaceName.toUpperCase())).append("\"");
        xml.append(" adtcore:type=\"").append(TYPE_INTERFACE).append("\"");
        xml.append(">\n");

        // Package reference (child element)
        xml.append("  <adtcore:packageRef adtcore:name=\"")
                .append(escapeXml(packageName.toUpperCase()))
                .append("\"/>\n");

        xml.append("</intf:abapInterface>");
        return xml.toString();
    }

    /**
     * Build XML body for program creation.
     *
     * @param programName   Program name
     * @param description   Short description
     * @param packageName   Target package
     * @param programType   Program type (executableProgram, include, modulePool, subroutinePool, etc.)
     * @return XML string for program creation
     */
    public static String buildProgramCreationXml(
            String programName,
            String description,
            String packageName,
            String programType) {

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        xml.append("<program:abapProgram");
        xml.append(" xmlns:adtcore=\"").append(NS_ADTCORE).append("\"");
        xml.append(" xmlns:program=\"").append(NS_PROGRAMS).append("\"");
        xml.append(" adtcore:description=\"").append(escapeXml(description)).append("\"");
        xml.append(" adtcore:name=\"").append(escapeXml(programName.toUpperCase())).append("\"");
        xml.append(" adtcore:type=\"").append(TYPE_PROGRAM).append("\"");

        // Program type (default: executableProgram)
        String type = (programType != null && !programType.isEmpty()) ? programType : "executableProgram";
        xml.append(" program:programType=\"").append(type).append("\"");
        xml.append(">\n");

        // Package reference (child element)
        xml.append("  <adtcore:packageRef adtcore:name=\"")
                .append(escapeXml(packageName.toUpperCase()))
                .append("\"/>\n");

        xml.append("</program:abapProgram>");
        return xml.toString();
    }

    /**
     * Build XML body for function group creation.
     *
     * @param groupName     Function group name
     * @param description   Short description
     * @param packageName   Target package
     * @return XML string for function group creation
     */
    public static String buildFunctionGroupCreationXml(
            String groupName,
            String description,
            String packageName) {

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        xml.append("<group:abapFunctionGroup");
        xml.append(" xmlns:adtcore=\"").append(NS_ADTCORE).append("\"");
        xml.append(" xmlns:group=\"").append(NS_FUGR).append("\"");
        xml.append(" adtcore:description=\"").append(escapeXml(description)).append("\"");
        xml.append(" adtcore:name=\"").append(escapeXml(groupName.toUpperCase())).append("\"");
        xml.append(" adtcore:type=\"").append(TYPE_FUNCTION_GROUP).append("\"");
        xml.append(">\n");

        // Package reference (child element)
        xml.append("  <adtcore:packageRef adtcore:name=\"")
                .append(escapeXml(packageName.toUpperCase()))
                .append("\"/>\n");

        xml.append("</group:abapFunctionGroup>");
        return xml.toString();
    }

    /**
     * Build XML body for include creation.
     *
     * @param includeName   Include name
     * @param description   Short description
     * @param packageName   Target package
     * @return XML string for include creation
     */
    public static String buildIncludeCreationXml(
            String includeName,
            String description,
            String packageName) {

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        xml.append("<include:abapInclude");
        xml.append(" xmlns:adtcore=\"").append(NS_ADTCORE).append("\"");
        xml.append(" xmlns:include=\"").append(NS_INCLUDES).append("\"");
        xml.append(" adtcore:description=\"").append(escapeXml(description)).append("\"");
        xml.append(" adtcore:name=\"").append(escapeXml(includeName.toUpperCase())).append("\"");
        xml.append(" adtcore:type=\"").append(TYPE_INCLUDE).append("\"");
        xml.append(">\n");

        // Package reference (child element)
        xml.append("  <adtcore:packageRef adtcore:name=\"")
                .append(escapeXml(packageName.toUpperCase()))
                .append("\"/>\n");

        xml.append("</include:abapInclude>");
        return xml.toString();
    }

    /**
     * Escape special XML characters in a string.
     *
     * @param input Input string
     * @return XML-escaped string
     */
    public static String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
